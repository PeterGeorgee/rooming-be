ALTER TABLE rooms
    ADD COLUMN extra_beds INTEGER NOT NULL DEFAULT 0 CHECK (extra_beds >= 0);
