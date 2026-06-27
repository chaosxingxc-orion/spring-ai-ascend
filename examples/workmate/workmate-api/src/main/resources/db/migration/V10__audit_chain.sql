-- W31: Tamper-evident audit hash chain (derived from append-only run_events).

CREATE TABLE audit_chain (
    seq_global BIGSERIAL PRIMARY KEY,
    run_event_id UUID NOT NULL UNIQUE,
    session_id UUID NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    event_name VARCHAR(64) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    prev_hash VARCHAR(64) NOT NULL,
    entry_hash VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_audit_chain_created_at ON audit_chain (created_at);
CREATE INDEX idx_audit_chain_session ON audit_chain (session_id, seq_global);
CREATE INDEX idx_audit_chain_category ON audit_chain (category, seq_global);

CREATE TABLE audit_segments (
    segment_date DATE PRIMARY KEY,
    entry_count BIGINT NOT NULL,
    first_hash VARCHAR(64) NOT NULL,
    last_hash VARCHAR(64) NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    closed_at TIMESTAMP NOT NULL
);

CREATE TABLE audit_chain_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    last_seq_global BIGINT NOT NULL DEFAULT 0,
    last_hash VARCHAR(64) NOT NULL,
    last_run_event_created_at TIMESTAMP,
    current_segment_date DATE
);

INSERT INTO audit_chain_state (id, last_seq_global, last_hash)
VALUES (
    1,
    0,
    '0000000000000000000000000000000000000000000000000000000000000000'
);

CREATE OR REPLACE FUNCTION workmate_deny_audit_chain_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_chain is append-only (WORM): UPDATE/DELETE denied';
END;
$$;

DROP TRIGGER IF EXISTS audit_chain_worm_guard ON audit_chain;

CREATE TRIGGER audit_chain_worm_guard
    BEFORE UPDATE OR DELETE ON audit_chain
    FOR EACH ROW
    EXECUTE FUNCTION workmate_deny_audit_chain_mutation();

CREATE OR REPLACE FUNCTION workmate_deny_audit_segments_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_segments is append-only (WORM): UPDATE/DELETE denied';
END;
$$;

DROP TRIGGER IF EXISTS audit_segments_worm_guard ON audit_segments;

CREATE TRIGGER audit_segments_worm_guard
    BEFORE UPDATE OR DELETE ON audit_segments
    FOR EACH ROW
    EXECUTE FUNCTION workmate_deny_audit_segments_mutation();
