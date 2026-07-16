# as-is · module — forwarding-reorg

The CURRENT `agent-bus` package structure (post the just-closed `agent-bus-layering` refactor) + its build. Drawn from real code (`agent-bus/src/main/java/com/huawei/ascend/bus/` + `agent-bus/pom.xml`). Uniform snapshot (before the reorg); module type via `[tag]`, never by fill.

## Structure

`com.huawei.ascend.bus` (ONE Maven module `agent-bus`; `AgentBusApplication` @SpringBootApplication; spring-boot repackage → 1 fat-jar; 2 process forms via `SPRING_PROFILES_ACTIVE`):

- **`forwarding/`** — the pure-Java forwarding substrate (the reorg anchor):
  - `forwarding/spi/` [PORT] — the forwarding SPI surface: `ForwardingEnvelope`/`AgentBusEventType`/`InvocationResponseStatus`, `ForwardingInboxPort`/`ForwardingOutboxPort`/`ForwardingOutboxClaimPort`, `ForwardingDeliveryPort`/`ForwardingDispatcher`/`ForwardingDeliveryResult`, + value types (`ForwardingRouteHandle`/`ForwardingLease`/`ForwardingReceipt`/`ForwardingStatus`/`ForwardingFailureCode`/`ForwardingInboxRecord`/`ForwardingOutboxRecord`/`ForwardingMessageId`). Pure Java, no Spring/JDBC/broker.
  - `forwarding/runtime/` [LIB]:
    - `runtime/` (core) — `ForwardingDispatchLoop`/`ForwardingDispatcherWorker`/`ForwardingStateMachine`/`ForwardingRetryPolicy`/`ForwardingCircuitBreaker`/`RouteCircuitBreaker`/`EpochClock`.
    - `runtime/relay/` — `EventBusRelayWorker`/`RelayTick`/`RelayDispatchLoop`.
    - `runtime/persistence/jdbc/` [DB] — `JdbcForwardingInbox`/`JdbcForwardingOutbox`/`ForwardingSqlCodec`. **ArchUnit:** Spring + `java.sql`/`javax.sql` confined here (the only Spring-permitted subpackage in forwarding).
    - `runtime/transport/` [PORT] — `BrokerTopicResolver`/`ForwardingEndpointResolver`/`MapEndpointResolver`.
      - `transport/a2a/` — `A2aForwardingDeliveryPort`/`A2aForwardingProperties`. **ArchUnit:** A2A SDK confined here.
      - `transport/broker/` — **11 classes MIXED** (the Q3 split target):
        - [PORT] `BrokerForwardingConsumerPort` / `BrokerForwardingRelayPort` — SPI interfaces.
        - [VO+CFG] `BrokerClientProperties` / `DeliveryFilter` / `BrokerInboundMessage` / `BrokerOutboundMessage` / `BrokerProduceOutcome` / `BrokerMessageHeaders` / `BrokerControlDescriptor` — common pub/sub value-objects + config.
        - [IMPL] `RocketMqBrokerForwardingConsumer` / `RocketMqBrokerForwardingRelay` — rocketmq-specific impl.
        **ArchUnit:** RocketMQ client confined here; `RocketMq*` adapters must reside here.
- **`common/`** [CFG] — `AgentBusBrokerProperties` (`@ConfigurationProperties("agent-bus")`, 12 fields, shared by both planes). Top-level (from the just-closed refactor).
- **`eventbus.runtime/`** [SRV] — the 3 event-bus wiring files: `EventBusRelayConfiguration` (@Profile(eventbus)), `EventBusRelaySchedulingConfig`, `RelayScheduler`. Top-level (from the just-closed refactor).
- **`gateway.runtime/`** [SRV] — `GatewayRuntimeConfiguration` (@Profile(gateway)), `GatewayRuntimeController` (@RestController), `GatewayRuntimeService` (IngressGateway).
- **`registry.runtime/`** [SRV] — registry plane wiring (out of scope).

Note: the top-level `com.huawei.ascend.bus.spi/` (engine / federation / ingress / registry / s2c — the cross-plane bus SPI, e.g. `IngressGateway`, `S2cCallbackTransport`) is **separate** from `forwarding/spi/` and is out of scope.

## ArchUnit constraints (as-is — the reorg must keep green or evolve)

- `AgentBusForwardingSpiPurityTest`: `forwarding..` (outside `persistence.jdbc`) must not import Spring/JDBC; (outside `transport.a2a`) must not import A2A SDK; (outside `transport.broker`) must not import RocketMQ; + adapters named `RocketMq*` must reside in `transport.broker`.
- `AgentBusDependencyBoundaryTest`: `bus..` must not depend on sibling platform modules (agent-runtime/service/engine/...); + the `gateway.runtime ↛ eventbus.runtime` rule (ADR-0162).
- The reorg's broker SPI extraction (ports ↑ to `forwarding/spi`) + rocketmq split (→ `broker/rocketmq`) + event-bus fold (→ `forwarding`) will TOUCH these — the to-be must update them (e.g. the `gateway↛eventbus` rule becomes `gateway↛forwarding.runtime` impl, or is reframed).

## Dependency direction (as-is)

- `eventbus.runtime` → `common` + `forwarding.spi` + `forwarding.runtime.{relay, transport.broker, persistence.jdbc}`.
- `gateway.runtime` → `common` + `forwarding.spi` + `forwarding.runtime.{transport.broker, persistence.jdbc}`.
- `gateway.runtime` ↛ `eventbus.runtime` (ArchUnit, ADR-0162).
- `forwarding.runtime` → `forwarding.spi` (impl → SPI).

## Build (as-is)

Single Maven module (`agent-bus`), `spring-boot-maven-plugin repackage` → one runnable fat-jar; two process forms (gateway / eventbus+registry) selected at launch by `SPRING_PROFILES_ACTIVE`. The "build different packages" requirement (event-bus process / SDK / gateway process) starts from this single-module+@Profile base — Q1 decides whether to split into Maven multi-module.
