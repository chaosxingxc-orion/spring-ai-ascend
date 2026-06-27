CREATE TABLE cloud_sessions (
    id UUID PRIMARY KEY,
    expert_id VARCHAR(128) NOT NULL,
    title VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL,
    manifest_json TEXT NOT NULL,
    runtime_base_url VARCHAR(2048),
    sandbox_id VARCHAR(128),
    linked_session_id UUID,
    last_error VARCHAR(2000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    destroyed_at TIMESTAMP
);

CREATE INDEX idx_cloud_sessions_status ON cloud_sessions (status);
CREATE INDEX idx_cloud_sessions_created_at ON cloud_sessions (created_at DESC);
