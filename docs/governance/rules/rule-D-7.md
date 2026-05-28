---
rule_id: D-7
title: "Concurrency / Async Resource Lifetime"
level: L1
view: development
principle_ref: P-A
authority_refs: []
enforcer_refs: []
status: active
product_claim: "PC-003"
scope_phase: design
kernel_cap: 8
kernel: |
  **Every async or reactive resource has a lifetime bound to exactly one execution context.**
---

## Motivation

Async clients, reactive sessions, and event-loop-bound handles silently break when shared across executors or wrapped in sync façades that spin a fresh loop per call. The failure mode is non-deterministic — the resource appears to work, then deadlocks or returns stale data under contention. The cure is structural: every such resource has exactly one owning execution context, and the construction strategy is chosen explicitly at design time rather than emerging accidentally from convenience helpers.

## Details

**Forbidden patterns:**
1. Constructing an async/reactive resource in a constructor of a sync-facing class, then driving it via per-call `asyncio.run` / `block()` / `Mono.block()`.
2. Sharing one client/session across two independent event loops or schedulers.
3. Wrapping an async library with a sync façade that spins a fresh event loop or scheduler per method.

**Required patterns** — pick one: async-native (use under owning context), sync bridge (single durable bridge on dedicated thread), or per-call construction (cheap resources only).

## Cross-references

- Rule D-8 (Single Construction Path Per Resource Class) — single construction path is the prerequisite for an enforceable lifetime.
- Rule R-G (Reactive External I/O) — runtime external I/O MUST be reactive or virtual-thread-backed; Rule D-7 governs HOW that resource is owned.
- P-G (Absolute Non-Blocking I/O) — the governing principle Rule D-7 partially operationalises at the resource-lifetime layer.
