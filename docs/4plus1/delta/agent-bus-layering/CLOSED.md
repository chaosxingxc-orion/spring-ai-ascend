# CLOSED — agent-bus layering refactor (arch-driven)

- **Change slug:** `agent-bus-layering`
- **Closed:** 2026-07-16
- **ADR:** [0162](../../../../adr/0162-agent-bus-layering.md) (new; does NOT amend ADR-0161)
- **Delta artifacts:** `docs/4plus1/delta/agent-bus-layering/` (as-is · decision-tree · to-be + sign-off · deviations · as-built + diff · overview.html · this CLOSED.md)

## What the change was

Move the 3 event-bus relay-wiring files (`EventBusRelayConfiguration`, `EventBusRelaySchedulingConfig`, `RelayScheduler`) out of `com.huawei.ascend.bus.gateway.runtime` into a NEW `com.huawei.ascend.bus.eventbus.runtime`; move the shared `AgentBusBrokerProperties` into a NEW `com.huawei.ascend.bus.common` (consumed by both planes — neither gateway- nor event-bus-specific); keep the 3 gateway-specific files in `gateway.runtime` (gateway-only); move `EventBusRelaySchedulerTest` to mirror `RelayScheduler`; add ONE ArchUnit rule (`gateway.runtime ↛ eventbus.runtime`) to make the new plane boundary load-bearing. Pure package re-organisation + one enforced boundary — no behaviour, profile, bean-name, or component-scan change.

## What was signed off (G4, 2026-07-16)

User confirmed all 5 G3 branches as the agent 拍板'd them ("确认,5 分支全按方案"):

- **Q1** `eventbus.runtime`, flat (sibling to `gateway`/`registry.runtime`).
- **Q2** `AgentBusBrokerProperties` → `common` (not `eventbus.runtime`) — the one deviation from the DoD's literal "4 files → `eventbus.runtime`" (now 3 + 1); shared by both planes.
- **Q3** add ONE ArchUnit rule `gateway.runtime ↛ eventbus.runtime` (the one piece of NEW behaviour vs a purely mechanical move).
- **Q4** new ADR (0162), NOT amend ADR-0161 (unrelated).
- **Q5** leave the closed plan doc + L2 line 457 untouched.

Plus the two corrected-handoff facts acknowledged (hard-constraint #1 = FALSE; "reverse ADR-0161" = mis-attributed).

## What drifted and was accepted (G6)

All Matched ✓ architecturally + 2 Drifted ⚠ (both accepted as-is, neither a plan-breaker, neither a decision change):

1. **Import ripple was 4 files, not 1** — mechanical consequence of Q2 (`AgentBusBrokerProperties` → `common`, so the 3 moved files that reference it need the import too). `RelayScheduler` correctly needs none (constructor takes primitives). See `deviations.md` #1/#2.
2. **ArchUnit rule home = `AgentBusDependencyBoundaryTest` (sibling)**, not `AgentBusForwardingSpiPurityTest` (would have been vacuous — that test's `FORWARDING` set excludes gateway/eventbus). Decision-tree Q3 anticipated the sibling. See `deviations.md` #3.

Zero Missing ✗, zero Surprise ⚠⚠ — the pure-mechanical-relocation outcome the handoff predicted.

## Verification (DoD)

- `./mvnw -f agent-bus/pom.xml test` → **443 green / 14 skip / 0 fail** (was 441 +2 = the Q3 rule + liveness guard). `BUILD SUCCESS`.
- ArchUnit green (incl. the new `gateway↛eventbus` rule + liveness guard; `AgentBusForwardingSpiPurityTest` 14 green — the move touches zero forwarding-purity rules, confirming the handoff's hard-constraint #1 stayed FALSE).
- Working tree: 5 renames + content edits + 2 staying-file edits, committed on `experimental` (separate from the 6 prior unpushed relay-scheduler commits). Push deferred per session convention.

## Follow-ups (not blocking)

- `docs/adr/ADR-CLASSIFICATION.md`'s per-ADR table is broadly stale (stops at 0068; missing 0069–0082 + 0161 + now 0162). Following the 0161 precedent (ADR file exists without a classification row), 0162's row was not added in this change; a bulk registry resync is a separate cleanup (out of this refactor's scope).
- The closed plan doc `docs/superpowers/plans/2026-07-15-relay-scheduler.md` line 17 ("Spring wiring confined to `gateway.runtime`") is now historically inaccurate prose; left untouched per Q5 (closed record). A "superseded by ADR-0162" pointer annotation is a cheap optional follow-up.
