# Scope — agent-bus layering refactor (as-is)

**Change slug:** `agent-bus-layering`
**G2 mode:** code-driven (no `docs/4plus1/` baseline; drawn from real code under `agent-bus/src/main|test/java/com/huawei/ascend/bus/`). G1 = no 4+1 baseline (project is code-driven per ADR-0147/0150/0160); route G2 on what's present in the code.

## What this change is

The relay-scheduler TDD slice (commits `6c4f2054`/`ac44b302`/`ae9e7ef3`/`a9cf103c`, 6 unpushed on `experimental`) landed 4 **event-bus wiring** files in `com.huawei.ascend.bus.gateway.runtime`. They are semantically event-bus code (`@Profile("eventbus")` / relay scheduling), not gateway code. This refactor moves them out so `gateway.runtime` holds only gateway-specific code, and event-bus wiring gets its own package — establishing a clean gateway-vs-eventbus plane boundary under `com.huawei.ascend.bus..`.

## In scope (modules this change touches)

- **`gateway.runtime` (the MIXED package — 7 files):**
  - 4 to-move (event-bus wiring, `@Profile("eventbus")` / relay driver):
    - `EventBusRelayConfiguration` — `@Configuration @Profile("eventbus")`, wires the two-hop relay workers/producers/consumers/subscriptions.
    - `EventBusRelaySchedulingConfig` — `@Configuration @Profile("eventbus")`, wires `relayTaskScheduler` + `relayScheduler` beans.
    - `RelayScheduler` — plain class, `@PostConstruct start()` drives the two relay ticks on a dedicated `ThreadPoolTaskScheduler` (instantiated only by `EventBusRelaySchedulingConfig`).
    - `AgentBusBrokerProperties` — `@ConfigurationProperties("agent-bus")` record, 12 fields. **SHARED** — consumed by BOTH `GatewayRuntimeConfiguration` (gateway) AND `EventBusRelayConfiguration`/`EventBusRelaySchedulingConfig` (event-bus). Ownership is the open question (G3-Q2).
  - 3 to-stay (gateway-specific, `@Profile("gateway")` / web):
    - `GatewayRuntimeConfiguration` — `@Configuration @Profile("gateway")`. References `AgentBusBrokerProperties` (same-package today → needs an import after the move).
    - `GatewayRuntimeController` — `@RestController` (web confined to gateway.runtime per ADR-0160).
    - `GatewayRuntimeService` — `IngressGateway` impl.
- **`AgentBusApplication`** (package `com.huawei.ascend.bus`) — plain `@SpringBootApplication`, default component-scan covers `com.huawei.ascend.bus..` so any new sub-package is auto-picked-up (javadoc line 19 confirms). **No `@ComponentScan` change needed.**

## Out of scope (explicitly)

- `forwarding.spi` / `forwarding.runtime.*` (relay/transport/persistence/transport.broker) — the pure-Java domain + ports; **untouched** (the 4 files depend ON these, none of these move).
- `registry.runtime` — registry plane wiring; untouched.
- The 3 RealBroker*IT files that `import com.huawei.ascend.bus.gateway.runtime.GatewayRuntimeService` — `GatewayRuntimeService` STAYS in gateway.runtime, so these imports are unchanged.
- `GatewayRuntimeServiceTest` / `GatewayRuntimeControllerTest` — test the staying gateway classes; unchanged (grep-confirmed: neither references any of the 4 moved classes).
- `RelayTick.java` javadoc `{@code RelayScheduler}` — javadoc-only, not a code dependency; no ripple (grep-confirmed).
- External repo `agent-runtime-java` — cross-repo boundary only.
- L0/L1/L2 architecture docs themselves (reference authority; line 457 of `feat-013...md` names `gateway.runtime` for the gateway 生产实现 — still accurate for the gateway-specific files that stay).

## Why ONLY the module diagram (not flow / sequence) — stated out loud

This change moves **package boundaries + module ownership + the dependency direction** (gateway/eventbus wiring currently co-located → split into separate packages; shared config → common). That is a **module/development-view** delta. It does **not** move control flow, request path, state transitions, or cross-component ordering over time: the relay still consumes→governs→re-publishes; the scheduler still fires ticks at fixed delay; the Spring bean graph keeps the same names/qualifiers (`forwardRelayTick`/`responseRelayTick`/`forwardRelayWorker`/`responseRelayWorker`/`relayTaskScheduler`/`relayScheduler`); `@Profile("eventbus")` is unchanged. A flow or sequence diagram drawn as-is vs to-be would be **byte-identical except for package labels** — so per G2's "draw only what the change actually exercises", flow + sequence are **skipped** (G2 anti-pattern: don't draw the whole system). If a later change alters relay behavior, that change draws its own flow/sequence.

