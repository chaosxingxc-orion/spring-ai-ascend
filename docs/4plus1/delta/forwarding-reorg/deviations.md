# deviations — forwarding-reorg (G5 implement)

Recorded during G5 (code-driven) of the arch-driven `forwarding-reorg` package reorg
(ADR-0163 supersedes ADR-0162). The 11 source-file MOVES were done via `git mv`
before G5; G5 fixed package decls + imports + ArchUnit rules + the test moves the
build forced, then iterated to `BUILD SUCCESS`. One bullet per deviation with the
reason. Net: 443 tests / 14 skipped / 0 fail (unchanged count — the ArchUnit test
count changes by 0 net: 2 `AgentBusDependencyBoundaryTest` tests replaced by 2;
no test added/removed elsewhere).

## (a) tests that had to MOVE (vs import-only) + why (package-private access)

- **`EventBusRelaySchedulerTest`** — moved
  `test/.../eventbus/runtime/EventBusRelaySchedulerTest.java` →
  `test/.../forwarding/runtime/relay/EventBusRelaySchedulerTest.java` via `git mv`.
  *Why:* it mirrors `RelayScheduler`, which moved main-side from the eliminated
  `eventbus.runtime` plane into `forwarding.runtime.relay` (decision-tree Q2a). The
  test's package decl had to follow the moved main class (it constructs `RelayScheduler`
  directly + references `RelayTick`/`EventBusRelayWorker.RelayTickResult`, which are
  same-package post-move). Package decl → `forwarding.runtime.relay`; removed the
  now-same-package imports of `EventBusRelayWorker`/`RelayTick`; `AgentBusBrokerProperties`
  import `common` → `forwarding.common`.

- **`RocketMqBrokerForwardingConsumerTest`** — moved
  `test/.../forwarding/runtime/transport/broker/` → `.../broker/rocketmq/`.
  *Why:* it calls `RocketMqBrokerForwardingConsumer.sql92Expression(filter)` — a
  package-private static method. The impl class moved to `broker.rocketmq`
  (decision-tree Q3), so the test must move to `broker.rocketmq` to keep
  same-package access to the package-private seam. Added `DeliveryFilter` import
  (`spi.broker`); the impl class is now same-package (no import).

- **`RocketMqBrokerForwardingConsumerLifecycleTest`** — moved to
  `.../broker/rocketmq/`. *Why:* it calls `RocketMqBrokerForwardingConsumer.sql92Expression`
  (package-private static) + uses the `MessagePollerFactory`/`MessagePoller` nested
  seams. Same package-private-access reason as above. Added `BrokerInboundMessage` +
  `DeliveryFilter` imports (`spi.broker`).

- **`RocketMqBrokerForwardingRelayTest`** — moved to `.../broker/rocketmq/`. *Why:*
  it calls `RocketMqBrokerForwardingRelay.buildMessage(record, topic)` — a
  package-private static method. The impl class moved to `broker.rocketmq`, so the
  test must move to keep same-package access. Added `BrokerProduceOutcome` import
  (`spi.broker`).

- **`RealBrokerTwoHopRelayIntegrationTest`** — moved to `.../broker/rocketmq/`.
  *Why:* it calls `RocketMqBrokerForwardingConsumer.drainAll(...)` — a package-private
  instance method (used to clear run-unique-group residue on the shared broker topics
  before the real two-hop round-trip). The impl moved to `broker.rocketmq`, so the IT
  must move to keep same-package access. (Stays env-skipped — `@EnabledIfEnvironmentVariable`
  on the real broker; 4 of its 4 tests skipped without `ROCKETMQ_NAMESERVER`.) Added
  `BrokerControlDescriptor` import (parent `broker/`, stays) + `BrokerInboundMessage` +
  `DeliveryFilter` imports (`spi.broker`).

  *Tests that stayed import-only (no move):* `BrokerForwardingPortsContractTest`,
  `InMemoryBroker`, `DeliveryFilterTest` (test doubles / contract tests on the broker
  SPI, which is public — no package-private access to the moved rocketmq impl), and
  `RealBrokerProduceSideIntegrationTest` / `RealBrokerResponseSideIntegrationTest`
  (use only `public` constructors + `defaultPollerFactory`/`defaultSender` static
  factories — no package-private seams). All got `forwarding.spi.broker.*` (+ where
  applicable `broker.rocketmq.*`) imports added.

## (b) load-bearing assumptions that needed correction

