package com.campkin.domain;
import jakarta.persistence.*; import lombok.*; import java.util.*;
@Entity @Table(name="caring_groups") @Getter @Setter @NoArgsConstructor public class CaringGroup {
 @Id private UUID id=UUID.randomUUID(); @ManyToOne(optional=false) @JoinColumn(name="camp_id") private Camp camp; @Column(nullable=false) private String name; @Column(name="leader_name",nullable=false) private String leaderName; @Enumerated(EnumType.STRING) @Column(nullable=false) private Domain.Gender gender;
}
