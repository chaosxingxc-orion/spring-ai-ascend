# Decision tree — forwarding-reorg

G3 of arch-driven (code-driven). Only genuinely open architectural questions are branched. The user gave a concrete target structure; the open questions are the clarifications + either/ors it leaves. Each records branches / chosen / rejected-why. **G4 sign-off confirms or amends before any code (no sign-off = no code).**

**Authority:** code-verified as-is (`as-is/scope.md` + `as-is/module.md`); broker class reads (`BrokerForwardingConsumerPort`/`RelayPort` = `public interface`; `BrokerInboundMessage`/`BrokerOutboundMessage`/`BrokerProduceOutcome`/`DeliveryFilter`/`BrokerClientProperties` = `public record`); `agent-bus/pom.xml` (single module + `spring-boot-maven-plugin repackage` + `@Profile`); ADR-0162 (the just-closed layering this supersedes); the broker-filtering decision packet (§3 D3/D4/D5/D8).

## G3.5 — UI prototype gate: SKIPPED (stated out loud)

Pure backend / package+build reorg — zero frontend template delta. G3.5 does not trigger; straight to G4.

---

## Q1 — Build-module strategy: Maven multi-module, or keep single-module + @Profile?

**Q (user wording):** 「需要考虑能构建出不同的包，包括event-bus的进程包，给生成者和消费者使用的SDK，gateway的进程包」.

**Branches:**
- **(a) Maven multi-module** — split `agent-bus` into sub-modules: `agent-bus-forwarding-sdk` (forwarding/spi + forwarding/common + forwarding/runtime/{relay, persistence, transport/{a2a, broker common}}) + `agent-bus-gateway` (gateway/runtime @Configuration + the gateway fat-jar) + `agent-bus-eventbus` (event-bus wiring @Configuration + the event-bus fat-jar), under a parent `agent-bus-parent`. Produces 3 actual artifacts (SDK jar + 2 fat-jars).
- (b) Keep single-module + @Profile — reorganize PACKAGES only (no Maven split); the 3 "packages" stay as logical package-sets within one module; process forms still selected by `SPRING_PROFILES_ACTIVE` (the current G5-B mechanism). The "SDK" is a documented package boundary, not a separate artifact.

**Chosen: (b) — single-module + @Profile, reorganize packages only.** *Why:* the user's PRIMARY ask is a cleaner DIRECTORY structure that CAN build different packages; the pom already builds a fat-jar + uses @Profile for the 2 process forms (working, G5-B). A Maven multi-module split (a) is a large build-system change (parent pom + 3 sub-modules + dependency wiring + the spring-boot repackage per process module + test-dep reshuffling + the agent-runtime test-dep relocation + docker-compose image refs) with real blast radius — and it's NOT required to "build different packages": the @Profile approach ALREADY yields distinct process forms (gateway vs event-bus) from one fat-jar, and the package reorg makes the SDK/process boundaries explicit (so a future multi-module split is a clean follow-on, not a prerequisite). (b) honors "surgical changes" + "keep it simple" — reorganize packages to make the SDK/process boundary clear, defer the Maven split. If the user wants actual separate artifacts NOW, pick (a) and G5 expands to a multi-module build (flagged: bigger change, build-system ripple).

**Rejected-why:** (a) is a build-system change beyond the directory reorg the user framed; the @Profile mechanism already delivers distinct process forms; the package reorg prepares for (a) without committing to its build ripple now.

---

## Q2 — Where do the 3 event-bus wiring files go once "merged into forwarding"?

**Q:** 「将event-bus目录下代码合并过来」 — `EventBusRelayConfiguration`/`EventBusRelaySchedulingConfig`/`RelayScheduler` (currently top-level `eventbus.runtime/`) fold into `forwarding/`. Where?

**Branches:**
- **(a) `forwarding/runtime/relay/`** — alongside `EventBusRelayWorker`/`RelayTick`/`RelayDispatchLoop` (the relay mechanism the wiring drives).
- (b) a new `forwarding/runtime/eventbus/` subpackage — group the event-bus wiring separately from the relay mechanism.
- (c) a new `forwarding/eventbus/` (level-2, sibling to spi/runtime/common) — event-bus wiring as its own sub-package of forwarding, not under runtime.

**Chosen: (a) `forwarding/runtime/relay/`.** *Why:* the 3 files WIRE the relay mechanism (`EventBusRelayWorker` + the `RelayTick` seams + the `RelayDispatchLoop` driver) — they are relay-runtime wiring, so `runtime/relay/` is their natural home (co-located with what they wire, mirroring how `GatewayRuntimeConfiguration` lives in `gateway/runtime/`). (b) separates wiring from mechanism unnecessarily (the wiring IS relay-runtime). (c) creates a `forwarding/eventbus/` that duplicates the `relay/` concept (the event-bus IS the relay here). (a) is the least-surprise, co-located home. `@Profile("eventbus")` + bean names stay; only the package changes. (Note: the `gateway↛eventbus` ArchUnit rule from ADR-0162 evolves — see Q5 — since "eventbus" is no longer a top-level package.)

