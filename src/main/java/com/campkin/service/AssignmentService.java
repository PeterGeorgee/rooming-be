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
            List<Room> genderRooms = campRooms.stream().filter(r -> r.getGender() == gender).toList();
            int totalCamperBeds = genderRooms.stream().mapToInt(this::camperCapacity).sum();
            if (totalCamperBeds < people.size())
                throw new IllegalStateException("Not enough " + gender.name().toLowerCase() + " camper beds: need " + people.size() + ", have " + totalCamperBeds + " after leader beds");
            List<Room> available = selectActiveRooms(genderRooms, people.size());
            if (people.isEmpty() && available.isEmpty()) continue;

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

    private int camperCapacity(Room room) {
        return room.getCapacity() - (room.getLeaderName() == null ? 0 : 1);
    }

    private List<Room> selectActiveRooms(List<Room> genderRooms, int camperCount) {
        List<Room> active = new ArrayList<>(genderRooms.stream().filter(r -> r.getLeaderName() != null).toList());
        int capacity = active.stream().mapToInt(this::camperCapacity).sum();
        List<Room> candidates = genderRooms.stream()
            .filter(r -> r.getLeaderName() == null)
            .sorted(Comparator.comparingInt(Room::getCapacity).reversed().thenComparing(Room::getName))
            .toList();
        for (Room room : candidates) {
            if (capacity >= camperCount) break;
            active.add(room);
            capacity += camperCapacity(room);
        }
        active.sort(Comparator.comparing(Room::getName));
        return active;
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
        if (count > all.size()) throw new IllegalArgumentException("Number of groups cannot exceed number of campers");
        groups.deleteByCampId(campId);
        if (!req.genderSeparated()) {
            assignToGroups(all, createGroups(camp, "Group", count, all.size(), false), camp);
            return;
        }

        List<Camper> girls = all.stream().filter(c -> c.getGender() == Domain.Gender.FEMALE).toList();
        List<Camper> boys = all.stream().filter(c -> c.getGender() == Domain.Gender.MALE).toList();
        if (girls.size() + boys.size() != all.size()) throw new IllegalStateException("Set every camper's gender before generating separated groups");
        int activeGenders = (girls.isEmpty() ? 0 : 1) + (boys.isEmpty() ? 0 : 1);
        if (count < activeGenders) throw new IllegalArgumentException("At least one group is required for each gender that has campers");

        int girlGroups = girls.isEmpty() ? 0 : 1;
        int boyGroups = boys.isEmpty() ? 0 : 1;
        while (girlGroups + boyGroups < count) {
            double girlLoad = girlGroups == 0 ? -1 : (double) girls.size() / girlGroups;
            double boyLoad = boyGroups == 0 ? -1 : (double) boys.size() / boyGroups;
            if (girlLoad >= boyLoad) girlGroups++; else boyGroups++;
        }
        if (girlGroups > girls.size() || boyGroups > boys.size()) throw new IllegalArgumentException("Too many groups for the available campers of one gender");
        if (girlGroups > 0) assignToGroups(girls, createGroups(camp, "Girls Group", girlGroups, girls.size(), true), camp);
        if (boyGroups > 0) assignToGroups(boys, createGroups(camp, "Boys Group", boyGroups, boys.size(), true), camp);
    }

    private List<DiscussionGroup> createGroups(Camp camp, String prefix, int count, int people, boolean separated) {
        List<DiscussionGroup> result = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            DiscussionGroup group = new DiscussionGroup();
            group.setCamp(camp); group.setName(prefix + " " + i); group.setCapacity((int) Math.ceil((double) people / count)); group.setGenderSeparated(separated);
            result.add(groups.save(group));
        }
        return result;
    }

    private void assignToGroups(List<Camper> people, List<DiscussionGroup> generated, Camp camp) {
        List<Camper> ordered = new ArrayList<>(people);
        ordered.sort(Comparator.comparingInt((Camper c) -> c.ageOn(camp.getStartDate())).thenComparing(Camper::getName));
        int count = generated.size();
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
