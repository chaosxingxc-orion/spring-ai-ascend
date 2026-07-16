# Deviations — agent-bus layering refactor (G5)

Deviations of the as-implemented G5 from the to-be plan (`to-be/module-delta.md` + handoff). Per arch-driven: deviations are not sins; unrecorded deviations are. **None of these change a G3/G4 decision** — they are mechanical consequences of Q2 (`AgentBusBrokerProperties` → `common`, not `eventbus.runtime`) or clarifications of the handoff's suggested locations. No G4 round-trip was warranted for any of them.

## 1. AgentBusBrokerProperties → `common` import ripple was 4 files, not 1

**To-be said:** "`GatewayRuntimeConfiguration` gains one `import …common.AgentBusBrokerProperties;` (the one gateway-side ripple)" (`to-be/module-delta.md` file-change table).

**As-built:** FOUR files gained `import com.huawei.ascend.bus.common.AgentBusBrokerProperties;`:
- `GatewayRuntimeConfiguration` (stays in `gateway.runtime`) — the documented one.
- `EventBusRelayConfiguration` (→ `eventbus.runtime`) — not documented.
- `EventBusRelaySchedulingConfig` (→ `eventbus.runtime`) — not documented.
- `EventBusRelaySchedulerTest` (→ `eventbus.runtime`, test) — not documented.
- `RelayScheduler` (→ `eventbus.runtime`) — correctly needs **no** import (its constructor takes primitives; it does not reference `AgentBusBrokerProperties`).

**Reason:** Q2 put `AgentBusBrokerProperties` in `common` (not `eventbus.runtime`). Today all five files are co-located with it in `gateway.runtime` (same-package, no imports). After the split, every file that references `AgentBusBrokerProperties` and is NOT in `common` needs the import — including the moved relay files, which are no longer co-located with it. The to-be plan's "one gateway-side ripple" under-counted because it scoped the ripple to **staying** files only, not to all files whose package-relationship to `AgentBusBrokerProperties` changed. Direct mechanical consequence of Q2; not a decision change.

**Load-bearing-assumption check (L3):** the L3 assumption "*GatewayRuntimeConfiguration* is the only STAYING file referencing a moved class" was **CONFIRMED** (true for staying files). But the plan conflated "staying files referencing moved classes" with "files needing a new import"; the latter is broader. L3 verification surfaced this before the build — exactly its purpose (arch-driven L3).

## 2. EventBusRelaySchedulerTest gained an import for AgentBusBrokerProperties

**To-be said:** "moved to mirror `RelayScheduler`; package decl updated (stays co-located → no import for the moved classes)" (`to-be/module-delta.md`).

**As-built:** the test gained `import com.huawei.ascend.bus.common.AgentBusBrokerProperties;`. The "stays co-located → no import" held for `RelayScheduler` (same-package `eventbus.runtime`, no import needed) but NOT for `AgentBusBrokerProperties` (→ `common`, different package). Subset of deviation #1; recorded separately because the plan's specific "no import" claim was partially false.

## 3. ArchUnit rule home: AgentBusDependencyBoundaryTest, not AgentBusForwardingSpiPurityTest

**Handoff said:** "给 `AgentBusForwardingSpiPurityTest` 加 1 条 … 规则(或 sibling)".

**As-built:** the rule + its liveness guard live in `AgentBusDependencyBoundaryTest` (the sibling), reusing its `BUS_PRODUCTION` (`importPackages("com.huawei.ascend.bus")`, production-only, `DO_NOT_INCLUDE_TESTS`). Decision-tree Q3 anticipated this ("reusing the `BUS_PRODUCTION` from `AgentBusDependencyBoundaryTest` (or a local import)").

**Reason:** `AgentBusForwardingSpiPurityTest`'s `JavaClasses` set (`FORWARDING`) imports ONLY `com.huawei.ascend.bus.forwarding` — it contains zero `gateway.runtime`/`eventbus.runtime` classes. A `gateway.runtime ↛ eventbus.runtime` rule checked against `FORWARDING` would pass **vacuously** (no gateway classes in the set to violate it) — exactly the "empty import silently passes" failure that test's own `forwarding_import_is_non_empty` liveness guard warns about. `AgentBusDependencyBoundaryTest`'s `BUS_PRODUCTION` imports the whole bus tree (incl. gateway + eventbus + common), so the rule is non-vacuous there. Not a deviation from Q3 (which anticipated the sibling); a clarification of the handoff's primary suggested location.

## 4. Suite is 443 green/14 skip, not 441 — the +2 are the Q3 rule + guard (expected)

**Baseline:** 441 green / 14 skip / 0 fail (pre-move, per handoff/memory).
**Post-move:** 443 green / 14 skip / 0 fail. `BUILD SUCCESS`.

**Reason:** +2 tests = the new ArchUnit rule (`gateway_runtime_does_not_depend_on_eventbus_runtime`) + its liveness guard (`gateway_and_eventbus_runtime_planes_are_non_empty`), both in `AgentBusDependencyBoundaryTest` (was 7 tests, now 9). This is the Q3 decision (add ONE rule) + the skill-mandated non-empty guard against vacuous-pass. Expected, not a regression. The handoff's "441 green/14 skip" DoD figure was the PRE-add baseline; post-add is 443.

## 5. (Minor) No javadoc home-ref to update on the 3 relay files

**To-be said:** "package decl + javadoc home-ref updated" for the 3 moved relay files.
**As-built:** `EventBusRelayConfiguration` / `EventBusRelaySchedulingConfig` / `RelayScheduler` javadocs contain no "lives in gateway.runtime" home-ref prose — only the package decl (+ import where needed) changed. Only `AgentBusBrokerProperties` had the "Lives in `gateway.runtime` — the Spring-permitted wiring home" prose (updated to "Lives in `common`, shared by both planes"). The plan's anticipated javadoc edits on the 3 relay files were no-ops. Less work, not more; recorded for diff transparency.

## Verified DoD

- `./mvnw -f agent-bus/pom.xml test` → **443 green / 14 skip / 0 fail** (+2 vs 441 baseline = the Q3 rule + guard). `BUILD SUCCESS`.
- ArchUnit green (incl. the new `gateway↛eventbus` rule + its liveness guard).
- Working tree: 5 renames (staged) + content edits on the moved files + 2 staying-file edits (`GatewayRuntimeConfiguration`, `AgentBusDependencyBoundaryTest`) + this delta's artefacts (untracked). Commit at G7 on `experimental`, separate from the 6 prior unpushed relay-scheduler commits; push deferred per session convention.
