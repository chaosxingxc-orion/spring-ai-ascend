# Decision tree — agent-bus layering refactor

G3 of arch-driven (code-driven). Only **genuinely open** architectural questions are branched. Each records branches / chosen / rejected-why. The user delegated the call ("你拍板") — I (the agent) picked a branch per Q with a one-line why; **G4 sign-off confirms or amends** before any code (no sign-off = no code).

**Authority:** code-verified as-is (`as-is/scope.md`); the event-bus-relay delta's ADR-0161 (confirmed = 3 G5-E substrate deviations, NOT a `gateway.runtime`-as-wiring-home decision); ADR-0160 (web confined to gateway.runtime); plan `2026-07-15-relay-scheduler.md`.

## G3.5 — UI prototype gate: SKIPPED (stated out loud)

Pure backend / package-move refactor — zero frontend template delta (no `.component.html`/`.vue`/`.tsx`, no UI surface added/removed/modified). G3.5 does not trigger; straight to G4. (L9: do not skip G3.5 for UI-touching changes — this is not UI-touching.)

---

## Q1 — Target package name + internal structure for the event-bus wiring?

**Q (user/handoff wording):** 「4 文件搬到 eventbus.runtime」「目标分层 — `eventbus.runtime`(新) — event-bus 独有 Spring 接线」.

**Branches:**
- **(a) `com.huawei.ascend.bus.eventbus.runtime`, flat (all 3 relay-wiring files here).** New top-level plane package under `bus`, mirroring the existing `gateway.runtime` / `registry.runtime` plane packages. Flat — no sub-packages for 3 files.
- (b) `com.huawei.ascend.bus.relay.runtime` — name by function (relay wiring), not by plane.
- (c) `com.huawei.ascend.bus.gateway.runtime.eventbus` — nest under gateway.

**Chosen: (a).** *Why:* the user's DoD explicitly names `eventbus.runtime`; it creates a clean **plane boundary** (gateway plane / eventbus plane / registry plane, all siblings under `bus`) matching the existing `gateway.runtime`/`registry.runtime` convention; the relay IS the event-bus, so a plane name is more durable than a function name (b) which would mis-scope a future non-relay event-bus wiring file. (c) defeats the refactor (gateway would still "own" event-bus wiring). Flat: 3 files is too few to justify sub-packages (premature abstraction). *Note:* only 3 of the 4 handoff files go here — see Q2 for `AgentBusBrokerProperties`.

**Rejected-why:** (b) mis-scopes future event-bus wiring (relay-named package holding a non-relay file is confusing); (c) leaves the inversion in place.

---

## Q2 — Where does `AgentBusBrokerProperties` go? (ownership — the handoff's flagged open question)

**Q:** 「`AgentBusBrokerProperties`(`@ConfigurationProperties("agent-bus")`)归属 event-bus 还是 common」. It is **shared** — consumed by BOTH `GatewayRuntimeConfiguration` (gateway, stays) AND `EventBusRelayConfiguration`/`EventBusRelaySchedulingConfig` (event-bus, moves); both annotate `@EnableConfigurationProperties(AgentBusBrokerProperties.class)`. 12 fields span BOTH planes (`gatewayServiceId` + `eventBusServiceId` + broker endpoints + timing knobs + tenant).

**Branches:**
- **(a) `com.huawei.ascend.bus.common` — a neutral shared-config package.** Both planes depend on `common`; neither depends on the other's implementation package. `AgentBusBrokerProperties` is plane-level config, not event-bus-specific.
- (b) Move it to `eventbus.runtime` with the other 3 (honors "4 files → eventbus.runtime" literally).
- (c) Leave it in `gateway.runtime` (event-bus wiring imports from gateway.runtime).

**Chosen: (a).** *Why:* the refactor's whole purpose is to stop gateway/event-bus wiring from co-locating and to make the dependency direction explicit ("gateway depends on event-bus **SPI**, not **impl**"). `AgentBusBrokerProperties` is a concrete `@ConfigurationProperties` record, not SPI. Putting it in `eventbus.runtime` (b) makes **gateway depend on the event-bus implementation package** for its config — the very inversion being fixed, and it would create a gateway→eventbus.runtime dependency that Q3's boundary rule would then forbid (forcing an awkward carve-out). (c) leaves event-bus wiring depending on gateway.runtime (the original sin). (a) — a neutral `common` package — matches the handoff's anticipated taxonomy ("common — 共享(按需,如 BrokerClientProperties 若跨 gateway/event-bus 共用)") and the existing pattern: `BrokerClientProperties` (the broker adapter's own props, built FROM `AgentBusBrokerProperties`) already lives in `forwarding.runtime.transport.broker`. `common` is the plane-level analogue. Package name `com.huawei.ascend.bus.common` (conventional, flat — one shared record).

