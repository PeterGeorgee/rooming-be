package com.campkin.domain;
import jakarta.persistence.*; import lombok.*; import java.util.*;
@Entity @Table(name="group_leaders") @Getter @Setter @NoArgsConstructor public class GroupLeader {
 @Id private UUID id=UUID.randomUUID(); @ManyToOne(optional=false) @JoinColumn(name="group_id") private DiscussionGroup group; @Column(nullable=false) private String name;
}
