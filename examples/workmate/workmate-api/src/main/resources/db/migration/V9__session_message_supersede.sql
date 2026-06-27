ALTER TABLE session_messages
    ADD COLUMN superseded BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_session_messages_session_superseded_seq
    ON session_messages (session_id, superseded, seq);

ALTER TABLE sessions
    ADD COLUMN conversation_generation INT NOT NULL DEFAULT 0;
