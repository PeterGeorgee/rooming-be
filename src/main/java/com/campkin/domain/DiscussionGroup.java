package com.campkin.domain;
import jakarta.persistence.*; import lombok.*; import java.util.*;
@Entity @Table(name="discussion_groups") @Getter @Setter @NoArgsConstructor public class DiscussionGroup {
 @Id private UUID id=UUID.randomUUID(); @ManyToOne(optional=false) @JoinColumn(name="camp_id") private Camp camp; @Column(nullable=false) private String name; private Integer capacity; @Column(name="gender_separated",nullable=false) private boolean genderSeparated;
}
