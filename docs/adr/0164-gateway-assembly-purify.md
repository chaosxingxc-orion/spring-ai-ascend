---
level: L1
view: development
status: active
authority: "arch-driven G6/G7 (gateway-assembly-purify); G4 sign-off 2026-07-16 (Q5a=(b) amendment)"
---

# ADR-0164: gateway plane SPI-only purity + forwarding adapter @Configuration self-assembly (delivers the ADR-0163 follow-on)

- **Status:** Accepted
- **Date:** 2026-07-16
- **Change slug:** `gateway-assembly-purify`
- **Delivers:** the ADR-0163 flagged follow-on (Decision 5 + Consequences: *"a future stricter rule (gateway↛forwarding.runtime via a forwarding-runtime auto-config that provides the broker/jdbc beans, so the gateway depends only on forwarding.spi + forwarding.common) is flagged as a follow-on"*).
- **Supersedes (the drift note):** ADR-0163's accepted-drift scope `gateway.runtime.. ↛ forwarding.runtime.relay..` + the `deviations.md` (b) note that flagged this as future work.
- **Related:** `docs/4plus1/delta/gateway-assembly-purify/`; ADR-0163 (the forwarding-reorg); ADR-0162 (superseded by 0163).

## Context

ADR-0163 (the just-closed forwarding-reorg) accepted `gateway.runtime.. ↛ forwarding.runtime.relay..`
as an **accepted drift**: the to-be's literal `gateway.runtime.. ↛ forwarding.runtime..` was
infeasible because the gateway `@Configuration` wired concrete `forwarding.runtime.*`
adapters (broker-common, rocketmq, JDBC outbox, transport resolver) +
`BrokerControlDescriptor`. ADR-0163 flagged the stricter literal rule as a follow-on
(delivered here). The user found `GatewayRuntimeConfiguration`'s concrete-adapter
imports (group 1: `RocketMqBrokerForwardingConsumer`/`Relay` + `DefaultMQProducer`;
group 2: `JdbcForwardingOutbox` + `BrokerTopicResolver` + `BrokerClientProperties`)
inappropriate at the assembly layer + asked for the adapter `@Bean` assembly to move
into forwarding adapter packages, so the gateway `@Configuration` keeps only SPI port
injection.

## Decision

1. **The gateway plane depends on `forwarding.spi` + `forwarding.spi.broker` +
   `forwarding.common` + `bus.spi.ingress` ONLY — zero `forwarding.runtime`.** This is
   machine-checked by the strengthened ArchUnit rule (Decision 4). The gateway
   `@Configuration` injects the broker / JDBC adapter beans via their SPI ports rather
   than constructing them.

2. **The 5 concrete-adapter `@Bean` moved from `GatewayRuntimeConfiguration` into 2
   forwarding adapter `@Configuration`s, co-located with their adapters:**
   - `++ RocketMqGatewayBrokerConfiguration` (`@Profile("gateway")` in
     `forwarding.runtime.transport.broker.rocketmq`) — owns `brokerClientProperties` +
     `gatewayProducer` (`DefaultMQProducer`) + `gatewayRelay`
     (`RocketMqBrokerForwardingRelay`, `BrokerTopicResolver("req")`) +
     `gatewayResponseConsumer` (`RocketMqBrokerForwardingConsumer`,
     `BrokerTopicResolver("resp_out")`).
   - `++ JdbcForwardingGatewayConfiguration` (`@Profile("gateway")` in
     `forwarding.runtime.persistence.jdbc`) — owns `gatewayOutbox`
     (`JdbcForwardingOutbox` → `ForwardingOutboxPort` + `ForwardingOutboxClaimPort`).
   - `GatewayRuntimeConfiguration` keeps ONLY 2 SPI-only `@Bean`: `gatewayRuntimeService`
     (now injects the SPI ports by type/qualifier) + `gatewayResponseSubscription`
     (unchanged — SPI-only subscribe-at-startup). Bean names are PRESERVED — no behaviour
     / profile / qualifier change for the gateway process; the 5 beans simply moved home.

3. **`BrokerControlDescriptor` moved `forwarding.runtime.transport.broker` →
   `forwarding.spi.broker`.** L1/L3-verified pure-Java static codec (imports only
   `forwarding.spi.AgentBusEventType` + `java.util.Objects`); its javadoc already called
   it the SHARED codec used by `GatewayRuntimeService` + `TestAgentRuntime`, so
   `forwarding.spi.broker` is its natural home. This was the one residual
   `forwarding.runtime` import blocking the literal-full rule. Consumers (~8) updated.

