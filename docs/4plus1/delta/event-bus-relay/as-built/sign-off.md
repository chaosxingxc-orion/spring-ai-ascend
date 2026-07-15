# G6 as-built sign-off — event-bus-relay

**Date:** 2026-07-15.
**State:** as-built 三件套 redrawn from the real code (post-G5-E) + diffed vs to-be.

## As-built vs to-be — every ⚠ / ✗ / ⚠⚠ resolved

- **⚠ Drifted (1):** response-relay governance-mode — the to-be left the response relay's governance unpinned; the as-built adds a `relayableTypes` set (`FORWARD_REQUEST_TYPES` / `RESPONSE_TYPES`) + the response carries a descriptor symmetric to the request, so both relays reuse the SAME governance. **Decision: accept as-built** (cleaner + more faithful to L2 §4.1 than the approved "skip decode" branching — `ForwardingEnvelope` requires control fields only a descriptor carries). To-be updated via `deviations.md` + ADR.
- **⚠⚠ Surprise (2):** (a) `JdbcForwardingInbox.markRejected` upsert — `rejectPoison` fires before `inbox.receive`, the guarded UPDATE threw; (b) V3 outbox `correlation_id`+`event_type` — V1 didn't persist them, the forward relay's corr-match + the gateway's classify needed them. Both are GOOD findings (G3-estimation corrections surfaced by G5-E — the skill's quality signal), both FIXED + recorded in `deviations.md` + ADR. **Decision: accept as-built (the fixes).**
- **✗ Missing (0):** all to-be deltas landed.
- **✓ Matched:** the forward relay, the @Profile two-form wiring, the fat-jar+compose, the two-hop IT (4/4 green broker env), the BrokerTopicResolver, the D13 gateway filter, the tenant-only relay filters, the SmartLifecycle subscribe, the A2aForwardingDeliveryPort legacy.

## Verification

- `RealBrokerTwoHopRelayIntegrationTest` 4/4 green (broker env) — happy-path two-hop→ACCEPTED, dedup-on-replay (real `ON CONFLICT`), cross-tenant broker-side bySql, corr-mismatch→REJECTED-audit.
- Full suite 434 green / 14 skip / 0 fail (no regression).
- 3 substrate bugs fixed (#1 worker governance-mode, #2 inbox upsert, #3 outbox V3).

**Outcome:** as-built accepted (with the 1 drift + 2 surprises accepted as the G5-E-surfaced fixes). Proceed to G7 (ADR + overview refresh + CLOSED).
