---
principle_id: P-M
title: "Heterogeneous Engine Contract & Server-Sovereign Boundary"
level: L0
view: logical
authority: "Layer 0 governing principle (CLAUDE.md); W2.x engine contract structural wave"
enforced_by_rules: [R-M.a, R-M.b, R-M.c, R-M.d, R-M.e, M-2.a]   # formerly Rules 43, 44, 45, 46, 47, 48 (numeric pre-ADR-0086)
product_claim: "PC-004"
kernel: |
  P-M — Heterogeneous Engine Contract & Server-Sovereign Boundary.
  The platform supports heterogeneous execution engines through a structured
  contract surface: a lightweight configuration envelope governs registration
  / routing / observability, strict matching prevents silent reinterpretation
  of engine-specific payloads, runtime-owned middleware attaches via
  engine-declared lifecycle hooks, server-to-client capability invocation is
  an explicit asynchronous protocol bound to the suspend/resume loop, and the
  evolution mechanism manages only server-controlled execution scope by
  default.
  Enforced by Rules R-M.a–R-M.e (formerly Rules 43–47); cross-cutting structural invariant operationalised
  by Rule M-2 sub-clause .a (formerly Rule 48; Schema-First Domain Contracts).
scope_phase: design
---

## Motivation

This principle exists because **a platform that supports more than one execution engine without a structured contract surface degenerates into N parallel implementations, N policy stacks, and N observability surfaces** — every engine ends up patched independently with cross-cutting concerns (model gateway, tool authz, memory governance, tenant policy, quota, observability, sandbox routing, checkpoint, failure handling), the platform team loses control of the policy boundary, and "heterogeneous engine support" becomes "heterogeneous bug surface". P-M imposes a five-rule structural contract — **envelope + strict matching + hook-based middleware + S2C protocol + evolution scope boundary** — and a sixth cross-cutting rule (Rule M-2 sub-clause .a — Schema-First Domain Contracts) ensures every NEW fixed-vocabulary taxonomy lands as `yaml schema → Java type → runtime self-validate` rather than as prose drift. The "Server-Sovereign Boundary" wording captures the second half: the evolution mechanism manages only server-controlled execution scope, not client-supplied execution.

## Operationalising rules

- Rule R-M sub-clause .a — Engine Envelope Single Authority ([`docs/governance/rules/rule-R-M.md`](../rules/rule-R-M.md))
- Rule R-M sub-clause .b — Strict Engine Matching ([`docs/governance/rules/rule-R-M.md`](../rules/rule-R-M.md))
- Rule R-M sub-clause .c — Runtime-Owned Middleware via Engine Hooks ([`docs/governance/rules/rule-R-M.md`](../rules/rule-R-M.md))
- Rule R-M sub-clause .d — S2C Callback Envelope + Lifecycle Bound ([`docs/governance/rules/rule-R-M.md`](../rules/rule-R-M.md))
- Rule R-M sub-clause .e — Evolution Scope Default Boundary ([`docs/governance/rules/rule-R-M.md`](../rules/rule-R-M.md))
- Rule M-2 sub-clause .a — Schema-First Domain Contracts ([`docs/governance/rules/rule-M-2.md`](../rules/rule-M-2.md))

## Cross-references

- ADR-0071 (umbrella ADR for the W2.x engine contract structural wave)
- ADR-0072 (engine envelope + strict matching — Rules 43/44)
- ADR-0073 (engine hooks + runtime-owned middleware — Rule R-M sub-clause .c)
- ADR-0074 (S2C callback envelope + lifecycle bound — Rule R-M sub-clause .d)
- ADR-0075 (evolution scope default boundary — Rule R-M sub-clause .e)
- ADR-0077 (schema-first domain contracts — Rule M-2 sub-clause .a cross-cutting invariant)
- Contract sources of truth under `docs/contracts/`: `engine-envelope.v1.yaml`, `engine-hooks.v1.yaml`, `s2c-callback.v1.yaml`; governance scope at `docs/governance/evolution-scope.v1.yaml`
- Deferred sub-clauses: 44.b, 44.c (matching follow-on), 45.b (Run-state consumption of HookOutcome — W2 Telemetry Vertical), 46.b, 46.c (S2C async orchestrator), 48.b, 48.c (schema-first follow-on); legacy deferred-rule registry retired 2026-05-28, see [`retired-rules-audit.md`](../retired-rules-audit.md)
- Related: P-D (SPI + DFX + TCK) — P-M is the W2.x extension of P-D into engine pluggability
- Related: P-H (Chronos Hydration) — S2C callback (Rule R-M sub-clause .d) uses SuspendSignal sealed checked-suspension variant
