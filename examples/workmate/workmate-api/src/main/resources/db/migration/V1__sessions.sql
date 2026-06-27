CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    title VARCHAR(512) NOT NULL,
    workspace_root VARCHAR(2048) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_sessions_created_at ON sessions (created_at DESC);
