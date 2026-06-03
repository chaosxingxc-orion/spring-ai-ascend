-- L1 idempotency dedup table (ADR-0057). Tenant-scoped composite key with a
-- request_hash column to detect body drift, and a CHECK constraint on status to
-- keep illegal values out at the storage layer (Rule 28, enforcer E13).

CREATE TABLE idempotency_dedup (
    tenant_id          UUID         NOT NULL,
    idempotency_key    UUID         NOT NULL,
    request_hash       VARCHAR(64)  NOT NULL,
    status             VARCHAR(32)  NOT NULL,
    response_status    INTEGER,
    response_body_ref  VARCHAR(512),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at       TIMESTAMPTZ,
    expires_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_idempotency_dedup PRIMARY KEY (tenant_id, idempotency_key),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('CLAIMED', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_idempotency_dedup_expires ON idempotency_dedup (expires_at);