**Rejected-why:** (b)/(c) over-partition the relay wiring from the relay mechanism it drives.

---

## Q3 — Broker class split: which → forwarding/spi, which stay broker/ common, which → broker/rocketmq?

**Q (user wording):** 「broker...对外提供SPI接口要提到spi目录下，pub/sub通用的代码放到此目录下 ... rocketmq独有的代码放到rocketmq五级目录」.

**Code-verified broker/ (11 classes):** 2 Port interfaces (`BrokerForwardingConsumerPort`/`BrokerForwardingRelayPort`), 7 records (`BrokerClientProperties`/`BrokerInboundMessage`/`BrokerOutboundMessage`/`BrokerProduceOutcome`/`BrokerMessageHeaders`/`BrokerControlDescriptor`/`DeliveryFilter`), 2 rocketmq impl (`RocketMqBrokerForwardingConsumer`/`Relay`).

**Dependency-direction constraint (load-bearing):** the Port interfaces' method signatures reference `BrokerInboundMessage` (poll/commit/reject), `BrokerProduceOutcome` (produce return), `DeliveryFilter` (subscribe param). Code-verified: these 3 records reference ONLY `forwarding.spi` types (`AgentBusEventType`/`ForwardingFailureCode`) — NO broker-internal types. So they are part of the broker SPI surface (callers of the Ports see them) and must move WITH the Ports (else SPI→runtime inversion). `BrokerOutboundMessage` references `BrokerMessageHeaders` — neither is in a Port signature (impl-internal) → stay broker/. `BrokerClientProperties` (config record) + `BrokerControlDescriptor` (decoded control body) are not in Port signatures → stay broker/ common.

**Chosen:**
- **broker SPI (5)** → **`forwarding/spi/broker/`** (NEW subpackage): `BrokerForwardingConsumerPort`, `BrokerForwardingRelayPort`, `BrokerInboundMessage`, `BrokerProduceOutcome`, `DeliveryFilter`.
- **broker common (4)** → **stay `forwarding/runtime/transport/broker/`**: `BrokerClientProperties`, `BrokerOutboundMessage`, `BrokerMessageHeaders`, `BrokerControlDescriptor`.
- **rocketmq impl (2)** → **`forwarding/runtime/transport/broker/rocketmq/`** (NEW): `RocketMqBrokerForwardingConsumer`, `RocketMqBrokerForwardingRelay`.

*Why the `spi/broker/` subpackage (not flat into `forwarding/spi/`):* the 5 broker SPI classes are a coherent "broker SPI" group, distinct from the ~18 forwarding-domain SPI classes already in `forwarding/spi/`; a subpackage keeps them grouped + readable, and matches the user's "提到spi目录下" (into the spi directory tree) without dumping 5 broker classes flat among the forwarding ports. (Alternative: flat into `forwarding/spi/` — fewer packages but a 23-class flat list; less grouping. Flag at sign-off if you prefer flat.)

**Rejected-why:** flat-into-spi (alternative) loses the broker-SPI grouping; keeping the Ports in broker/ (no extraction) violates the user's "SPI↑to spi" directive + leaves the broker SPI buried in the impl package.

**Note:** `BrokerControlDescriptor` is the decoded control-descriptor body (used by the relay worker + gateway, NOT by the broker Ports); classified broker-common for now, but G5 may refine its home (it might belong with the relay mechanism, not broker) — flagged, not blocking.

---

## Q4 — SDK boundary: what's in the producer/consumer SDK?

**Q:** 「给生成者和消费者使用的SDK」 — what does the SDK contain? (Depends on Q1.)

**Q1=b chosen → the "SDK" is a logical package boundary (not a separate artifact):**

**Chosen:** the SDK surface = `forwarding/spi/` (forwarding-domain SPI) + `forwarding/spi/broker/` (broker SPI) + `forwarding/common/` (`AgentBusBrokerProperties`) + `forwarding/runtime/transport/broker/` common (`BrokerClientProperties` etc.) + `forwarding/runtime/transport/a2a/` (the A2A delivery port) + `forwarding/runtime/relay/` (`EventBusRelayWorker` etc. — the reusable relay mechanism) + `forwarding/runtime/persistence/jdbc/` (the JDBC inbox/outbox adapters — if producers/consumers need durable inbox/outbox). The PROCESS packages (`gateway/runtime/` @Configuration, the event-bus wiring @Configuration in `forwarding/runtime/relay/`) ADD their @Configuration + Spring boot on top of the SDK.

*Why:* producers (the gateway) + consumers (agent-runtime, via the SDK) need the SPI (ports/envelopes) + the broker SPI (to produce/consume) + the common config + the relay mechanism + the durable adapters. They do NOT need the gateway/event-bus @Configuration wiring (process-specific). This matches the existing dependency direction (gateway → forwarding.spi + common + broker; the reorg makes the boundary explicit). Under Q1=b, this is a documented package boundary; a future Q1=a multi-module split would make `forwarding/{spi, common, runtime/{relay, transport, persistence}}` the `agent-bus-forwarding-sdk` module.

