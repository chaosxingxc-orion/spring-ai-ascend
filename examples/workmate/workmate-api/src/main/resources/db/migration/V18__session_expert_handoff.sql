ALTER TABLE sessions ADD COLUMN pending_handoff_generation INTEGER;
ALTER TABLE sessions ADD COLUMN pending_handoff_from_expert_id VARCHAR(128);
