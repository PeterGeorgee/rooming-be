CREATE TABLE room_leaders (
    id UUID PRIMARY KEY,
    managed_room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    name VARCHAR(180) NOT NULL,
    sleep_room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE CASCADE
);

INSERT INTO room_leaders (id, managed_room_id, name, sleep_room_id)
SELECT gen_random_uuid(), id, leader_name, COALESCE(leader_sleep_room_id, id)
FROM rooms
WHERE leader_name IS NOT NULL;

CREATE TABLE group_leaders (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES discussion_groups(id) ON DELETE CASCADE,
    name VARCHAR(180) NOT NULL
);
