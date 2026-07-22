package com.campkin.domain;
import jakarta.persistence.*; import lombok.*; import java.time.*; import java.util.*;
@Entity @Table(name="campers") @Getter @Setter @NoArgsConstructor public class Camper {
 @Id private UUID id=UUID.randomUUID(); @ManyToOne(optional=false) @JoinColumn(name="camp_id") private Camp camp; @Column(nullable=false) private String name; @Column(name="normalized_name",nullable=false) private String normalizedName; @Enumerated(EnumType.STRING) @Column(nullable=false) private Domain.Gender gender; @Column(name="gender_assumed",nullable=false) private boolean genderAssumed; @Column(nullable=false) private LocalDate birthdate; @ManyToOne @JoinColumn(name="room_id") private Room room; @ManyToOne @JoinColumn(name="discussion_group_id") private DiscussionGroup discussionGroup; @ManyToOne @JoinColumn(name="caring_group_id") private CaringGroup caringGroup; @Version private long version;
 public int ageOn(LocalDate date){ return Period.between(birthdate,date).getYears(); }
}
