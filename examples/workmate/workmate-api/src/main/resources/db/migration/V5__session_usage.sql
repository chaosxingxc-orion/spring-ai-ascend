CREATE TABLE session_usage (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    prompt_tokens INT NOT NULL,
    completion_tokens INT NOT NULL,
    model VARCHAR(128),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_session_usage_session_id ON session_usage (session_id);