**Rejected-why:** a thinner SDK (spi only) omits the broker SPI + common config producers/consumers actually need; a fatter SDK (incl. the process @Configuration) defeats the SDK/process split. The chosen boundary is the natural "what producers/consumers depend on" cut.

---

## Q5 — How does this supersede ADR-0162 + the gateway↛eventbus ArchUnit rule?

**Q:** the just-closed `agent-bus-layering` (ADR-0162) established top-level `eventbus.runtime` + `common` + the `gateway.runtime ↛ eventbus.runtime` rule. This reorg folds event-bus into `forwarding` + moves `common`→`forwarding/common`. How to handle 0162 + the rule?

**Branches:**
- **(a) New ADR (0163) superseding 0162's layering + REPLACING the rule** — record: the forwarding-centric layering (event-bus wiring in `forwarding/runtime/relay/`, common in `forwarding/common/`, broker SPI in `forwarding/spi/broker/`); ADR-0162's top-level-plane split is superseded by the forwarding-internal structure; the `gateway↛eventbus` rule is REPLACED by `gateway.runtime ↛ forwarding.runtime..` (gateway depends on forwarding SPI + common, never on the forwarding runtime impl — relay/persistence/transport-broker) — STRICTER + more general (gateway never reaches into ANY forwarding runtime impl, not just event-bus wiring).
- (b) Amend ADR-0162.
- (c) Keep both (0162 unchanged + 0163 additive).

**Chosen: (a) new ADR-0163, superseding 0162's layering + replacing the rule.** *Why:* ADR-0162's premise (top-level `eventbus.runtime`/`common` planes) no longer holds post-reorg (folded into `forwarding`); amending 0162 (b) would conflate two different layering models in one ADR. A new ADR-0163 records the forwarding-centric layering + the evolved (stricter) boundary rule, and marks 0162's plane-split as superseded (0162's OTHER content — the agent-bus-layering refactor's mechanics — stays as historical record). The rule evolution (`gateway↛eventbus` → `gateway↛forwarding.runtime`) is a STRENGTHENING, captured in 0163 + the updated ArchUnit test.

**Rejected-why:** (b) conflates; (c) leaves two conflicting layering ADRs.

---

## ArchUnit evolution (the reorg's ArchUnit delta)

- `AgentBusForwardingSpiPurityTest`: the broker SPI extraction (Ports + 3 value objects → `forwarding/spi/broker/`) means `spi/broker/` is now SPI (pure Java). The rocketmq impl is now in `transport.broker.rocketmq` (subpackage of `transport.broker`); the import rule `forwarding.. outside transport.broker: no RocketMQ` likely still covers `transport.broker.rocketmq` via the `..` wildcard (green without change); the `RocketMq* must reside in transport.broker` residence rule updates to `transport.broker.rocketmq` (or the `..` covers it). G5 verifies the exact wording.
- `AgentBusDependencyBoundaryTest`: the `gateway.runtime ↛ eventbus.runtime` rule (ADR-0162) is REPLACED by `gateway.runtime.. ↛ forwarding.runtime..` (Q5a) — stricter. The liveness guard updates (gateway.runtime + forwarding.runtime both non-empty).

## Load-bearing assumptions to verify before G5 implement (L3)

1. The 5 broker SPI classes (2 Ports + `BrokerInboundMessage`/`BrokerProduceOutcome`/`DeliveryFilter`) reference NO broker-internal types (code-verified: only `forwarding.spi` types). → moving them to `forwarding/spi/broker/` breaks no compile + inverts nothing.
2. The 2 rocketmq impls reference the broker SPI (Ports they implement) + broker common (`BrokerClientProperties`/`BrokerOutboundMessage`/`BrokerMessageHeaders`/`BrokerControlDescriptor`). → moving to `broker/rocketmq/` adds imports for parent-broker common + `spi/broker`; the `..` ArchUnit rule covers the subpackage.
3. `EventBusRelayConfiguration`/`EventBusRelaySchedulingConfig`/`RelayScheduler` → `forwarding/runtime/relay/`: they reference `forwarding.spi` + `forwarding.runtime.transport.broker` + `forwarding.runtime.persistence.jdbc` + `forwarding.common` (AgentBusBrokerProperties). Moving to `relay/` (sibling to `EventBusRelayWorker`) — import set unchanged (they already import forwarding.* + common).
4. `forwarding/spi` "目录结构不变" — the existing ~18 forwarding SPI classes stay; the broker SPI is ADDED as `spi/broker/` (a new subpackage, not a reorg of existing).
5. Single-module build (Q1=b): no pom split → the `spring-boot-maven-plugin repackage` + `@Profile` mechanism is unchanged; the fat-jar still builds the 2 process forms. The package reorg is compile-only (no build ripple).