- **The spec's ArchUnit delta understated the reorg's purity-rule ripple.** The
  decision-tree Q5 + to-be module-delta named only 2 ArchUnit tests to evolve
  (`AgentBusDependencyBoundaryTest` + `AgentBusForwardingSpiPurityTest`). In practice
  the reorg forced evolution of 4 ArchUnit test classes, because moving
  `EventBusRelayConfiguration`/`EventBusRelaySchedulingConfig`/`RelayScheduler` from
  the eliminated `eventbus.runtime` plane (which lived OUTSIDE `forwarding..` and so
  was exempt from the `forwarding..` purity rules) INTO `forwarding.runtime.relay`
  (now INSIDE `forwarding..`) brought legitimately-Spring-using + RocketMQ-using +
  `javax.sql`-using wiring under those rules; and moving `AgentBusBrokerProperties`
  from the eliminated `common` plane into `forwarding.common` brought the
  `@ConfigurationProperties` record under the Spring-purity rule; and extracting the
  broker SPI to `forwarding.spi.broker` brought broker-SPI javadoc (which names
  `org.apache.rocketmq` to state callers never touch it) under the broker-client
  text-scan. The fixes (all mirroring the existing `persistence.jdbc`/`transport.a2a`/
  `transport.broker` exemption pattern):
  - `AgentBusForwardingSpiPurityTest`: exempted `forwarding.runtime.relay` from the
    Spring / `javax.sql` / RocketMQ rules, and `forwarding.common` from the Spring
    rule (the two reorg fold-in homes). The broker-SPI classes in
    `forwarding.spi.broker` are pure Java (only `forwarding.spi.*` imports) so they
    satisfy the rules unaided.
  - `AgentBusForwardingRuntimeContractTest` (`readForwardingProductionSources` text
    scan): added `forwarding.runtime.relay` + `forwarding.spi.broker` to the path
    exclusions (the former wires the relay `DefaultMQProducer` bean; the latter's
    javadoc names `org.apache.rocketmq` only to disclaim it).
  - `AgentBusForwardingDesignContractTest.stage4_adds_no_broker_runtime_package`:
    added `forwarding.spi.broker` to the sanctioned-broker-home allowlist (the reorg's
    broker-SPI extraction is a sanctioned broker home, pure Java, not a broker runtime).

- **The spec's `gateway.runtime.. ↛ forwarding.runtime..` rule is unsatisfiable as
  literally written.** Q5 + the to-be module-delta state the rule becomes
  `gateway.runtime.. should not dependOn forwarding.runtime..`, with the assertion
  "gateway.runtime imports forwarding.spi + forwarding.common, NOT forwarding.runtime."
  That assertion is factually false: `GatewayRuntimeConfiguration` imports
  `forwarding.runtime.persistence.jdbc.JdbcForwardingOutbox`,
  `forwarding.runtime.transport.BrokerTopicResolver`,
  `forwarding.runtime.transport.broker.BrokerClientProperties`,
  `forwarding.runtime.transport.broker.rocketmq.RocketMqBrokerForwardingConsumer`/
  `Relay`, and `GatewayRuntimeService` imports
  `forwarding.runtime.transport.broker.BrokerControlDescriptor` — i.e. the gateway
  WIRES several `forwarding.runtime..` subpackages (broker-common, the rocketmq
  adapters, the JDBC outbox, the transport resolver). The to-be module-delta's own
  dependency-direction line confirms this (it lists the gateway as depending on
  `forwarding/runtime/transport/broker/` + `forwarding/runtime/persistence/jdbc/`),
  directly contradicting its `↛ forwarding/runtime/..` parenthetical. Per the spec's
  "STOP + note it in deviations rather than improvising scope" instruction, the rule
  was written as the strictest GREEN generalisation of ADR-0162's
  `gateway↛eventbus.runtime` rule: **`gateway.runtime.. ↛ forwarding.runtime.relay..`**
  — the event-bus two-hop relay mechanism / wiring's post-reorg home (where
  `EventBusRelayConfiguration`/`EventBusRelaySchedulingConfig`/`RelayScheduler`/
  `EventBusRelayWorker`/`RelayTick`/`RelayDispatchLoop` now live). This is green
  (gateway imports nothing from `forwarding.runtime.relay`), non-vacuous (the relay
  package ships 6 classes), and is the honest evolution of the prior rule (the
  event-bus wiring moved from the eliminated `eventbus.runtime` plane into
  `forwarding.runtime.relay`). The liveness guard was updated to assert
  `gateway.runtime` + `forwarding.runtime.relay` are both non-empty. A future,
  stricter rule (gateway↛ the broker adapters / persistence / transport, with the
  broker-common config exempted) would require either moving `BrokerClientProperties`
  out of `forwarding.runtime.transport.broker` or a more elaborate exemption; that
  is out of scope for this reorg and flagged here for ADR-0163 / G7.

- **`RocketMqBrokerForwardingConsumer`/`Relay` do NOT reference the broker-common
  types.** The spec said the rocketmq impls "possibly" reference
  `BrokerClientProperties`/`BrokerOutboundMessage`/`BrokerMessageHeaders`/`BrokerControlDescriptor`
  (parent `broker/`) — "CHECK the file body". Code-verified: they do NOT. The consumer
  uses only `BrokerForwardingConsumerPort`/`DeliveryFilter`/`BrokerInboundMessage`
  (SPI, now `spi.broker`); the relay uses only `BrokerForwardingRelayPort`/
  `BrokerProduceOutcome` (SPI, now `spi.broker`). So the added imports for the moved
  impls are SPI-only — no parent-`broker/` imports were needed for them.

## (c) final test count

- **`Tests run: 443, Failures: 0, Errors: 0, Skipped: 14` — `BUILD SUCCESS`.**
  Matches the expected ~443 / 14-skipped baseline (the ArchUnit test count changes by
  0 net: 2 `AgentBusDependencyBoundaryTest` tests replaced by 2; no test added/removed
  elsewhere). The 14 skipped = the 3 `RealBroker*` integration tests (2 + 8 + 4),
  env-guarded by `@EnabledIfEnvironmentVariable` on the real RocketMQ broker
  (`ROCKETMQ_NAMESERVER` unset).
