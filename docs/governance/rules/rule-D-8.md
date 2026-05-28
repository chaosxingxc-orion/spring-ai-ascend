---
rule_id: D-8
title: "Single Construction Path Per Resource Class"
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
  **For every shared-state resource, exactly one builder/factory owns construction. All consumers receive the instance by dependency injection.**
---

## Motivation

Inline-fallback construction (`x or DefaultX()`) is the dominant source of shadow singletons: two consumers each "fall back" to a default and silently operate on different instances of what was supposed to be shared state. The cure is a single construction site per resource class — consumers receive it by injection, and missing required scope (tenant, posture) is a hard error rather than a silent default.

## Details

Inline fallbacks of the shape `x or DefaultX()` / `x != null ? x : new DefaultX()` are forbidden.

When a class needs tenant scoping, scope is a **required constructor argument**. Missing scope must be a hard error.

## Cross-references

- Rule D-7 (Concurrency / Async Resource Lifetime) — single construction is the prerequisite for an enforceable lifetime.
- Rule R-C.2 sub-clause .c (Tenant Propagation Purity; was Rule R-C.e pre-rc17 per ADR-0094) — tenant scope as a required constructor argument is the structural enforcement that prevents request-scoped ThreadLocal leakage into runtime code.
