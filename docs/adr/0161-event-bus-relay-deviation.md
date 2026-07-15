---
level: L1
view: process
status: active
authority: "arch-driven G6/G7 (event-bus-relay); G5-E two-hop IT 2026-07-15"
---

# ADR-0161: event-bus-relay — accepted as-built deviation(s)

- **Status:** Accepted
- **Date:** 2026-07-15
- **Change slug:** `event-bus-relay`
- **Supersedes:** —
- **Related:** `docs/4plus1/delta/event-bus-relay/`

## Context

The arch-driven event-bus two-hop relay gap (FEAT-013/014) was delivered G1 code-driven → G4 to-be signed off (2026-07-15, `docs/4plus1/delta/event-bus-relay/to-be/sign-off.md`) → G5 implemented (G5-A/B/C/D + G5-E). The to-be (decision tree Q1a/Q2a/Q3a/Q4a) planned a new `EventBusRelayWorker` (consume→govern→re-produce, reusing all substrate ports), Spring `@Profile` two-form wiring on the single `AgentBusApplication`, spring-boot repackage + docker-compose, and an in-repo two-hop IT. G5-E — the boot-verification deferred from G5-B (`RealBrokerTwoHopRelayIntegrationTest`, real broker + real `JdbcForwardingInbox`/`Outbox` + embedded-postgres) — surfaced three substrate bugs the unit tests (`InMemoryForwardingOutbox` + `FakeInbox`) hid + one governance-mechanism drift the to-be left unpinned. All four are accepted as-built (the 3 bugs are FIXED; the drift is a cleaner design than the to-be anticipated). Recorded in `docs/4plus1/delta/event-bus-relay/deviations.md` + `as-built/diff.md` + `as-built/sign-off.md`.

## Deviations accepted

### Deviation 1 — response-relay governance-mode (forward vs response)

- **To-be said:** "response relay — consume resp_in → govern → re-publish resp_out" (the governance mechanism was NOT pinned; the to-be assumed a single `EventBusRelayWorker` shape).
- **As-built delivered:** `EventBusRelayWorker` takes a `Set<AgentBusEventType> relayableTypes` constructor param — the forward relay is constructed with `FORWARD_REQUEST_TYPES` (request eventTypes), the response relay with `RESPONSE_TYPES` (response/terminal eventTypes). Both reuse the SAME governance (descriptor decode + correlation-match + inbox-dedup + re-publish + audit). Responses carry a `BrokerControlDescriptor` payloadRef symmetric to requests (control fields recovered from it; `taskId`/`status` ride as extra tokens the gateway reads via `BrokerControlDescriptor.token`).
- **Reason for deviation:** `ForwardingEnvelope` requires non-blank `traceId`/`idempotencyKey`/`capability`/`routeHandle` that `BrokerInboundMessage` doesn't carry — only a descriptor provides them, so the approved "skip descriptor decode" was infeasible. Reusing forward governance (decode + corr-match) for the response relay is cleaner + more faithful to L2 §4.1 ("Inbox dedup + correlation match") than governance-mode branching.
- **User decision:** Accepted on 2026-07-15 (see `as-built/sign-off.md`).

### Deviation 2 — `JdbcForwardingInbox.markRejected` upsert

