-- FEAT-013 (arch-driven G5-E): add correlation_id + event_type to the forwarding outbox.
--
-- The V1 outbox schema predates FEAT-013's envelope extensions; the
-- correlationId / eventType fields on ForwardingOutboxRecord were therefore null for
-- JDBC-loaded rows (the in-memory outbox persisted them, the JDBC adapter did not —
-- see ForwardingSqlCodec.mapOutbox + JdbcForwardingOutbox.enqueue before this change).
--
-- FEAT-013's two-hop relay needs BOTH on the broker hop:
--   * correlation_id — the forward relay's correlation-match governance compares the
--     native header correlationId to the descriptor's; without it the gateway-produced
--     hop1 carries nativeCorr=null and is rejected as a poison (G5-E surfaced this).
--   * event_type — the gateway's acceptWindow classifies responses by the NATIVE
--     eventType header; without it resp_out carries no eventType and classify yields
--     UNKNOWN (the response is never matched).
-- Both are nullable for backwards-compat with pre-FEAT-013 rows (control-only /
-- JDBC-back-compat records legitimately carry null). Anticipated by the V1 +
-- ForwardingSqlCodec comments ("a real correlation_id column is deferred until
-- FEAT-013 wires JDBC") — this migration is that wiring. No new CHECK / index / RLS
-- clause: the existing row-level tenant_isolation policy + status invariants cover
-- these additive nullable columns.

ALTER TABLE agent_bus_forwarding_outbox ADD COLUMN correlation_id VARCHAR(128);
ALTER TABLE agent_bus_forwarding_outbox ADD COLUMN event_type VARCHAR(64);
