package com.campkin.domain;
import jakarta.persistence.*; import lombok.*; import java.util.*;
@Entity @Table(name="preferences") @Getter @Setter @NoArgsConstructor public class Preference {
 @Id private UUID id=UUID.randomUUID(); @ManyToOne(optional=false) @JoinColumn(name="camper_id") private Camper camper; @Column(name="raw_name",nullable=false) private String rawName; @ManyToOne @JoinColumn(name="matched_camper_id") private Camper matchedCamper; @Enumerated(EnumType.STRING) @Column(nullable=false) private Domain.PreferenceStatus status; private Double similarity; private String alternatives;
}