- **To-be said:** the to-be assumed `markRejected` (the inbox's REJECTED-audit mutation) behaves as the in-memory double (no prior-row requirement).
- **As-built delivered:** `markRejected` is an **upsert** — INSERT REJECTED if no prior row (the poison-audit row), UPDATE RECEIVED→REJECTED if present, idempotent on already-terminal (the `WHERE status='RECEIVED'` guard on the conflict branch). `markConsumed` stays the guarded `mutate` (a consume always follows a receive).
- **Reason for deviation:** `EventBusRelayWorker.rejectPoison` (governance-2 decode-fail / governance-3 correlation-mismatch) calls `inbox.markRejected` BEFORE `inbox.receive` (those governances fire before governance-4). The real `JdbcForwardingInbox.markRejected` ran `UPDATE … WHERE status='RECEIVED'` (guarded terminal mutation) → 0 rows → `IllegalStateException` (no prior RECEIVED row). The unit test (`FakeInbox.markRejected`) hid it (doesn't require a prior row).
- **User decision:** Accepted on 2026-07-15 (the fix).

### Deviation 3 — V3 outbox `correlation_id` + `event_type`

- **To-be said:** the to-be assumed the JDBC outbox persisted the FEAT-013 envelope fields (`correlationId`/`eventType`) — it did not check the V1 schema.
- **As-built delivered:** V3 migration (`V3__add_outbox_correlation_event_type.sql`) adds nullable `correlation_id` + `event_type` to `agent_bus_forwarding_outbox`; `JdbcForwardingOutbox.enqueue` persists them; `ForwardingSqlCodec.mapOutbox` reads them (`decodeEventType`).
- **Reason for deviation:** the V1 outbox schema predates FEAT-013's envelope extensions; `ForwardingSqlCodec.mapOutbox` hardcoded `correlationId=null`/`eventType=null` with the comment "Add a V3 migration + column read when FEAT-013 wires JDBC." Without them, the gateway's `dispatchRequest` produce stamped no `correlationId`/`eventType` user-properties (`if (record.correlationId() != null)`) → the forward relay's correlation-match (`null ≠ descriptor`) rejected every gateway-produced hop1, AND the gateway's `acceptWindow` (classify by NATIVE `eventType`) would yield UNKNOWN. This IS the FEAT-013 JDBC wiring the V1 comment anticipated.
- **User decision:** Accepted on 2026-07-15 (the fix).

## Consequences

- **New invariant (response relay):** both the forward + response relays share the SAME `EventBusRelayWorker` governance (descriptor decode + correlation-match + inbox-dedup + re-publish + audit); the only per-relay configuration is the `relayableTypes` set. Future relay changes (e.g. a 3rd relay role) add a new type set, not a new governance path. Responses MUST carry a descriptor (symmetric to requests) — the response producer (external `agent-runtime-java`) must stamp it.
- **New invariant (inbox):** `markRejected` is an upsert (no prior `receive` required); `markConsumed` stays the guarded `mutate`. A poison (governance decode/corr failure) is audited REJECTED + committed (no broker redelivery — a redelivery cannot fix a poison + would loop). Future inbox consumers that reject before receive inherit this.
- **New invariant (outbox):** the outbox persists `correlation_id` + `event_type` (V3). The forward relay's correlation-match + the gateway's acceptWindow classify-by-eventType depend on them. Future outbox consumers (e.g. the dispatcher worker) that need the native corrId/eventType inherit them for free.
- **Test-isolation quirk (not a production invariant):** the broker does NOT reliably persist `DefaultLitePullConsumer` offsets across `mvn` runs NOR start a new group at `CONSUME_FROM_LAST_OFFSET` — the IT uses run-unique consumer-group suffixes + a `@BeforeEach` `drainAll` (test-only `RocketMqBrokerForwardingConsumer.drainAll`). Production consumers are long-lived (offsets persist) so this is test-only.
- **NOT a REQ-2026-001 release blocker** (release bar = InMemoryBroker CI gate + real RocketMQ env-guarded + TestAgentRuntime E2E; none require the two-form Spring wiring to boot).

## Updated baseline

This project is code-driven (no `docs/4plus1/` baseline; uses `architecture/L0|L1|L2` + structurizr per ADR-0147/0150/0160), so the `docs/4plus1/<system>-architecture/element-model.yaml` baseline does not apply. The as-built delta artifacts live at `docs/4plus1/delta/event-bus-relay/` (the code-driven G1-G7 set). The V3 migration changed the outbox schema (additive nullable columns) — existing `agent_bus_forwarding_outbox` rows + the C3 JDBC tests are unaffected (the columns are nullable; `ForwardingJdbcIntegrationTest` 17 green confirms). The L2 spec (`feat-013-client-invocation-event-forwarding.md` §4.1) already mandates the response relay (resp_in→resp_out with governance) — the as-built fulfills it; no L2 amendment needed.
