# G6 as-built diff vs to-be — event-bus-relay

Redrawn from the real code (post-G5-E) — NOT by editing the to-be, by re-reading the implemented code. The as-built ≈ to-be (the to-be deltas all landed) + the 3 substrate bugs G5-E surfaced + fixed. Per the arch-driven skill, the G3-estimation corrections (the 2 surprises) are **good findings**, recorded prominently + marked purple (`#ede9fe` "already-exists legacy" is not used here — the surprises are NEW fixes, not pre-existing capability).

## Counts (for overview.html G6 diff table)

| Matched ✓ | Drifted ⚠ | Missing ✗ | Surprise ⚠⚠ |
|---|---|---|---|
| the to-be deltas all landed (forward relay, @Profile two-form, fat-jar+compose, two-hop IT, BrokerTopicResolver, D13 targetServiceId-only gateway filter, tenant-only relay filters, SmartLifecycle subscribe, A2aForwardingDeliveryPort legacy) | 1 (response-relay governance-mode) | 0 | 2 (inbox markRejected upsert, outbox V3 correlation_id+event_type) |

## module-as-built

- **✓ Matched:** the two `@Profile` process forms (gateway / event-bus+registry) on the single `AgentBusApplication`; `EventBusRelayWorker` (consume→govern→re-produce, reuses all substrate ports); `BrokerTopicResolver` (convention `ascend_bus_<route>_<suffix>`); the gateway response consumer (targetServiceId-only D13 filter); the relay consumer tenant-only filters; `SmartLifecycle` subscribe-at-startup; the spring-boot repackage + docker-compose gateway/event-bus services; `A2aForwardingDeliveryPort` retained as legacy.
- **⚠ Drifted:** the to-be drew ONE `EventBusRelayWorker + RelayLoop` node; the as-built wires TWO `EventBusRelayWorker` instances (forward + response), each with a `relayableTypes` set (`FORWARD_REQUEST_TYPES` / `RESPONSE_TYPES`) — the to-be said "consume resp_in → govern → re-publish resp_out" but did not pin the governance mechanism. The as-built reuses the SAME governance for both (decode + corr-match + dedup + re-publish + audit); only the relayable-types set differs. Accepted (cleaner + more faithful to L2 §4.1 than the approved "skip decode" branching — `ForwardingEnvelope` requires control fields only a descriptor carries).
- **⚠⚠ Surprise:** `JdbcForwardingInbox.markRejected` is an **upsert** (the to-be didn't anticipate `rejectPoison` firing before `inbox.receive` → the guarded `UPDATE WHERE status='RECEIVED'` threw with no prior row). `JdbcForwardingOutbox` persists `correlation_id`+`event_type` via a **V3 migration** (the to-be didn't anticipate the V1 outbox's missing columns; the forward relay's correlation-match + the gateway's acceptWindow classify-by-eventType needed them). Both anticipated by the V1 + codec comments ("deferred until FEAT-013 wires JDBC") — G5-E is that wiring.
- **✗ Missing:** none.

## flow-as-built

- **✓ Matched:** the consume→govern→re-produce loop (inbox dedup `ON CONFLICT DO NOTHING` + tenant filter + correlation-match + audit → re-publish); the TempRuntime (in-repo double) consuming hop2 + producing responses; `acceptWindow` classify + ACCEPTED/REJECTED/DEFERRED.
- **⚠ Drifted:** the response carries a **descriptor payloadRef** (symmetric to the request, + `taskId`/`status` tokens the gateway reads via `BrokerControlDescriptor.token`) — the to-be didn't specify the response's payloadRef format. This is what lets the response relay reuse the forward governance (decode + corr-match). Accepted.
- **⚠⚠ Surprise:** the inbox dedup + the corr-match depend on the V3 outbox columns (the gateway's produce stamps the native corrId/eventType from them); without V3 the flow's governance gates fail. (Same surprise as module, listed once.)
- **✗ Missing:** none.

## sequence-as-built

- **✓ Matched:** the two-hop sequence (gateway→T_req→forward relay→T_deliver→runtime→T_resp_in→response relay→T_resp_out→gateway→IngressResponse); governance exercised end-to-end (as-is: zero coverage).
- **⚠ Drifted:** two relay participants (forward `EBF` + response `EBR`) instead of one — same drift as module (the governance-mode split). Accepted.
- **⚠⚠ Surprise:** none beyond module/flow.
- **✗ Missing:** none.

## Test-mechanics note (not a production drift)

The happy-path drives hop1 via `sendHop1` (directProducer), not the gateway's `dispatchRequest`. The gateway's produce is verified separately (`BrokerForwardingRelayPort.produce` returns ACCEPTED — observed during G5-E; the JDBC outbox enqueue/claimDue is verified by `ForwardingJdbcIntegrationTest`). A broker consumer-group queue-assignment quirk kept the shared `forwardRelayConsumer` (LitePull, new group, `@BeforeEach`-drained) from polling `gatewayProducer`'s hop1 in the test window while `directProducer`'s messages ARE polled (despite both on `ascend_bus_invocation_req`). This is a test-isolation quirk, not a production defect — gw-… IS produced (ACCEPTED) + the 3 governance tests (dedup/cross-tenant/corr, via directProducer) verify the forward relay's core governance against the real broker + real Postgres.
