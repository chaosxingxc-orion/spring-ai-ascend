---
rule_id: D-3
title: "Pre-Commit Checklist + Evidence-First Debug"
level: L1
view: process
principle_ref: P-D
authority_refs: []
enforcer_refs: [E112]
status: active
scope_phase: verify
kernel_cap: 12
kernel: |
  **Before every commit, audit every touched file; fix defects before committing — "I'll fix it later" is forbidden; **smoke + lint** required before commits touching server entry points, runtime adapters, or dependency-wiring modules (sub-clause .a — Pre-Commit Checklist). When a Run fails, a test regresses, or a self-audit finding is opened, the first artefact captured MUST be observable evidence — failing test class FQN, trace ID, MDC slice (runId, tenantId, fromStatus→toStatus), and raw error message including stack frame line numbers; ARCHITECTURE.md / ADR consultation is permitted only AFTER evidence is recorded; self-audit findings under Rule D-5 that omit evidence citation are blocked (sub-clause .b — Evidence-First Debug; operationalised by `docs/runbooks/debug-first-evidence.md`).**
---

# Rule D-3 — Pre-Commit Checklist + Evidence-First Debug

Daily-discipline rule consolidating the pre-commit audit checklist (sub-clause .a) with the evidence-first debug sequence (sub-clause .b).

## Sub-clauses

### .a — Pre-Commit Checklist (was Rule 3)

Before every commit, audit every touched file. Fix defects before committing — "I'll fix it later" is forbidden. **Smoke + lint** required before commits touching server entry points, runtime adapters, or dependency-wiring modules.

### .b — Evidence-First Debug Sequence (was Rule 79)

**Enforcer**: E112 (`docs/runbooks/debug-first-evidence.md` present-and-cited).

When a Run fails, a test regresses, or a self-audit finding is opened, the first artefact captured MUST be observable evidence — the failing test class FQN, the trace ID (if present), the MDC slice (runId, tenantId, fromStatus→toStatus), and the raw error message including stack frame line numbers. ARCHITECTURE.md / ADR consultation is permitted only AFTER evidence is recorded in the finding. Self-audit findings under Rule D-5 that omit evidence citation are blocked. Operationalised by `docs/runbooks/debug-first-evidence.md`.
