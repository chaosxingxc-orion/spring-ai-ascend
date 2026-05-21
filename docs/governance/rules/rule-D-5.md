---
rule_id: D-5
title: "Self-Audit is a Ship Gate, Not a Disclosure"
level: L0
view: scenarios
principle_ref: P-A
authority_refs: []
enforcer_refs: []
status: active
scope_phase: commit
kernel_cap: 12
kernel: |
  A self-audit with open findings in a downstream-correctness category **blocks delivery**.
---

## Motivation

A self-audit that lists open findings without blocking the ship turns into a disclosure document — "we knew, we shipped anyway." For categories with downstream-correctness blast radius (model gateway, run lifecycle, HTTP contracts, security boundaries, resource lifetimes, observability), an open finding is not metadata; it is a stop-ship event. The rule replaces the implicit "ship + disclose" pattern with explicit "fix or defer with re-introduction trigger."

## Details

**Ship-blocking categories:**
- Model / LLM path (gateway, adapter, streaming, async lifetime, retry, rate-limit)
- Run lifecycle (stage, state machine, cancellation, resume, watchdog)
- HTTP / API contract (path, method, body, status, auth)
- Security boundary (path traversal, shell injection, auth bypass, tenant-scope escape)
- Resource lifetime (async clients, file handles, subprocesses, connection pools)
- Observability (missing metric, log, or health signal for a failure path)

**Forbidden:** any phrasing that ships with open ship-blocking findings.

## Cross-references

- Rule G-2 sub-clause .a (Architecture-Text Truth) — prose-enforcer claims without a real enforcer are a ship-blocking finding under Rule D-5.
- Rule R-C.a (Code-as-Contract) — per-sentence audit of architecture corpus is enforced via PR review under Rule D-5 (no automated sentence scanner exists today).
- Rule D-4 (Three-Layer Testing, With Honest Assertions) — the test-honesty predicate is the most common ship-blocking finding category.
