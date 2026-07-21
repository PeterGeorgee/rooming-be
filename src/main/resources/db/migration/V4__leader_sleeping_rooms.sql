ALTER TABLE rooms ADD COLUMN leader_sleep_room_id UUID;

UPDATE rooms SET leader_sleep_room_id = id WHERE leader_name IS NOT NULL;

ALTER TABLE rooms
    ADD CONSTRAINT fk_rooms_leader_sleep_room
    FOREIGN KEY (leader_sleep_room_id) REFERENCES rooms(id)
    ON DELETE SET NULL;
