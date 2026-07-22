CREATE TABLE caring_groups (
    id UUID PRIMARY KEY,
    camp_id UUID NOT NULL REFERENCES camps(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    leader_name VARCHAR(180) NOT NULL,
    gender VARCHAR(20) NOT NULL,
    UNIQUE(camp_id, name)
);

ALTER TABLE campers ADD COLUMN caring_group_id UUID REFERENCES caring_groups(id) ON DELETE SET NULL;
CREATE INDEX idx_camper_caring_group ON campers(caring_group_id);
