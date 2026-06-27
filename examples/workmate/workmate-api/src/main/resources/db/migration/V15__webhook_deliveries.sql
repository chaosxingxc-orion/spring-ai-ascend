CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY,
    channel VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    session_id UUID,
    message VARCHAR(2048),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_webhook_deliveries_created ON webhook_deliveries (created_at DESC);
