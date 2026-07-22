CREATE TABLE app_users (
  id UUID PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(254) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_sessions (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_sessions_user ON user_sessions(user_id);

CREATE TABLE camp_memberships (
  id UUID PRIMARY KEY,
  camp_id UUID NOT NULL REFERENCES camps(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  role VARCHAR(20) NOT NULL,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(camp_id, user_id)
);
CREATE INDEX idx_membership_user ON camp_memberships(user_id);

ALTER TABLE camps ADD COLUMN join_code VARCHAR(12);
UPDATE camps SET join_code = upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 8));
ALTER TABLE camps ALTER COLUMN join_code SET NOT NULL;
ALTER TABLE camps ADD CONSTRAINT uk_camps_join_code UNIQUE(join_code);
