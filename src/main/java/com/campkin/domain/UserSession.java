package com.campkin.domain;
import jakarta.persistence.*; import lombok.*; import java.time.*; import java.util.*;
@Entity @Table(name="user_sessions") @Getter @Setter @NoArgsConstructor public class UserSession {
 @Id private UUID id=UUID.randomUUID(); @ManyToOne(optional=false) @JoinColumn(name="user_id") private AppUser user; @Column(name="token_hash",nullable=false,unique=true,length=64) private String tokenHash; @Column(name="expires_at",nullable=false) private Instant expiresAt; @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
}
