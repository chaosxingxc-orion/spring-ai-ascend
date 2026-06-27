CREATE TABLE automation_jobs (
    id UUID PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    expert_id VARCHAR(128),
    prompt_text TEXT NOT NULL,
    cron_expression VARCHAR(128) NOT NULL DEFAULT '0 9 * * *',
    next_run_at TIMESTAMP,
    last_run_at TIMESTAMP,
    last_session_id UUID,
    last_status VARCHAR(32),
    last_error VARCHAR(2048),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_automation_jobs_next_run ON automation_jobs (enabled, next_run_at);
