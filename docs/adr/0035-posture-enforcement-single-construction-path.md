# 0035. Posture Enforcement Single-Construction-Path

**Status:** accepted
**Deciders:** architecture
**Date:** 2026-05-13
**Technical story:** Sixth reviewer (LucioIT L3 concession) found `SyncOrchestrator` has no posture
guard — dev-only restriction is Javadoc only. Seventh reviewer (P1.3) found `posture-model.md`
had two factual errors (POST-only filter; UnsupportedOperationException). Cluster 2 self-audit
surfaced 8 hidden defects: three in-memory components each independently read `APP_POSTURE`
(Rule D-8 violation). This ADR defines `AppPostureGate` as the single-construction-path posture
utility and names `posture-model.md` as the canonical posture-truth ledger.

## Context

`InMemoryCheckpointer` read `System.getenv("APP_POSTURE")` directly in its constructor. Neither
`InMemoryRunRegistry` nor `SyncOrchestrator` had any posture check. Three components each
implemented (or omitted) posture reading independently — a Rule D-8 violation.

`posture-model.md` contained two facts that had drifted from the code:
- Line 20: "Missing Idempotency-Key on POST" — actual filter covers POST/PUT/PATCH.
- Line 22: "throws `UnsupportedOperationException`" — actual throw is `IllegalStateException`.

The gate had no rule to catch posture-truth drift in `posture-model.md`.

## Decision Drivers

- Rule D-8 (Single Construction Path): all `APP_POSTURE` reading must be centralised.
- Seventh reviewer P1.3: posture-model.md must be accurate — it is a truth-claim document.
- Hidden defect 2.8: posture-model.md is a truth-claim doc that lags behind components. Gate must cover it.
- Hidden defect 2.3: three in-memory components each independently implement posture reading.

## Considered Options

1. **`AppPostureGate` as single utility; `posture-model.md` as canonical ledger; Gate Rule 12** — this decision.
2. **Spring-managed posture bean** — would require Spring import in pure-Java SPI classes; violates §4 #7.
3. **Constructor injection of posture** — callers must supply posture string; breaks Rule D-8 (callers shouldn't know about posture).

## Decision Outcome

**Chosen option:** Option 1.

### AppPostureGate (§4 #32)

`com.huawei.ascend.runtime.posture.AppPostureGate` — pure Java, no Spring imports:

```java
public final class AppPostureGate {
    public static void requireDevForInMemoryComponent(String componentName) {
        // dev / null: emit [WARN] to stderr and continue
        // research / prod: throw IllegalStateException with ADR-0035 reference
    }
}
```

**Rule D-8 enforcement:** `AppPostureGate` is the ONLY class permitted to call
`System.getenv("APP_POSTURE")`. Gate Rule 12 asserts
`AppPostureGate.requireDevForInMemoryComponent` literal exists in all three in-memory classes.

### In-memory component posture guards (W0 shipped)

| Component | Change |
|---|---|
| `InMemoryCheckpointer` | Constructor calls `AppPostureGate.requireDevForInMemoryComponent("InMemoryCheckpointer")`; `failOnOversize=false` (dev only reaches here) |
| `InMemoryRunRegistry` | Constructor calls `AppPostureGate.requireDevForInMemoryComponent("InMemoryRunRegistry")` |
| `SyncOrchestrator` | Constructor calls `AppPostureGate.requireDevForInMemoryComponent("SyncOrchestrator")` |

### posture-model.md as canonical posture-truth ledger

`docs/cross-cutting/posture-model.md` is the **single source of truth** for all posture-aware
component behaviours. Every posture-aware component MUST be listed in its table. Factual
corrections shipped this cycle:
- "POST" → "POST / PUT / PATCH" (IdempotencyHeaderFilter actual scope).
- `UnsupportedOperationException` → `IllegalStateException` (IdempotencyStore actual throw).
- Added rows: `InMemoryRunRegistry construction`, `InMemoryCheckpointer construction`,
  `SyncOrchestrator construction`, `InMemoryCheckpointer 16-KiB cap`.
- Added generalised dev-warn / research+prod-throw pattern statement.

### Gate Rule 12 — `inmemory_orchestrator_posture_guard_present`

Verifies that the literal string `AppPostureGate.requireDevForInMemoryComponent` appears in each
of `SyncOrchestrator.java`, `InMemoryRunRegistry.java`, `InMemoryCheckpointer.java`.
Fails if any of the three files lacks the literal (prevents silent regression).

### Consequences

**Positive:**
- Rule D-8 restored: single construction path for posture reading.
- All three in-memory classes now throw in research/prod posture, preventing accidental non-durable state in production.
- `posture-model.md` is now accurate and gate-enforced.

**Negative:**
- `InMemoryCheckpointer`'s `failOnOversize` path is only reachable via the package-private test constructor; the public no-arg constructor throws before reaching it in research/prod.

## References

- Seventh reviewer P1.3: `docs/logs/reviews/2026-05-13-l0-architecture-readiness-agent-systems-review.en.md`
- Sixth reviewer L3 concession: `docs/logs/reviews/2026-05-12-architecture-LucioIT-wave-1-request.en.md`
- Rule D-8 (Single Construction Path Per Resource Class)
- Rule D-6 (Posture-Aware Defaults)
- `AppPostureGate.java`, `SyncOrchestrator.java`, `InMemoryRunRegistry.java`, `InMemoryCheckpointer.java`
- `docs/cross-cutting/posture-model.md` (updated this cycle)
- `gate/check_architecture_sync.ps1` Gate Rule 12
- `architecture-status.yaml` row: `posture_single_construction_path`
