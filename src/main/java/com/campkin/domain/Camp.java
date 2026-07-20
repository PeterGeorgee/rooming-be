package com.campkin.domain;
import jakarta.persistence.*; import lombok.*; import java.time.*; import java.util.*;
@Entity @Table(name="camps") @Getter @Setter @NoArgsConstructor public class Camp {
 @Id private UUID id=UUID.randomUUID(); @Column(nullable=false) private String name; @Column(name="start_date",nullable=false) private LocalDate startDate; @Column(name="end_date",nullable=false) private LocalDate endDate; private String description; @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
}
