ALTER TABLE sessions
    ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE sessions
    ADD COLUMN archived_at TIMESTAMP NULL;

CREATE INDEX idx_sessions_list_order ON sessions (archived_at NULLS FIRST, pinned DESC, updated_at DESC);
