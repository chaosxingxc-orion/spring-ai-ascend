CREATE TABLE session_messages (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    session_id UUID NOT NULL,
    run_id VARCHAR(64),
    seq INTEGER NOT NULL,
    payload_json VARCHAR(65535) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_session_messages_session_seq ON session_messages (session_id, seq);

CREATE TABLE run_events (
    id UUID NOT NULL PRIMARY KEY,
    session_id UUID NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    seq INTEGER NOT NULL,
    event_name VARCHAR(64) NOT NULL,
    payload_json VARCHAR(65535) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX idx_run_events_session_seq ON run_events (session_id, seq);
CREATE INDEX idx_run_events_session_run ON run_events (session_id, run_id);
