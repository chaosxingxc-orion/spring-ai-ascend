-- W0 init migration. Per docs/cross-cutting/data-model-conventions.md sec-12.
-- Adds the singleton health_check row that HealthController pings on every request.

CREATE TABLE health_check (
    singleton  boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    created_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO health_check (singleton) VALUES (true);

-- Note: tenant tables + RLS policies + assertion triggers land in V2 (W1).
