package com.campkin.service;

import com.campkin.api.ApiModels.GenerateRoomsRequest;
import com.campkin.api.ApiModels.GroupRequest;
import com.campkin.domain.*;
import com.campkin.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentService {
    private final CampRepository camps;
    private final CamperRepository campers;
    private final RoomRepository rooms;
    private final DiscussionGroupRepository groups;
    private final PreferenceRepository preferences;

    @Transactional
    public void assignRooms(UUID campId, GenerateRoomsRequest request) {
        Camp camp = camps.findById(campId).orElseThrow();
        List<Camper> all = campers.findByCampIdOrderByName(campId);
        List<Room> campRooms = rooms.findByCampIdOrderByName(campId);
        if (campRooms.isEmpty()) throw new IllegalStateException("Add rooms before generating assignments");
        if (all.stream().anyMatch(c -> c.getGender() == Domain.Gender.UNKNOWN))
            throw new IllegalStateException("Set Male or Female for every camper before generating rooms");

        Map<UUID, String> leaders = new HashMap<>();
        for (var leader : request.leaders()) {
            if (leaders.put(leader.roomId(), leader.name().trim()) != null)
                throw new IllegalArgumentException("Only one leader can be assigned to a room");
        }
        for (Room room : campRooms) {
            room.setLeaderName(leaders.get(room.getId()));
            if (room.getLeaderName() != null && room.getCapacity() < 1)
                throw new IllegalStateException(room.getName() + " has no bed for its leader");
        }
        if (leaders.keySet().stream().anyMatch(id -> campRooms.stream().noneMatch(r -> r.getId().equals(id))))
            throw new IllegalArgumentException("A selected leader room does not belong to this camp");

        all.forEach(c -> c.setRoom(null));
        Map<UUID, Set<UUID>> friendMap = friendMap(campId);

        for (Domain.Gender gender : List.of(Domain.Gender.FEMALE, Domain.Gender.MALE)) {
            List<Camper> people = new ArrayList<>(all.stream().filter(c -> c.getGender() == gender).toList());
            List<Room> available = campRooms.stream().filter(r -> r.getGender() == gender).toList();
            if (people.isEmpty() && available.isEmpty()) continue;
            int leaderBeds = (int) available.stream().filter(r -> r.getLeaderName() != null).count();
            int camperBeds = available.stream().mapToInt(r -> r.getCapacity() - (r.getLeaderName() == null ? 0 : 1)).sum();
            if (camperBeds < people.size())
                throw new IllegalStateException("Not enough " + gender.name().toLowerCase() + " camper beds: need " + people.size() + ", have " + camperBeds + " after leader beds");
            if (people.size() + leaderBeds < available.size())
                throw new IllegalStateException("There are not enough " + gender.name().toLowerCase() + " campers and leaders to occupy every room");

            Map<UUID, List<Camper>> placed = available.stream().collect(Collectors.toMap(Room::getId, x -> new ArrayList<>(), (a,b) -> a, LinkedHashMap::new));
            List<List<Camper>> components = components(people, friendMap);
            components.sort(Comparator.<List<Camper>>comparingInt(List::size).reversed().thenComparing(x -> x.getFirst().getName()));
            List<Camper> ordered = components.stream().flatMap(Collection::stream).collect(Collectors.toCollection(ArrayList::new));

            for (Room room : available) {
                if (room.getLeaderName() == null && placed.get(room.getId()).isEmpty()) {
                    Camper seed = ordered.removeFirst();
                    placed.get(room.getId()).add(seed);
                }
            }
            for (Camper camper : ordered) {
                Room best = available.stream()
                    .filter(r -> usedBeds(r, placed) < r.getCapacity())
                    .max(Comparator.comparingDouble((Room r) -> placementScore(camper, r, placed, friendMap, camp)).thenComparing(Room::getName, Comparator.reverseOrder()))
                    .orElseThrow(() -> new IllegalStateException("No available bed for " + camper.getName()));
                placed.get(best.getId()).add(camper);
            }
            placed.forEach((roomId, peopleInRoom) -> {
                Room room = rooms.findById(roomId).orElseThrow();
                peopleInRoom.forEach(c -> c.setRoom(room));
            });
        }
    }

    private int usedBeds(Room room, Map<UUID, List<Camper>> placed) {
        return placed.get(room.getId()).size() + (room.getLeaderName() == null ? 0 : 1);
    }

    private double placementScore(Camper camper, Room room, Map<UUID, List<Camper>> placed, Map<UUID, Set<UUID>> friends, Camp camp) {
        List<Camper> current = placed.get(room.getId());
        long friendCount = current.stream().filter(c -> friends.getOrDefault(camper.getId(), Set.of()).contains(c.getId())).count();
        double utilization = (double) usedBeds(room, placed) / room.getCapacity();
        double ageDifference = current.isEmpty() ? 0 : Math.abs(camper.ageOn(camp.getStartDate()) - current.stream().mapToInt(c -> c.ageOn(camp.getStartDate())).average().orElse(0));
        return friendCount * 8 - utilization * 12 - ageDifference * 0.05;
    }

    @Transactional
    public void assignGroups(UUID campId, GroupRequest req) {
        Camp camp = camps.findById(campId).orElseThrow();
        List<Camper> all = campers.findByCampIdOrderByName(campId);
        if (all.isEmpty()) throw new IllegalStateException("Import campers first");
        int count = req.numberOfGroups() != null ? req.numberOfGroups() : (int) Math.ceil((double) all.size() / req.membersPerGroup());
        if (count < 1) throw new IllegalArgumentException("At least one group is required");
        groups.deleteByCampId(campId);
        List<DiscussionGroup> generated = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            DiscussionGroup group = new DiscussionGroup();
            group.setCamp(camp); group.setName("Group " + i); group.setCapacity((int) Math.ceil((double) all.size() / count)); group.setGenderSeparated(req.genderSeparated());
            generated.add(groups.save(group));
        }
        List<Camper> ordered = new ArrayList<>(all);
        ordered.sort(Comparator.comparingInt((Camper c) -> c.ageOn(camp.getStartDate())).thenComparing(Camper::getName));
        for (int i = 0; i < ordered.size(); i++) {
            int cycle = i % (count * 2); int index = cycle < count ? cycle : count * 2 - 1 - cycle;
            ordered.get(i).setDiscussionGroup(generated.get(index));
        }
    }

    private Map<UUID, Set<UUID>> friendMap(UUID campId) {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        for (Preference p : preferences.findByCamperCampId(campId)) if (p.getMatchedCamper() != null) {
            result.computeIfAbsent(p.getCamper().getId(), x -> new HashSet<>()).add(p.getMatchedCamper().getId());
            result.computeIfAbsent(p.getMatchedCamper().getId(), x -> new HashSet<>()).add(p.getCamper().getId());
        }
        return result;
    }

    private List<List<Camper>> components(List<Camper> people, Map<UUID, Set<UUID>> friends) {
        Map<UUID, Camper> byId = people.stream().collect(Collectors.toMap(Camper::getId, x -> x));
        Set<UUID> seen = new HashSet<>(); List<List<Camper>> result = new ArrayList<>();
        for (Camper start : people) if (seen.add(start.getId())) {
            List<Camper> part = new ArrayList<>(); Deque<UUID> queue = new ArrayDeque<>(); queue.add(start.getId());
            while (!queue.isEmpty()) { UUID id = queue.remove(); part.add(byId.get(id)); for (UUID next : friends.getOrDefault(id, Set.of())) if (byId.containsKey(next) && seen.add(next)) queue.add(next); }
            part.sort(Comparator.comparing(Camper::getName)); result.add(part);
        }
        return result;
    }
}
