CREATE TABLE leaders (
    id UUID PRIMARY KEY,
    camp_id UUID NOT NULL REFERENCES camps(id) ON DELETE CASCADE,
    name VARCHAR(180) NOT NULL,
    normalized_name VARCHAR(180) NOT NULL,
    gender VARCHAR(20) NOT NULL,
    UNIQUE(camp_id, normalized_name)
);
CREATE INDEX idx_leaders_camp ON leaders(camp_id);

WITH candidates AS (
    SELECT r.camp_id, rl.name, lower(regexp_replace(trim(rl.name), '\s+', ' ', 'g')) normalized_name, r.gender, 1 priority FROM room_leaders rl JOIN rooms r ON r.id=rl.managed_room_id
    UNION ALL SELECT cg.camp_id, cg.leader_name, lower(regexp_replace(trim(cg.leader_name), '\s+', ' ', 'g')), cg.gender, 2 FROM caring_groups cg
    UNION ALL SELECT dg.camp_id, gl.name, lower(regexp_replace(trim(gl.name), '\s+', ' ', 'g')), 'UNKNOWN', 3 FROM group_leaders gl JOIN discussion_groups dg ON dg.id=gl.group_id
), chosen AS (
    SELECT DISTINCT ON (camp_id, normalized_name) camp_id,name,normalized_name,gender FROM candidates ORDER BY camp_id,normalized_name,priority
)
INSERT INTO leaders(id,camp_id,name,normalized_name,gender) SELECT gen_random_uuid(),camp_id,name,normalized_name,gender FROM chosen;
