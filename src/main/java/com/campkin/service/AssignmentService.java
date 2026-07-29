package com.campkin.service;

import com.campkin.api.ApiModels.GenerateRoomsRequest;
import com.campkin.api.ApiModels.GroupRequest;
import com.campkin.api.ApiModels.GenerateCaringRequest;
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
    private final RoomLeaderRepository roomLeaders;
    private final GroupLeaderRepository groupLeaders;
    private final CaringGroupRepository caringGroups;
    private final LeaderRepository leaders;

    @Transactional
    public void assignCaring(UUID campId, GenerateCaringRequest request) {
        Camp camp = camps.findById(campId).orElseThrow();
        List<Camper> all = campers.findByCampIdOrderByName(campId);
        if (all.isEmpty()) throw new IllegalStateException("Import campers before generating Caring groups");
        if (all.stream().anyMatch(c -> c.getRoom() == null))
            throw new IllegalStateException("Assign every camper to a room before generating Caring groups");
        if (all.stream().anyMatch(c -> c.getGender() == Domain.Gender.UNKNOWN))
            throw new IllegalStateException("Set Male or Female for every camper before generating Caring groups");
        List<RoomLeader> roomLinks=roomLeaders.findByManagedRoomCampId(campId);
        List<Leader> availableRoomLeaders=roomLinks.stream()
            .map(link->leaders.findByCampIdAndNormalizedName(campId,NameMatcher.normalize(link.getName())).orElse(null))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<Room,List<Camper>> clusters=roomClusters(all);
        Map<Leader,List<Camper>> plan=new LinkedHashMap<>();
        for(var entry:clusters.entrySet()){
            Room room=entry.getKey();
            Leader preferred=roomLinks.stream().filter(link->link.getManagedRoom().getId().equals(room.getId())).sorted(Comparator.comparing(RoomLeader::getName)).map(link->leaders.findByCampIdAndNormalizedName(campId,NameMatcher.normalize(link.getName())).orElse(null)).filter(Objects::nonNull).findFirst().orElse(null);
            if(preferred==null)preferred=availableRoomLeaders.stream().filter(leader->leader.getGender()==room.getGender()).min(Comparator.comparingInt(leader->plan.getOrDefault(leader,List.of()).size())).orElseThrow(()->new IllegalStateException("Assign a leader to at least one "+(room.getGender()==Domain.Gender.FEMALE?"girls'":"boys'")+" room before generating Caring groups"));
            plan.computeIfAbsent(preferred,ignored->new ArrayList<>()).addAll(entry.getValue());
        }
        all.forEach(c->c.setCaringGroup(null));campers.flush();caringGroups.deleteByCampId(campId);caringGroups.flush();
        Map<Domain.Gender,Integer> numbers=new EnumMap<>(Domain.Gender.class);
        for(var entry:plan.entrySet()){
            Leader leader=entry.getKey();int number=numbers.merge(leader.getGender(),1,Integer::sum);CaringGroup group=new CaringGroup();group.setCamp(camp);group.setName((leader.getGender()==Domain.Gender.FEMALE?"Girls Caring ":"Boys Caring ")+number);group.setLeaderName(leader.getName());group.setGender(leader.getGender());caringGroups.save(group);entry.getValue().forEach(camper->camper.setCaringGroup(group));
        }
    }

    @Transactional
    public void assignRooms(UUID campId, GenerateRoomsRequest request) {
        Camp camp = camps.findById(campId).orElseThrow();
        List<Camper> all = campers.findByCampIdOrderByName(campId);
        List<Room> campRooms = rooms.findByCampIdOrderByName(campId);
        if (campRooms.isEmpty()) throw new IllegalStateException("Add rooms before generating assignments");
        if (all.stream().anyMatch(c -> c.getGender() == Domain.Gender.UNKNOWN))
            throw new IllegalStateException("Set Male or Female for every camper before generating rooms");

        if (!request.leaders().isEmpty()) throw new IllegalArgumentException("Assign leaders from the Rooms page after generating rooms");

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
                if (leaderBeds(room) == 0 && placed.get(room.getId()).isEmpty()) {
                    Camper seed = ordered.removeFirst();
                    placed.get(room.getId()).add(seed);
                }
            }
            for (Camper camper : ordered) {
                Room best = available.stream()
                    .filter(r -> usedBeds(r, placed) < totalCapacity(r))
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
        return placed.get(room.getId()).size() + leaderBeds(room);
    }

    private int camperCapacity(Room room) {
        return totalCapacity(room) - leaderBeds(room);
    }

    private List<Room> selectActiveRooms(List<Room> genderRooms, int camperCount) {
        List<Room> active = new ArrayList<>(genderRooms.stream().filter(r -> leaderBeds(r) > 0).toList());
        int capacity = active.stream().mapToInt(this::camperCapacity).sum();
        List<Room> candidates = genderRooms.stream()
            .filter(r -> leaderBeds(r) == 0)
            .sorted(Comparator.comparingInt(this::totalCapacity).reversed().thenComparing(Room::getName))
            .toList();
        for (Room room : candidates) {
            if (capacity >= camperCount) break;
            active.add(room);
            capacity += camperCapacity(room);
        }
        active.sort(Comparator.comparing(Room::getName));
        return active;
    }

    private int leaderBeds(Room room) { return (int) roomLeaders.findBySleepRoomIdOrderByName(room.getId()).stream().map(link -> NameMatcher.normalize(link.getName())).distinct().count(); }

    private double placementScore(Camper camper, Room room, Map<UUID, List<Camper>> placed, Map<UUID, Set<UUID>> friends, Camp camp) {
        List<Camper> current = placed.get(room.getId());
        long friendCount = current.stream().filter(c -> friends.getOrDefault(camper.getId(), Set.of()).contains(c.getId())).count();
        double utilization = (double) usedBeds(room, placed) / totalCapacity(room);
        double ageDifference = current.isEmpty() ? 0 : Math.abs(camper.ageOn(camp.getStartDate()) - current.stream().mapToInt(c -> c.ageOn(camp.getStartDate())).average().orElse(0));
        return friendCount * 8 - utilization * 12 - ageDifference * 0.05;
    }

    @Transactional
    public void assignGroups(UUID campId, GroupRequest req) {
        Camp camp = camps.findById(campId).orElseThrow();
        List<Camper> all = campers.findByCampIdOrderByName(campId);
        if (all.isEmpty()) throw new IllegalStateException("Import campers first");
        if (all.stream().anyMatch(c -> c.getRoom() == null)) throw new IllegalStateException("Assign every camper to a room before generating discussion groups");
        if (!req.genderSeparated()) {
            int count = req.numberOfGroups() != null ? req.numberOfGroups() : (int) Math.ceil((double) all.size() / req.membersPerGroup());
            if (count < 1) throw new IllegalArgumentException("At least one group is required");
            if (count > all.size()) throw new IllegalArgumentException("Number of groups cannot exceed number of campers");
            clearAndDeleteDiscussionGroups(campId, all);
            assignRoomBasedGroups(campId, all, createGroups(camp, "Group", count, all.size(), false));
            return;
        }

        List<Camper> girls = all.stream().filter(c -> c.getGender() == Domain.Gender.FEMALE).toList();
        List<Camper> boys = all.stream().filter(c -> c.getGender() == Domain.Gender.MALE).toList();
        if (girls.size() + boys.size() != all.size()) throw new IllegalStateException("Set every camper's gender before generating separated groups");
        boolean explicitGenderCounts = req.femaleGroups() != null || req.maleGroups() != null;
        int girlGroups;
        int boyGroups;
        if (explicitGenderCounts) {
            girlGroups = req.femaleGroups() == null ? 0 : req.femaleGroups();
            boyGroups = req.maleGroups() == null ? 0 : req.maleGroups();
        } else {
            int count = req.numberOfGroups() != null ? req.numberOfGroups() : (int) Math.ceil((double) all.size() / req.membersPerGroup());
            int activeGenders = (girls.isEmpty() ? 0 : 1) + (boys.isEmpty() ? 0 : 1);
            if (count < activeGenders) throw new IllegalArgumentException("At least one group is required for each gender that has campers");
            girlGroups = girls.isEmpty() ? 0 : 1;
            boyGroups = boys.isEmpty() ? 0 : 1;
            while (girlGroups + boyGroups < count) {
                double girlLoad = girlGroups == 0 ? -1 : (double) girls.size() / girlGroups;
                double boyLoad = boyGroups == 0 ? -1 : (double) boys.size() / boyGroups;
                if (girlLoad >= boyLoad) girlGroups++; else boyGroups++;
            }
        }
        if (!girls.isEmpty() && girlGroups < 1) throw new IllegalArgumentException("At least one girls' group is required");
        if (!boys.isEmpty() && boyGroups < 1) throw new IllegalArgumentException("At least one boys' group is required");
        if (girls.isEmpty() && girlGroups > 0) throw new IllegalArgumentException("Girls' groups cannot be created because there are no female campers");
        if (boys.isEmpty() && boyGroups > 0) throw new IllegalArgumentException("Boys' groups cannot be created because there are no male campers");
        if (girlGroups > roomClusters(girls).size() || boyGroups > roomClusters(boys).size()) throw new IllegalArgumentException("The number of groups cannot exceed the occupied rooms for that gender because room members stay together");
        clearAndDeleteDiscussionGroups(campId, all);
        if (girlGroups > 0) assignRoomBasedGroups(campId,girls,createGroups(camp,"Girls Group",girlGroups,girls.size(),true));
        if (boyGroups > 0) assignRoomBasedGroups(campId,boys,createGroups(camp,"Boys Group",boyGroups,boys.size(),true));
    }

    private void clearAndDeleteDiscussionGroups(UUID campId, List<Camper> all) {
        all.forEach(camper -> camper.setDiscussionGroup(null));
        campers.flush();
        groups.deleteByCampId(campId);
        groups.flush();
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

    private Map<Room,List<Camper>> roomClusters(List<Camper> people){Map<Room,List<Camper>> result=new LinkedHashMap<>();for(Camper camper:people)result.computeIfAbsent(camper.getRoom(),ignored->new ArrayList<>()).add(camper);return result;}

    private void assignRoomBasedGroups(UUID campId,List<Camper> people,List<DiscussionGroup> generated){
        List<Map.Entry<Room,List<Camper>>> clusters=new ArrayList<>(roomClusters(people).entrySet());
        if(generated.size()>clusters.size())throw new IllegalArgumentException("The number of discussion groups cannot exceed the number of occupied rooms because room members stay together");
        clusters.sort(Comparator.<Map.Entry<Room,List<Camper>>>comparingInt(entry->entry.getValue().size()).reversed().thenComparing(entry->entry.getKey().getName()));
        Map<DiscussionGroup,Integer> loads=new LinkedHashMap<>();generated.forEach(group->loads.put(group,0));
        Map<DiscussionGroup,List<Room>> sourceRooms=new LinkedHashMap<>();generated.forEach(group->sourceRooms.put(group,new ArrayList<>()));
        for(int i=0;i<clusters.size();i++){var cluster=clusters.get(i);DiscussionGroup target=i<generated.size()?generated.get(i):generated.stream().min(Comparator.<DiscussionGroup>comparingInt(loads::get).thenComparing(DiscussionGroup::getName)).orElseThrow();cluster.getValue().forEach(camper->camper.setDiscussionGroup(target));sourceRooms.get(target).add(cluster.getKey());loads.compute(target,(group,size)->size+cluster.getValue().size());}
        List<RoomLeader> links=roomLeaders.findByManagedRoomCampId(campId);Map<String,Integer> fallbackLoads=new HashMap<>();
        for(DiscussionGroup group:generated){LinkedHashSet<String> names=new LinkedHashSet<>();for(Room room:sourceRooms.get(group)){List<RoomLeader> own=links.stream().filter(link->link.getManagedRoom().getId().equals(room.getId())).toList();if(own.isEmpty()){RoomLeader fallback=links.stream().filter(link->link.getManagedRoom().getGender()==room.getGender()).min(Comparator.comparingInt(link->fallbackLoads.getOrDefault(NameMatcher.normalize(link.getName()),0))).orElseThrow(()->new IllegalStateException("Assign at least one "+(room.getGender()==Domain.Gender.FEMALE?"female":"male")+" room leader before generating discussion groups"));names.add(fallback.getName());fallbackLoads.merge(NameMatcher.normalize(fallback.getName()),1,Integer::sum);}else own.stream().map(RoomLeader::getName).forEach(names::add);}for(String name:names){GroupLeader link=new GroupLeader();link.setGroup(group);link.setName(name);groupLeaders.save(link);}}
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

    private int totalCapacity(Room room) { return room.getCapacity() + room.getExtraBeds(); }
}
