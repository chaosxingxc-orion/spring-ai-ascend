---
rule_id: D-4
title: "Three-Layer Testing, With Honest Assertions"
level: L1
view: process
principle_ref: P-A
authority_refs: []
enforcer_refs: []
status: active
governance_infra: true
scope_phase: impl
kernel_cap: 12
kernel: |
  A feature is implementable only when all three layers are designed. A feature is shippable only when all three are green and Rule D-5 passes.
---

## Motivation

A feature backed only by unit tests passes on a green CI bar while the system that integrates it remains untested. The three layers exist because each catches failure modes the others cannot: unit tests catch logic defects, integration tests catch wiring defects, E2E tests catch contract defects. Honest assertions matter as much as layered coverage — mocking the subsystem under test in integration mislabels a unit test as integration coverage and produces a false sense of safety.

## Details

- **Layer 1 — Unit**: one function/method per test; mock only external network or fault injection.
- **Layer 2 — Integration**: real components wired together. **Zero mocks on the subsystem under test.** Skip with the test framework's skip annotation if a dependency is absent — never fake it.
- **Layer 3 — E2E**: drive through the public interface (HTTP / CLI / top-level API); assert on observable outputs, not internal variables.

**Test honesty is not optional**: mocking the subsystem under test in integration = mislabeled unit test.

## Cross-references

- Rule D-5 (Self-Audit is a Ship Gate, Not a Disclosure) — the ship-gate predicate names Rule D-4 explicitly.
- Rule D-3.a (Pre-Commit Checklist) — the "Test honesty" dimension reinforces the no-mock-on-subsystem clause at every commit.
