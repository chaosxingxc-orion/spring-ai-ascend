# as-built · module — forwarding-reorg (G6)

Redrawn from the REAL post-G5 code (the sub-agent implementation). Delta palette: green = added/moved (landed), amber = modified (landed), purple = the landed ArchUnit rule. The plan landed as designed **except the rule-scope** (user-accepted drift: `gateway↛forwarding.runtime` → `gateway↛forwarding.runtime.relay`) + 3 mechanical drifts. See `module-diff.md`.

## Structure (as-built)

`com.huawei.ascend.bus` (ONE Maven module; spring-boot repackage + `@Profile` UNCHANGED — Q1=b):

- **`forwarding/common/`** [CFG] — `AgentBusBrokerProperties` (← top-level `common/`, eliminated).
- **`forwarding/spi/`** [PORT] — existing ~18 forwarding SPI (unchanged) + NEW **`spi/broker/`** (5 broker SPI: `BrokerForwardingConsumerPort`/`BrokerForwardingRelayPort` interfaces + `BrokerInboundMessage`/`BrokerProduceOutcome`/`DeliveryFilter` records).
- **`forwarding/runtime/`** [LIB]:
  - `runtime/relay/` — `EventBusRelayWorker`/`RelayTick`/`RelayDispatchLoop` (relay mechanism) + the 3 event-bus wiring files (`EventBusRelayConfiguration`/`EventBusRelaySchedulingConfig`/`RelayScheduler`) folded in (← top-level `eventbus.runtime/`, eliminated).
  - `runtime/persistence/jdbc/` [DB] — unchanged.
  - `runtime/transport/` [PORT] — resolvers + `a2a/` (unchanged) + `broker/` (4 common stay: `BrokerClientProperties`/`BrokerOutboundMessage`/`BrokerMessageHeaders`/`BrokerControlDescriptor`) + NEW `broker/rocketmq/` (2 impl).
- **`gateway/runtime/`** [SRV] — 3 files; `GatewayRuntimeConfiguration` import → `forwarding.common.AgentBusBrokerProperties`.
- **`registry.runtime/`** [SRV] — out of scope.
- **Eliminated:** top-level `common/` + top-level `eventbus.runtime/`.

## ArchUnit (as-built, ADR-0163)

- **`gateway.runtime.. ↛ forwarding.runtime.relay..`** (the enforced boundary — green; gateway imports nothing from the relay package). **Drift:** narrower than the to-be's `↛forwarding.runtime..` (which was infeasible — the gateway legitimately wires `forwarding.runtime.{broker, jdbc, transport}` as a producer/consumer). **User-accepted** (2026-07-16). Liveness guard: `gateway.runtime` + `forwarding.runtime.relay` non-empty.
- **Purity exemptions** (deviation (b) — the fold-in brought Spring/RocketMQ-using wiring under `forwarding..` rules): `AgentBusForwardingSpiPurityTest` exempts `forwarding.runtime.relay` (Spring/`javax.sql`/RocketMQ) + `forwarding.common` (Spring) — mirroring the existing `persistence.jdbc`/`transport.broker` exemption pattern; `AgentBusForwardingRuntimeContractTest` + `AgentBusForwardingDesignContractTest` add `forwarding.spi.broker` to path-exclusions/sanctioned-homes.
- `AgentBusForwardingSpiPurityTest`'s rocketmq rules cover `transport.broker.rocketmq` via the `..` wildcard (green, no change).

## Build (as-built, Q1=b)

Single Maven module + `spring-boot-maven-plugin repackage` + `@Profile` UNCHANGED. `./mvnw -f agent-bus/pom.xml test` → **443 green / 14 skip / 0 fail** (BUILD SUCCESS; ArchUnit test count net 0 — 2 boundary tests replaced by 2).

## Tests (as-built)

- 5 tests MOVED (build-forced by package-private access — deviation (a)): `EventBusRelaySchedulerTest` → `forwarding/runtime/relay/`; the 4 rocketmq tests (`RocketMqBrokerForwardingConsumerTest`/`ConsumerLifecycleTest`/`RelayTest`/`RealBrokerTwoHopRelayIntegrationTest`) → `broker/rocketmq/` (they call `sql92Expression`/`buildMessage`/`drainAll` package-private seams).
- 5 broker tests stayed import-only (`BrokerForwardingPortsContractTest`/`InMemoryBroker`/`DeliveryFilterTest`/`RealBrokerProduceSideIT`/`RealBrokerResponseSideIT` — public API only).
