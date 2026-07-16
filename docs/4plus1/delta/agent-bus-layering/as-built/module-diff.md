# as-built · module diff — agent-bus layering (G6)

Redrawn from the REAL post-G5 code (not by editing to-be). The refactor was a pure mechanical relocation + one ArchUnit rule, so the as-built **matches the to-be plan** — every planned delta landed. Two **Drifted ⚠** deltas are the import-ripple under-enumeration (mechanical consequence of Q2, not a decision change; see `deviations.md` #1/#2/#3). Zero Missing, zero Surprise — exactly the "pure mechanical relocation, no surprises" the handoff predicted.

## Matched ✓ (as-built realised the to-be delta)

- ✓ **`++ common`** package created — `AgentBusBrokerProperties` now at `com.huawei.ascend.bus.common` (Q2a landed). Javadoc "Lives in `gateway.runtime` — Spring-permitted wiring home" prose updated to "Lives in `common`, shared by both planes".
- ✓ **`++ eventbus.runtime`** package created — `EventBusRelayConfiguration` + `EventBusRelaySchedulingConfig` + `RelayScheduler` now at `com.huawei.ascend.bus.eventbus.runtime` (Q1a landed, flat — no sub-packages). `@Profile("eventbus")` + bean names/qualifiers unchanged.
- ✓ **`~ gateway.runtime`** now gateway-only — 3 files (`GatewayRuntimeConfiguration` + `GatewayRuntimeController` + `GatewayRuntimeService`); lost the 4 event-bus files.
- ✓ **`++ ArchUnit rule`** `gateway.runtime ↛ eventbus.runtime` landed (Q3a) — in `AgentBusDependencyBoundaryTest`, `check(BUS_PRODUCTION)`, **green immediately** + a liveness guard asserting both planes are non-empty.
- ✓ **Dependency direction** — `gateway.runtime`→`common` (AgentBusBrokerProperties), `eventbus.runtime`→`common` + `forwarding.spi` + `forwarding.runtime.*`; gateway does **not** depend on `eventbus.runtime`. The cross-package arrows the to-be drew (`EBRCFG-->PROPS`, `EBSCHED-->PROPS`, `GWCFG-->PROPS`) are realised as explicit `import com.huawei.ascend.bus.common.AgentBusBrokerProperties;` statements.
- ✓ **Unchanged context** — `forwarding.spi` / `forwarding.runtime.{relay,transport.broker,persistence.jdbc}` / `registry.runtime` untouched; the moved files depend on them exactly as before.
- ✓ **Component scan** — `AgentBusApplication` (plain `@SpringBootApplication` in `com.huawei.ascend.bus`) default-scans `com.huawei.ascend.bus..`; the new `common` + `eventbus.runtime` are auto-covered. No `@ComponentScan` change (javadoc line 19 confirms).

## Drifted ⚠ (as-built differs from to-be — both documented, neither a plan-breaker)

- ⚠ **Import ripple was 4 files, not 1.** To-be said "the one gateway-side ripple" (`GatewayRuntimeConfiguration` +1 import). As-built: **4 files** import `common.AgentBusBrokerProperties` — `GatewayRuntimeConfiguration` (stays) + `EventBusRelayConfiguration` + `EventBusRelaySchedulingConfig` + `EventBusRelaySchedulerTest` (the 3 moved files that reference it, no longer co-located with it). `RelayScheduler` correctly needs **no** import (its constructor takes primitives; it does not reference `AgentBusBrokerProperties`). Architecturally consistent — the to-be arrows already drew `EBRCFG→PROPS`/`EBSCHED→PROPS`; the imports just realise them. Mechanical consequence of Q2 (BrokerProperties→`common`, not `eventbus.runtime`). See `deviations.md` #1/#2. No G4 round-trip.
- ⚠ **ArchUnit rule home = `AgentBusDependencyBoundaryTest` (sibling), not `AgentBusForwardingSpiPurityTest`.** The handoff's primary suggestion would have been vacuous (that test's `FORWARDING` set imports only `forwarding..`, excluding gateway/eventbus — the rule would pass against an empty subset). Decision-tree Q3 anticipated the sibling + `BUS_PRODUCTION`. See `deviations.md` #3.

## Missing ✗ (to-be said X, as-built has no X)

- ✗ none.

## Surprise ⚠⚠ (as-built has Y, to-be never mentioned Y)

- ⚠⚠ none. Pure mechanical relocation; the architecture landed exactly as G4 signed off.

## Suite verification (DoD)

- `./mvnw -f agent-bus/pom.xml test` → **443 green / 14 skip / 0 fail** (was 441 +2 = the Q3 rule + liveness guard). `BUILD SUCCESS`.
- ArchUnit green (incl. the new `gateway↛eventbus` rule + liveness guard; the moved `EventBusRelaySchedulerTest` green at its new package `com.huawei.ascend.bus.eventbus.runtime`).
- `AgentBusForwardingSpiPurityTest` (14) green — confirms the move touches zero forwarding-purity rules (the handoff's "hard-constraint #1" stayed FALSE).

## Net

All Matched ✓ + 2 Drifted ⚠ (documented, mechanical, no decision change) + 0 Missing + 0 Surprise. The refactor is a clean, surgical package re-organisation + one enforced boundary — exactly what G4 signed off.