4. **The plane boundary rule is strengthened to the literal full
   `gateway.runtime.. ↛ forwarding.runtime..`** (GREEN — machine-checked: the gateway
   imports zero `forwarding.runtime` classes). This supersedes ADR-0163's accepted-drift
   `gateway.runtime.. ↛ forwarding.runtime.relay..` + delivers the ADR-0163 "stricter
   rule via forwarding auto-config" follow-on. The liveness guard widens to
   `forwarding.runtime`.

5. **The Spring purity exemption is extended to `forwarding.runtime.transport.broker..`**
   for the new `RocketMqGatewayBrokerConfiguration` — mirroring how ADR-0163 extended it
   for `runtime.relay` (the broker @Configuration is `@Configuration`+`@Bean`+`@Profile`
   by nature). The rocketmq-import rule already covered `transport.broker..`; the
   javax.sql rule already covered `persistence.jdbc`; no change to those.

6. **Non-goals (locked by G3 sign-off):** NO `brokerType` property / 3-way switch
   (Q1=b — only RocketMQ exists; Kafka is purity-forbidden; no production InMemory; a
   switch with 1 real arm + 2 stubs is premature abstraction). NO refactor of the
   symmetric `EventBusRelayConfiguration` (Q4=a — it lives inside `forwarding`, is
   purity-exempt, and is not a plane violation; gateway-only is surgical). NO
   `@AutoConfiguration` (Q6=b — the repo uses zero; plain `@Configuration`+`@Profile` is
   the established convention for app-internal process-form wiring). NO Maven
   multi-module (ADR-0163 Q1 follow-on, still deferred).

## Consequences

- **New invariant (gateway plane SPI-only purity):** the gateway process form depends on
  `forwarding` ONLY via its SPI surface (`forwarding.spi` + `forwarding.spi.broker` incl.
  `BrokerControlDescriptor`) + `forwarding.common` config — never on `forwarding.runtime`
  (the relay mechanism, the broker adapters, the JDBC adapter, the transport resolvers).
  Machine-checked: `gateway.runtime.. ↛ forwarding.runtime..` is GREEN. The ADR-0163
  accepted drift is CLOSED.
- **Forwarding adapter @Configuration self-assembly:** the broker / JDBC adapter `@Bean`
  assembly lives co-located with its adapters in `forwarding.runtime.*`, gated by the
  SAME `@Profile` that selects the process form. The role→resolver mapping
  (`"req"`/`"resp_out"`/`"deliver"`/`"resp_in"`/`"resp_out"`) lives with the beans it
  constructs. The gateway (and, symmetrically, the relay) inject SPI ports.
- **BrokerControlDescriptor is SPI-surface** (`forwarding.spi.broker`) — it was already
  a shared cross-process-form codec; its home now matches its role.
- **No behaviour / bean-name / profile / build change** for the gateway process (bean
  names `gatewayProducer`/`gatewayRelay`/`gatewayResponseConsumer`/`gatewayOutbox`/
  `brokerClientProperties` preserved; the 5 beans moved home; the 2 SPI-only beans kept
  their shape, flipping construct→inject). `443 green / 14 skip` (unchanged from the
  ADR-0163 baseline).
- **Follow-ons (flagged, not blocking):** (a) a `brokerType` switch (RocketMQ/Kafka/
  InMemory) when a 2nd broker exists — the package structure is ready; (b) the symmetric
  relay config (`EventBusRelayConfiguration`) could mirror this for full symmetry — it is
  purity-exempt today so not a layering violation; (c) Maven multi-module (ADR-0163 Q1).
- **NOT a REQ-2026-001 release blocker** (pure wiring refactor + ArchUnit-rule
  strengthening; the gateway process behaves identically; release bar unaffected).

## Updated baseline

Code-driven (no `docs/4plus1/` L1 baseline — G1 found the L1 yaml corrupted, INV-002;
the element-model is therefore not updated by this change). The arch-driven delta
artifacts live at `docs/4plus1/delta/gateway-assembly-purify/` (G2 as-is · G3 decision
tree · G4 to-be + sign-off · G5 deviations · G6 as-built + diff · G7 CLOSED). Suite:
`./mvnw -f agent-bus/pom.xml test` → 443 green / 14 skip / 0 fail (BUILD SUCCESS).
Supersedes the ADR-0163 drift note (the accepted `gateway↛forwarding.runtime.relay`
scope is replaced by the literal full `gateway↛forwarding.runtime`); ADR-0163's other
content (the forwarding-centric reorg) stays as historical record.