## CRITICAL as-is finding (corrects the handoff) — must read before G3/G4

The handoff doc asserts a **"hard constraint #1"**: *"AgentBusForwardingSpiPurityTest 现在只许可 Spring 在 gateway.runtime;搬到 eventbus.runtime 后必须给新包发 Spring 许可,否则 purity 测试变红"*. **Code-verified to be FALSE:**

1. `AgentBusForwardingSpiPurityTest` scopes **only** `com.huawei.ascend.bus.forwarding..` (every rule `resideInAPackage("com.huawei.ascend.bus.forwarding..")`). It licenses Spring ONLY inside `forwarding.runtime.persistence.jdbc`, RocketMQ ONLY inside `forwarding.runtime.transport.broker`. It **never inspects `gateway.runtime`** (or `eventbus.runtime`).
2. Enumerated **all 7 ArchUnit tests** in `agent-bus/src/test/.../architecture/`:
   - 4 purity tests scope `forwarding..` / `spi..` / `spi.registry..` / `registry..` — **none scope `gateway..` or `eventbus..`**.
   - `AgentBusDependencyBoundaryTest` scopes `bus..` not-depending-on sibling **platform** modules (agent-runtime/agent-service/…); moving WITHIN `bus..` is invisible to it.
   - `AgentBusForwardingDesignContractTest` = ICD doc-text assertions + a broker-package-name trip-wire (`broker`/`queue`/`mailbox`/`dlq`/`replay`); `eventbus.runtime` matches **none** of those keywords.
   - `AgentBusRegistryDiscoveryDesignContractTest` — grep for `gateway`/`eventbus`: **no matches**.
3. **Conclusion:** NO existing ArchUnit rule references `gateway.runtime`. Moving the 4 files to `eventbus.runtime` breaks **zero** existing rules; requires **no** "Spring license grant." The DoD item "ArchUnit 规则同步改且 green" is therefore **vacuously satisfiable** (rules stay green; nothing to sync) UNLESS G3 decides to ADD a new boundary rule (open question G3-Q3).

Also: the handoff's "**推翻 ADR-0161 的 gateway.runtime-as-wiring-home 决定**" is **mis-attributed**. ADR-0161 records 3 G5-E substrate deviations (response-relay governance-mode, `markRejected` upsert, V3 outbox columns) — it is NOT about `gateway.runtime`-as-wiring-home. That convention is **implicit prose** in `AgentBusBrokerProperties` javadoc (line 20: "Lives in gateway.runtime — the Spring-permitted wiring home") + plan doc `docs/superpowers/plans/2026-07-15-relay-scheduler.md:17`. So the refactor reverses an **implicit javadoc/plan convention**, not ADR-0161; the close artefact is a **new** ADR, not an amendment to 0161 (G3-Q4).

## Ripple scope (grep-verified, code only)

Grep `EventBusRelayConfiguration|EventBusRelaySchedulingConfig|RelayScheduler|AgentBusBrokerProperties` across `agent-bus` → 7 hits, of which the real code ripple is:
- **4 source files** — move + repackage (self-references between them are same-package today, no imports to fix between them).
- **`EventBusRelaySchedulerTest.java`** (test, package `gateway.runtime`) — tests `RelayScheduler`; move to mirror the new home + stays co-located (no import needed for the moved classes).
- **`GatewayRuntimeConfiguration.java`** (gateway, stays) — references `AgentBusBrokerProperties` (same-package today, no import; `@EnableConfigurationProperties(AgentBusBrokerProperties.class)` + 4 method params). After the move, needs an explicit `import` for it.
- `RelayTick.java` — javadoc-only `{@code RelayScheduler}`; **no ripple**.

Doc ripple (non-code, decision at G3-Q5): plan `2026-07-15-relay-scheduler.md` (3 package-decl snippets in code blocks + line 17 prose "Spring wiring confined to gateway.runtime"); L2 `feat-013...md` line 457 (gateway 生产实现 = gateway.runtime — still accurate for the staying files).
