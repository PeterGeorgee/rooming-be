package com.campkin.api;
import com.campkin.domain.Domain; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.time.*; import java.util.*;
public final class ApiModels { private ApiModels(){}
 public record CampRequest(@NotBlank String name,@NotNull LocalDate startDate,@NotNull LocalDate endDate,String description){}
 public record RegisterRequest(@NotBlank @Size(max=120) String name,@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(min=8,max=100) String password){}
 public record LoginRequest(@NotBlank @Email String email,@NotBlank String password){}
 public record JoinCampRequest(@NotBlank @Size(max=20) String code){}
 public record UserView(UUID id,String name,String email){}
 public record AuthResponse(String token,UserView user){}
 public record RoomRequest(@NotBlank String name,@Min(1) int capacity,@NotNull Domain.Gender gender){}
 public record BatchRoomRequest(@Min(1) @Max(100) int count,@Min(1) int capacity,@NotNull Domain.Gender gender){}
 public record RoomRenameRequest(@NotBlank @Size(max=120) String name){}
 public record RoomLeaderItem(@NotNull UUID leaderId,@NotNull UUID sleepRoomId){}
 public record RoomLeadersUpdateRequest(@NotNull List<@Valid RoomLeaderItem> leaders){}
 public record GroupLeadersUpdateRequest(@NotNull List<@NotNull UUID> leaderIds){}
 public record LeaderView(UUID id,UUID leaderId,String name,UUID sleepRoomId,String sleepRoom){}
 public record LeaderRecordView(UUID id,String name,Domain.Gender gender){}
 public record LeaderRequest(@NotBlank @Size(max=180) String name,@NotNull Domain.Gender gender){}
 public record LeaderImportResult(int imported,int added,int updated,List<String> errors){}
 public record RoomLeaderRequest(@NotNull UUID roomId,@NotBlank @Size(max=180) String name){}
 public record GenerateRoomsRequest(@NotNull List<@Valid RoomLeaderRequest> leaders){}
 public record GroupRequest(@Min(1) Integer numberOfGroups,@Min(1) Integer membersPerGroup,boolean genderSeparated){}
 public record MoveRequest(UUID roomId,UUID groupId,UUID caringGroupId){}
 public record GenerateCaringRequest(@NotEmpty List<@NotNull UUID> leaderIds){}
 public record CaringLeaderUpdateRequest(@NotNull UUID leaderId){}
 public record GenderRequest(@NotNull Domain.Gender gender){}
 public record MatchRequest(@NotNull UUID matchedCamperId){}
 public record CamperIdsRequest(@NotEmpty List<@NotNull UUID> camperIds){}
 public record CamperView(UUID id,String name,Domain.Gender gender,boolean genderAssumed,LocalDate birthdate,int age,UUID roomId,String room,UUID groupId,String group,UUID caringGroupId,String caringGroup,List<PreferenceView> preferences){}
 public record PreferenceView(UUID id,String rawName,String matchedName,String status,Double similarity,List<UUID> alternatives){}
 public record RoomView(UUID id,String name,int capacity,Domain.Gender gender,List<LeaderView> leaders,long occupancy,double averageAge,List<CamperView> campers){}
 public record GroupView(UUID id,String name,Integer capacity,List<String> leaders,long occupancy,double averageAge,List<CamperView> campers){}
 public record CaringGroupView(UUID id,String name,UUID leaderId,String leaderName,Domain.Gender gender,long occupancy,double averageAge,List<CamperView> campers){}
 public record Stats(long total,long boys,long girls,long unknownGender,long assumedGender,double averageAge,long matched,long ambiguous,long unresolved,double satisfaction,double averageRoomAgeSpread){}
 public record Dashboard(Object camp,List<RoomView> rooms,List<GroupView> groups,List<CaringGroupView> caringGroups,List<LeaderRecordView> leaders,List<CamperView> campers,Stats stats){}
 public record MissingCamper(UUID id,String name){}
 public record ImportResult(int imported,int added,int updated,boolean existingAssignments,int boys,int girls,int unknownGender,double averageAge,List<String> warnings,List<MissingCamper> missingCampers){}
}
