package com.campkin.api;
import com.campkin.domain.Domain; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.time.*; import java.util.*;
public final class ApiModels { private ApiModels(){}
 public record CampRequest(@NotBlank String name,@NotNull LocalDate startDate,@NotNull LocalDate endDate,String description){}
 public record RoomRequest(@NotBlank String name,@Min(1) int capacity,@NotNull Domain.Gender gender){}
 public record BatchRoomRequest(@Min(1) @Max(100) int count,@Min(1) int capacity,@NotNull Domain.Gender gender){}
 public record RoomRenameRequest(@NotBlank @Size(max=120) String name){}
 public record RoomLeaderItem(@NotBlank @Size(max=180) String name,@NotNull UUID sleepRoomId){}
 public record RoomLeadersUpdateRequest(@NotNull List<@Valid RoomLeaderItem> leaders){}
 public record GroupLeadersUpdateRequest(@NotNull List<@NotBlank @Size(max=180) String> leaders){}
 public record LeaderView(UUID id,String name,UUID sleepRoomId,String sleepRoom){}
 public record RoomLeaderRequest(@NotNull UUID roomId,@NotBlank @Size(max=180) String name){}
 public record GenerateRoomsRequest(@NotNull List<@Valid RoomLeaderRequest> leaders){}
 public record GroupRequest(@Min(1) Integer numberOfGroups,@Min(1) Integer membersPerGroup,boolean genderSeparated){}
 public record MoveRequest(UUID roomId,UUID groupId){}
 public record GenderRequest(@NotNull Domain.Gender gender){}
 public record MatchRequest(@NotNull UUID matchedCamperId){}
 public record CamperView(UUID id,String name,Domain.Gender gender,boolean genderAssumed,LocalDate birthdate,int age,UUID roomId,String room,UUID groupId,String group,List<PreferenceView> preferences){}
 public record PreferenceView(UUID id,String rawName,String matchedName,String status,Double similarity,List<UUID> alternatives){}
 public record RoomView(UUID id,String name,int capacity,Domain.Gender gender,List<LeaderView> leaders,long occupancy,double averageAge,List<CamperView> campers){}
 public record GroupView(UUID id,String name,Integer capacity,List<String> leaders,long occupancy,double averageAge,List<CamperView> campers){}
 public record Stats(long total,long boys,long girls,long unknownGender,long assumedGender,double averageAge,long matched,long ambiguous,long unresolved,double satisfaction,double averageRoomAgeSpread){}
 public record Dashboard(Object camp,List<RoomView> rooms,List<GroupView> groups,List<CamperView> campers,Stats stats){}
 public record ImportResult(int imported,int boys,int girls,int unknownGender,double averageAge,List<String> warnings){}
}
