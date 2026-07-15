# Decision tree — event-bus two-hop relay gap

G3 of arch-driven (code-driven). Only **genuinely open** architectural questions are branched; where L2 already decides, it's stated as a constraint, not a branch. Each open Q records branches / chosen / why / rejected-why. These feed G4 to-be; the to-be sign-off confirms (or amends) the chosen branches.

**Authority:** L2 `feat-013-client-invocation-event-forwarding.md` (§1.1 topology, §1.2 governance relay, §3.1 two-form, §4.1 two-hop sequence, §5.1 deployment, §8.1/§8.3 net-new) + `feat-014-...` + 4 L2 constraints (memory `feat-013-014-broker-decisions`): gateway separate process / event-bus+registry co-located / RocketMQ pub/sub both hops / no a2a push.

## G3.5 — UI prototype gate: SKIPPED (state out loud)
This change is **pure backend / runtime / deployment topology** — zero frontend template delta (no `.component.html`/`.vue`/`.tsx`, no UI surface added/removed/modified). G3.5 does not trigger. Go straight to G4. (L9 anti-pattern: do not skip G3.5 for UI-touching changes — this one is not UI-touching.)

## Q1 — Two-hop relay loop: how to assemble the consume→govern→re-produce relay?

**Q (user/handoff wording):** 「组装两跳中继回路（consume hop1→inbox→outbox→dispatcher→produce hop2）+ 对称响应路径，复用现有积木」—— how is the consume→govern→re-produce relay realized, given the existing worker is producer-shape (claim own outbox → `ForwardingDeliveryPort.deliver`)?

**Branches:**
- **(a) New `EventBusRelayWorker`/`RelayLoop` built FROM existing ports** — a consume→govern→re-produce component: `BrokerForwardingConsumerPort.poll` hop1 req → `ForwardingInboxPort.receive` dedup + tenant/correlation/audit → `ForwardingOutboxPort.enqueue` → `BrokerForwardingRelayPort.produce` hop2 deliver; symmetric relay for the response leg (consume `resp_in` → govern → produce `resp_out`).
- (b) Extend the existing `ForwardingDispatcherWorker`/`DispatchLoop` by grafting a consume→govern front-end onto its producer path.
- (c) No new class — a Spring `@Bean`/`@Scheduled` method that orchestrates the ports inline in a config.

**Chosen: (a).** *Why:* the existing worker has a single, documented responsibility (claim own outbox → deliver via `ForwardingDeliveryPort`); it is the wrong shape for a relay (it does not consume from a broker nor re-publish). Grafting a consume front-end onto it (b) muddies its lease/ack/retry contract and reuses none of its claim-outbox logic. (c) puts orchestration in a config class (untestable as a unit, no lease/idle seams). (a) reuses *all* the substrate ports (inbox/outbox/relay/consumer + state machine/retry/breaker) — "复用现有积木" — while giving the relay its own lease/idle seams like `ForwardingDispatchLoop` (TickSource/IdleStrategy), so it is deterministically unit-testable the same way the worker is.

**Rejected-why:** (b) violates single-responsibility of the worker + reuses little of it; (c) untestable orchestration with no idle/tick seams. Note (L1 lesson applied): the existing worker/loop were grepped for consumers — they ARE wired, but only in *tests*; their shape (producer) is the finding that rules out (b), not "unused."

## Q2 — Process split mechanism: L2 leaves it open — how to get two launchable forms (gateway / event-bus+registry) from the one agent-bus codebase?

**Q:** L2 §3.1 mandates the *outcome* ("gateway 进程与 event-bus+registry 进程分别打包启动") but does **not** pin the mechanism. Today there is one `AgentBusApplication` booting only the registry plane.

**Branches:**
- **(a) Spring profiles on the single `AgentBusApplication`** — `spring.profiles.active=gateway` vs `eventbus`, with `@Profile`-gated `@Configuration` wiring each form's beans (gateway form wires `GatewayRuntimeService` + gateway-side relay/consumer + controller; eventbus form wires the relay worker + inbox/outbox + registry).
- (b) Two separate main classes (`GatewayApplication`, `EventBusApplication`) each `@SpringBootApplication` with its own scan/imports.
- (c) Single main + a property flag (non-`@Profile`) selecting beans.

**Chosen: (a).** *Why:* minimal diff over the existing single main ADR-0160 promoted; profiles are the idiomatic Spring way to derive two launch forms from one codebase without duplicating bootstrap; deployment selects form via env var (`SPRING_PROFILES_ACTIVE`), which composes cleanly with docker-compose services. (b) is defensible (cleaner scan boundaries) but doubles the bootstrap + main-class maintenance for no current payoff (the two forms share the agent-bus codebase and most substrate). (c) reinvents `@Profile` poorly.

**Rejected-why:** (b) premature separation — two mains for one shared codebase; (c) non-idiomatic, re-implements `@Profile`.

## Q3 — Deployment artifacts: L2 does not pin the format — what to produce?

**Q:** as-is has **no** `spring-boot-maven-plugin <repackage>` anywhere (root pom only `pluginManagement` config; agent-bus has no `<build>`; agent-runtime explicit "plain library"); `AgentBusApplication`'s `java -jar` claim is unfulfillable; docker-compose runs RocketMQ-only; root Dockerfile builds agent-runtime as a `CMD java -version` library-carrier.