**Consequence for the DoD:** the literal "4 文件搬到 eventbus.runtime" becomes **3 files → `eventbus.runtime` + 1 file (`AgentBusBrokerProperties`) → `common`**. This is a **deviation from the DoD's literal count** — surfaced here, not silently. G4 sign-off ratifies it.

**Rejected-why:** (b) inverts the dependency (gateway→eventbus.impl) and fights Q3's boundary; (c) preserves the co-location the refactor removes.

---

## Q3 — Add a new ArchUnit rule to enforce the new layering, or keep it purely mechanical?

**Q:** The handoff's "hard constraint #1" (must grant Spring permission to `eventbus.runtime` or purity turns red) is **code-verified FALSE** — no rule scopes `gateway..`/`eventbus..`, so moving breaks zero rules and needs no grant (`as-is/scope.md` "CRITICAL as-is finding"). So "ArchUnit 规则同步改且 green" is vacuously satisfiable (nothing to change, stays green). The open question: does this refactor **add** a rule encoding the new boundary, or stay mechanical?

**Branches:**
- **(a) Add ONE new boundary rule** (in `AgentBusForwardingSpiPurityTest` or a sibling): `gateway.runtime..` must not depend on `eventbus.runtime..` (gateway depends on `forwarding.spi` + `common` only). Enforces the layering the refactor establishes; prevents future regression (an unenforced boundary rots back).
- (b) Mechanical only — move files, fix imports, tests green, no new rule. Minimal diff; honors "surgical changes".

**Chosen: (a), scoped to the single rule `gateway.runtime must not depend on eventbus.runtime`.** *Why:* the refactor's entire point is to establish the gateway↔eventbus boundary; a boundary that exists only as a convention + javadoc (exactly what "gateway.runtime-as-wiring-home" was — and it rotted into the co-location we're now fixing) will rot again. One ArchUnit rule makes it load-bearing. This is in-scope for the user's stated intent (step 3: "SPI 边界") — the boundary is the deliverable. Kept to ONE rule (not a full lattice) to avoid scope creep / kitchen-sink; `common`↔plane rules can be added later if a real violation threatens. *Note:* this expands the change beyond a pure move; it's the one piece of NEW behaviour. If the user wants strictly mechanical, pick (b) and the rule stays absent — flag the trade-off explicitly at sign-off.

**Rejected-why:** (b) leaves the new boundary unenforced — the same posture that produced the original co-location.

**Detail for G5 (if (a) chosen):** add a `@Test` mirroring the purity-test style:
```java
@Test
void gateway_runtime_does_not_depend_on_eventbus_runtime() {
    noClasses().that().resideInAPackage("com.huawei.ascend.bus.gateway.runtime..")
            .should().dependOnClassesThat().resideInAPackage("com.huawei.ascend.bus.eventbus.runtime..")
            .because("agent-bus layering (ADR-NNNN): gateway depends on forwarding SPI + shared "
                   + "common config, never on the event-bus wiring implementation package.")
            .check(BUS_PRODUCTION); // importPackages("com.huawei.ascend.bus")
}
```
plus a non-empty guard if reusing the `BUS_PRODUCTION` from `AgentBusDependencyBoundaryTest` (or a local import). This rule is **green immediately** (gateway has no eventbus.runtime dep today, and post-move `GatewayRuntimeConfiguration` imports `AgentBusBrokerProperties` from `common` not `eventbus.runtime`).

---

## Q4 — ADR: new, or amend ADR-0161? (handoff said "推翻 ADR-0161")

**Q:** The handoff says reverse "ADR-0161 的 gateway.runtime-as-wiring-home 决定".

**Branches:**
- **(a) New ADR (next free number, e.g. ADR-0162)** recording: the `gateway.runtime`-as-Spring-wiring-home convention (previously implicit in `AgentBusBrokerProperties` javadoc + plan doc, NOT in any enforced rule, NOT in ADR-0161) is superseded; event-bus wiring now lives in `eventbus.runtime`; shared config in `common`; the new boundary rule (Q3) enforces it.
- (b) Amend ADR-0161.

