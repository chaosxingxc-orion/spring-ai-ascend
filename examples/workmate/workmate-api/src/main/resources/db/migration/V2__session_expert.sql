ALTER TABLE sessions ADD COLUMN expert_id VARCHAR(128);

CREATE INDEX idx_sessions_expert_id ON sessions (expert_id);
