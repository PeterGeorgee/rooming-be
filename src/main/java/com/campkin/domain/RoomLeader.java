package com.campkin.domain;
import jakarta.persistence.*; import lombok.*; import java.util.*;
@Entity @Table(name="room_leaders") @Getter @Setter @NoArgsConstructor public class RoomLeader {
 @Id private UUID id=UUID.randomUUID(); @ManyToOne(optional=false) @JoinColumn(name="managed_room_id") private Room managedRoom; @Column(nullable=false) private String name; @ManyToOne @JoinColumn(name="sleep_room_id") private Room sleepRoom;
}
