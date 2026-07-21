package com.campkin.domain;
import jakarta.persistence.*; import lombok.*; import java.util.*;
@Entity @Table(name="rooms") @Getter @Setter @NoArgsConstructor public class Room {
 @Id private UUID id=UUID.randomUUID(); @ManyToOne(optional=false) @JoinColumn(name="camp_id") private Camp camp; @Column(nullable=false) private String name; @Column(nullable=false) private int capacity; @Enumerated(EnumType.STRING) @Column(nullable=false) private Domain.Gender gender; @Column(name="leader_name") private String leaderName;
}
