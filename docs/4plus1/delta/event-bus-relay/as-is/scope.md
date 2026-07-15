# Scope — event-bus two-hop relay gap (as-is)

**Change slug:** `event-bus-relay`
**G2 mode:** code-driven (no `docs/4plus1/` baseline; drawn from real code + `architecture/L2-Low-Level-Design/agent-bus/feat-013|014` spec). G1 surfaced the 4+1-format baseline absent; user picked code-driven (route G2 on what's present).

## In scope (modules this change touches)
- **gateway runtime:** `GatewayRuntimeService` (`dispatchRequest` synchronous inline `:170`; `acceptWindow`; class javadoc defers controller/async-worker/SSE to S4).
- **forwarding substrate (library, UNWIRED):** `ForwardingDispatcherWorker`, `ForwardingDispatchLoop`, `ForwardingStateMachine`, `ForwardingCircuitBreaker`, `ForwardingRetryPolicy`, `EpochClock`.
- **forwarding SPI:** `ForwardingOutboxPort`, `ForwardingInboxPort`, `ForwardingOutboxClaimPort`, `ForwardingDeliveryPort`, `ForwardingDispatcher`, `ForwardingEnvelope`/`AgentBusEventType`/`InvocationResponseStatus`.
- **broker SPI + RocketMQ adapter:** `BrokerForwardingRelayPort`/`RocketMqBrokerForwardingRelay` (produce); `BrokerForwardingConsumerPort`/`RocketMqBrokerForwardingConsumer` (consume, §7 slice2/3 bySql/LitePull, committed `201f119c`).
- **transport/a2a:** `A2aForwardingDeliveryPort` (Stage-15 T1 HTTP push; to-be replaced by broker pub/sub; the only `ForwardingDeliveryPort` impl).
- **JDBC persistence:** `JdbcForwardingOutbox`/`JdbcForwardingInbox`/`ForwardingSqlCodec`.
- **entry point:** `AgentBusApplication` (single `@SpringBootApplication`; boots registry plane only).
- **deployment:** root `pom.xml`, `agent-bus/pom.xml`, `docker-compose.yml`, root `Dockerfile`.
- **validation:** slice-3 IT `RealBrokerResponseSideIntegrationTest` (single-hop; relay bypass).

## Out of scope (explicitly)
- External repo `agent-runtime-java` hop2 consumer/producer (parallel, not time-locked; cross-repo boundary only).
- Broker-filtering §7 SPI (committed slices 1–5 + 2-lifecycle + 3, `201f119c`; done).
- Registry plane internals (`MvpRegistryController`/`PgMvpDiscoveryServiceImpl`/`MvpHealthProbeScheduler` — already booted, not changing).
- L0/L1/L2 architecture docs themselves (reference authority, not code under change).
- Deferred slice-3 review 5 findings (advisory; orthogonal — handoff §2).

## Why these three diagrams (module / flow / sequence), not all of 4+1
This change moves **module boundaries** (wired-vs-unwired today → two-process split to-be) → module; **control flow** (synchronous inline dispatch → consume→govern→re-produce relay) → flow; **cross-component ordering over time** (gateway→broker→event-bus→broker→runtime two-hop + symmetric response) → sequence. Deployment topology is a *delta* expressed on the module/sequence diagrams (process split + artifacts), not a separate physical-view deliverable — the project's physical view lives in `architecture/L1-High-Level-Design/agent-bus/physical.md`, referenced not redrawn. No logical-view or scenarios-view redraw needed (L2 feat-013/014 already authoritative).
