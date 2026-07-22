package com.campkin.service;
import com.campkin.domain.*; import com.campkin.repo.*; import lombok.RequiredArgsConstructor; import org.springframework.http.*; import org.springframework.stereotype.Service; import org.springframework.web.server.ResponseStatusException; import java.util.*;
@Service @RequiredArgsConstructor public class AccessService {
 private final AuthContext auth; private final CampMembershipRepository memberships; private final RoomRepository rooms; private final DiscussionGroupRepository groups; private final CaringGroupRepository caringGroups; private final LeaderRepository leaders; private final CamperRepository campers; private final PreferenceRepository preferences;
 public CampMembership requireCamp(UUID campId){return memberships.findByCampIdAndUserId(campId,auth.user().getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"You do not have access to this camp"));}
 public void requireOwner(UUID campId){if(requireCamp(campId).getRole()!=CampMembership.Role.OWNER)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only the camp owner can do this");}
 public void requireRoom(UUID roomId){requireCamp(rooms.findById(roomId).orElseThrow().getCamp().getId());}
 public void requireGroup(UUID groupId){requireCamp(groups.findById(groupId).orElseThrow().getCamp().getId());}
 public void requireCaringGroup(UUID groupId){requireCamp(caringGroups.findById(groupId).orElseThrow().getCamp().getId());}
 public void requireLeader(UUID leaderId){requireCamp(leaders.findById(leaderId).orElseThrow().getCamp().getId());}
 public void requireCamper(UUID camperId){requireCamp(campers.findById(camperId).orElseThrow().getCamp().getId());}
 public void requirePreference(UUID preferenceId){requireCamp(preferences.findById(preferenceId).orElseThrow().getCamper().getCamp().getId());}
}
