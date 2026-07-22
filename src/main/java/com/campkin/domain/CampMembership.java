package com.campkin.domain;
import jakarta.persistence.*; import lombok.*; import java.time.*; import java.util.*;
@Entity @Table(name="camp_memberships",uniqueConstraints=@UniqueConstraint(columnNames={"camp_id","user_id"})) @Getter @Setter @NoArgsConstructor public class CampMembership {
 public enum Role { OWNER, MEMBER }
 @Id private UUID id=UUID.randomUUID(); @ManyToOne(optional=false) @JoinColumn(name="camp_id") private Camp camp; @ManyToOne(optional=false) @JoinColumn(name="user_id") private AppUser user; @Enumerated(EnumType.STRING) @Column(nullable=false) private Role role; @Column(name="joined_at",nullable=false) private Instant joinedAt=Instant.now();
}
