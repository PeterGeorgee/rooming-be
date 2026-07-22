package com.campkin.domain;
import jakarta.persistence.*; import lombok.*; import java.time.*; import java.util.*;
@Entity @Table(name="app_users") @Getter @Setter @NoArgsConstructor public class AppUser {
 @Id private UUID id=UUID.randomUUID(); @Column(nullable=false,length=120) private String name; @Column(nullable=false,unique=true,length=254) private String email; @Column(name="password_hash",nullable=false,length=100) private String passwordHash; @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
}