**Branches:**
- **(a) Full: spring-boot `<repackage>` on agent-bus (fat-jar) + gateway/event-bus services in docker-compose.**
- (b) Fat-jar only; defer container images / compose services.
- (c) Defer all artifacts; run via `spring-boot:run` only.

**Chosen: (a)**, scoped to **fat-jar repackage + docker-compose gateway & event-bus services**. *Why:* the repackage is a prerequisite for *any* `java -jar` launch (the two-process split + two-hop IT both need launchable artifacts); docker-compose already provisions the broker + 8 topics but no app containers, so adding gateway/event-bus services completes the local two-process topology the two-hop IT (Q4) needs. Container *images* (Dockerfile per form) are kept optional/deferred — the root Dockerfile's library-carrier pattern isn't the right shape and building per-form images is a separable, lower-priority step; the compose services can build/run from the fat-jar for now.

**Rejected-why:** (b) leaves the local two-process topology unlaunchable (no compose services → can't run the two-hop IT end-to-end); (c) leaves `java -jar` unfulfillable and the gap's "部署制品缺" item unresolved.

## Q4 — Two-hop IT strategy: how to verify relay governance, given external agent-runtime-java is parallel (not time-locked)?

**Q:** governance-between-hops has zero coverage (the slice-3 IT is single-hop, relay bypassed). The external `agent-runtime-java` hop2 consumer/producer is parallel, not time-locked (cross-repo boundary only).

**Branches:**
- **(a) In-repo two-hop IT with `TestAgentRuntime` test-doubles on both ends**, env-guarded (`ROCKETMQ_NAMESERVER`) like slice-3: gateway form → broker `T_invocation_req` → event-bus form (relay governance: inbox dedup/tenant/corr/audit) → broker `T_invocation_deliver` → `TestAgentRuntime` (test-double consumer) → response path symmetric. Asserts governance acts fire (dedup on replay, cross-tenant filtered, correlation mismatch skipped, audit recorded).
- (b) Defer two-hop IT until external agent-runtime-java hop2 is ready (full cross-repo E2E).
- (c) Unit/contract-level only (no cross-process IT).

**Chosen: (a).** *Why:* matches the REQ-2026-001 release-bar decision (release bar = InMemoryBroker CI hard gate + real RocketMQ env-guarded + **TestAgentRuntime E2E in-repo test-double single-JVM**; external runtime parallel, not time-locked — see memory `feat-013-014-broker-decisions` §step-1 ④⑥). (a) directly closes the zero-coverage gap (the L2-core governance semantics) without blocking on the external repo. (b) would leave the L2-core governance indefinitely unverified (the "别无限 defer" concern). (c) can't prove the cross-process two-hop relay actually relays + governs.

**Rejected-why:** (b) defers L2-core verification onto an unbounded external dependency; (c) can't exercise the cross-process relay path (the thing being built).

## Non-open constraints (L2 decides — not branched, recorded for G4)
- **Governance location = the event-bus+registry process, between hops** (feat-013 §1.2/§4.1): inbox dedup (`ON CONFLICT DO NOTHING` + `SKIP LOCKED` claim) + tenant check (envelope constructor + broker `poll` tenant scope) + correlation match + audit — applied by the new relay (Q1a) using the existing `ForwardingInboxPort`/`ForwardingOutboxPort` substrate. NOT byte-transparent.
- **Both hops via RocketMQ pub/sub; no a2a push** (constraint #2/#4): `A2aForwardingDeliveryPort` (T1 HTTP) replaced for FEAT-013/014 scope; T1 PoC retained for coexistence/灰度, switch deferred Stage 30.
- **8 topics** provisioned (FEAT-013 `ascend_bus_invocation_{req,deliver,resp_in,resp_out}` + FEAT-014 `ascend_bus_a2a_*`); the deliver/resp_in/resp_out topics exist but are unused in as-is — to-be wires them.
- **Agent-runtime hop2 consumer/producer = external repo**, parallel, not time-locked (cross-repo boundary only; in-repo `TestAgentRuntime` doubles cover the IT).

## Load-bearing assumptions to verify before G5 implement (L3)
1. **`AgentBusApplication` boots only registry beans today** (grep-verified: only registry `@Component`/`@Service`/`@Configuration`/`@RestController`/`@Bean` in main; `new GatewayRuntimeService`/`DispatcherWorker`/`DispatchLoop`/`A2aForwardingDeliveryPort` only in tests). → to-be must ADD `@Profile`-gated beans wiring the gateway + relay forms. (Already verified G2; re-confirm at G5 that no new forwarding beans slipped in.)
2. **No `spring-boot-maven-plugin <repackage>` exists in any module** (agent-verified: root pom `pluginManagement` config only; agent-bus no `<build>`; agent-runtime explicit plain-library). → to-be adds the repackage execution to `agent-bus/pom.xml`.
3. **The relay needs a consume→govern→re-produce shape, which no existing class provides** (grep-verified: `BrokerForwardingConsumerPort` used only by gateway response polling + `TestAgentRuntime` + tests; no hop1-request consumer/re-publisher). → to-be net-new `EventBusRelayWorker` (Q1a).