**Chosen: (a).** *Why:* ADR-0161 is about 3 G5-E substrate deviations (response-relay governance-mode / `markRejected` upsert / V3 outbox columns) — it says **nothing** about `gateway.runtime`-as-wiring-home. Amending it (b) conflates an unrelated decision and mis-records history. The convention being reversed is implicit (javadoc + plan prose), so the correct close artefact is a **new** ADR that records the explicit layering decision + supersedes the implicit convention. Written at G7 close (after as-built confirmed).

**Rejected-why:** (b) mis-attributes — ADR-0161 has no `gateway.runtime`-as-wiring-home decision to reverse.

---

## Q5 — Doc updates: edit the closed plan doc + L2 line, or leave?

**Q:** grep `com.huawei.ascend.bus.gateway.runtime` hit doc references: plan `2026-07-15-relay-scheduler.md` (lines 17, 310, 469, 607 — "Spring wiring confined to gateway.runtime" prose + 3 package-decl snippets in code blocks) and L2 `feat-013...md` line 457 (gateway 生产实现 = `gateway.runtime`).

**Branches:**
- **(a) Leave both.** The plan doc is a CLOSED record of done work (its package-decl snippets accurately record what was committed at the time; editing rewrites history). The L2 line is still accurate for the gateway-specific files that stay.
- (b) Update both to the new package layout.

**Chosen: (a).** *Why:* the closed plan doc is a historical artefact (the relay-scheduler slice is CLOSED + pushed-pending); rewriting its code blocks would falsify the record. The L2 line describes the gateway 生产实现 location, which is still `gateway.runtime` for the 3 staying files — accurate. The **new** ADR (Q4) + this delta's `deviations.md`/`CLOSED.md` are where the layering change is recorded going forward. (If the user wants the plan doc's prose line 17 annotated with a "superseded by ADR-NNNN" pointer, that's a cheap follow-up — flag at sign-off, not blocking.)

**Rejected-why:** (b) falsifies a closed plan record + edits an L2 line that remains accurate.

---

## Non-open constraints (recorded for G4, not branched)

- **`@Profile("eventbus")` unchanged** on `EventBusRelayConfiguration`/`EventBusRelaySchedulingConfig`; `RelayScheduler` is a plain class instantiated only by the scheduling config. No profile/wiring semantic change.
- **Bean names / qualifiers unchanged:** `forwardRelayTick`/`responseRelayTick`/`forwardRelayWorker`/`responseRelayWorker`/`relayTaskScheduler`/`relayScheduler`. Spring wiring is package-agnostic (bean identity by name/type, not package).
- **Component scan:** `AgentBusApplication` (plain `@SpringBootApplication` in package `com.huawei.ascend.bus`) default-scans `com.huawei.ascend.bus..` → new `eventbus.runtime` + `common` auto-covered (javadoc line 19 confirms; no `@ComponentScan` edit).
- **Spring 7 / Boot 4.0.5 / Java 21 / Jakarta** idioms already correct in `RelayScheduler` (`scheduleWithFixedDelay`, `jakarta.annotation.PostConstruct`) — package move doesn't touch API usage.
- **Web confinement (ADR-0160):** `GatewayRuntimeController` (`@RestController`) stays in `gateway.runtime` — web confined there; unaffected.

## Load-bearing assumptions to verify before G5 implement (L3)

1. **`AgentBusBrokerProperties` has no positional `new` call sites** (handoff §待搬迁 claims it's yaml-bound only). → grep `new AgentBusBrokerProperties(` across the repo before moving; if any positional constructor call exists outside `EventBusRelaySchedulerTest`, the move must update it. (`EventBusRelaySchedulerTest.java:71` has one — expected, it's the test for the moved class.)
2. **`GatewayRuntimeConfiguration` is the ONLY staying file that references a moved class** (it references `AgentBusBrokerProperties`; the other 2 staying files `GatewayRuntimeController`/`GatewayRuntimeService` + their tests reference none of the 4 — grep-confirmed). → the move adds exactly ONE import to `GatewayRuntimeConfiguration`.
3. **No ArchUnit rule scopes `gateway..`/`eventbus..`** (verified G2 — `as-is/scope.md` "CRITICAL as-is finding"). → the move breaks zero rules; Q3's new rule is the only ArchUnit delta.
