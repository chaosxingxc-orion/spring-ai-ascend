# to-be · module delta — forwarding-reorg

Delta against `as-is/module`. Palette: **green `++` added/moved**, **amber `~` modified**; unchanged context neutral white. No removed/red — nothing deleted; classes MOVE + two top-level packages are eliminated (folded into `forwarding`).

## The delta, package by package

- **`++ forwarding/common/`** (NEW) — `AgentBusBrokerProperties` moves here from top-level `common/` (eliminated). The shared config is now forwarding-internal.
- **`++ forwarding/spi/broker/`** (NEW subpackage) — the broker SPI (5 classes, code-verified no broker-internal refs): `BrokerForwardingConsumerPort`/`BrokerForwardingRelayPort` (interfaces) + `BrokerInboundMessage`/`BrokerProduceOutcome`/`DeliveryFilter` (records), moved from `forwarding/runtime/transport/broker/`. Keeps the broker SPI grouped + visible at the SPI surface. `forwarding/spi/`'s existing ~18 forwarding-domain classes unchanged.
- **`++ forwarding/runtime/relay/`** gains the 3 event-bus wiring files (`EventBusRelayConfiguration`/`EventBusRelaySchedulingConfig`/`RelayScheduler`), moved from top-level `eventbus.runtime/` (eliminated). They join `EventBusRelayWorker`/`RelayTick`/`RelayDispatchLoop` — co-located with the relay mechanism they wire (Q2).
- **`~ forwarding/runtime/transport/broker/`** (MODIFIED) — loses the 5 broker-SPI classes (↑ to `spi/broker/`) + the 2 rocketmq impl (↓ to `broker/rocketmq/`); KEEPS the 4 broker-common classes (`BrokerClientProperties`/`BrokerOutboundMessage`/`BrokerMessageHeaders`/`BrokerControlDescriptor`).
- **`++ forwarding/runtime/transport/broker/rocketmq/`** (NEW) — the 2 rocketmq impl (`RocketMqBrokerForwardingConsumer`/`Relay`), moved from `broker/` root. Sibling dirs for future brokers (kafka/, nats/).
- **`~ gateway/runtime/`** — `GatewayRuntimeConfiguration`'s import follows `AgentBusBrokerProperties` to `forwarding/common`; structure unchanged per target.
- **Eliminated:** top-level `common/` (→ `forwarding/common/`) + top-level `eventbus.runtime/` (→ `forwarding/runtime/relay/`).
- **Build (Q1=b):** single Maven module + `spring-boot repackage` + `@Profile` UNCHANGED. The SDK boundary (Q4) is a documented package-set, not a separate artifact (multi-module is a deferred follow-on).

## Dependency direction (the load-bearing outcome)

- `forwarding/spi/broker/` → `forwarding/spi/` (broker SPI references forwarding-domain SPI: `AgentBusEventType`/`ForwardingFailureCode`/`ForwardingRouteHandle`/`ForwardingOutboxRecord`). ✓ no inversion.
- `forwarding/runtime/transport/broker/rocketmq/` → `forwarding/spi/broker/` (Ports it implements + the broker SPI value objects) + `forwarding/runtime/transport/broker/` (common: `BrokerClientProperties`/`OutboundMessage`/`MessageHeaders`/`ControlDescriptor`). ✓ impl → spi + common.
- `gateway.runtime/` → `forwarding/spi/` + `forwarding/spi/broker/` + `forwarding/common/` + `forwarding/runtime/transport/broker/` + `forwarding/runtime/persistence/jdbc/`. **↛ `forwarding/runtime/..`** (the impl) — ArchUnit-enforced (Q5, stricter than ADR-0162's `gateway↛eventbus`).
- The relay wiring (`forwarding/runtime/relay/` @Configuration) → `forwarding/spi/` + `forwarding/spi/broker/` + `forwarding/common/` + `forwarding/runtime/{transport.broker, persistence.jdbc}` (siblings).

## ArchUnit delta (Q5)

- REPLACE `gateway.runtime ↛ eventbus.runtime` (ADR-0162) with `gateway.runtime.. ↛ forwarding.runtime..` (ADR-0163) — STRICTER (gateway never depends on ANY forwarding runtime impl, not just event-bus wiring). Update the liveness guard (gateway.runtime + forwarding.runtime both non-empty).
- `AgentBusForwardingSpiPurityTest`: the broker SPI extraction + rocketmq subpackage — the rocketmq import rule (`forwarding.. outside transport.broker: no RocketMQ`) likely still covers `transport.broker.rocketmq` via the `..` wildcard (green without change); the `RocketMq*` residence rule updates to `transport.broker.rocketmq` (or `..` covers it). G5 verifies.

## Files touched (planned, for the G5 file-change table)

| Δ | Path | What |
|---|---|---|
| `++` move | `forwarding/common/AgentBusBrokerProperties.java` | ← top-level common/ (eliminated) |
| `++` move | `forwarding/spi/broker/{BrokerForwardingConsumerPort,BrokerForwardingRelayPort,BrokerInboundMessage,BrokerProduceOutcome,DeliveryFilter}.java` (5) | ← forwarding/runtime/transport/broker/ |
| `++` move | `forwarding/runtime/relay/{EventBusRelayConfiguration,EventBusRelaySchedulingConfig,RelayScheduler}.java` (3) | ← top-level eventbus.runtime/ (eliminated) |
| `++` move | `forwarding/runtime/transport/broker/rocketmq/{RocketMqBrokerForwardingConsumer,RocketMqBrokerForwardingRelay}.java` (2) | ← forwarding/runtime/transport/broker/ root |
| `~` | `gateway/runtime/GatewayRuntimeConfiguration.java` | import → `forwarding.common.AgentBusBrokerProperties` |
| `~` | `forwarding/runtime/transport/broker/{BrokerClientProperties,BrokerOutboundMessage,BrokerMessageHeaders,BrokerControlDescriptor}.java` (4, stay) | package decl unchanged; consumers gain imports |
| `++` | `architecture/AgentBusDependencyBoundaryTest.java` | replace `gateway↛eventbus` rule with `gateway.runtime.. ↛ forwarding.runtime..` + update liveness guard |
| `~` | `architecture/AgentBusForwardingSpiPurityTest.java` | rocketmq residence rule → `transport.broker.rocketmq` (if `..` doesn't cover) |
| ripple | imports across moved files + consumers | mechanical (Q3/Q2 consequence) |

Net: 11 source files moved (5 broker SPI + 3 event-bus wiring + 2 rocketmq impl + 1 AgentBusBrokerProperties→forwarding/common) + 1 gateway edit + 1–2 arch-test edits + import ripple. Zero behaviour/profile/bean-name change; build (Q1=b) unchanged.

## What is NOT in the delta (stated, to prevent scope creep)

- No Maven multi-module split (Q1=b — deferred follow-on; the package reorg prepares for it).
- No new relay/scheduler logic, no bean-graph change, no Spring-profile change, no deployment/compose change.
- `registry.runtime/` + top-level `bus/spi/` (engine/ingress/registry/s2c) untouched.
- ADR-0162 not amended (Q5=a — new ADR-0163 supersedes 0162's layering + replaces the rule).
