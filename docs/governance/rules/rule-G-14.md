---
rule_id: G-14
title: "Feature Lifecycle Validity"
level: L0
view: scenarios
principle_ref: P-C
authority_refs: [ADR-0151]
enforcer_refs: [E175]
status: active_advisory
governance_infra: true
scope_phase: design
kernel_cap: 8
scope_surfaces:
  - architecture/features/features.dsl
  - architecture/profile/required-properties.yaml
  - architecture/profile/relationship-types.yaml
  - docs/adr/*.yaml
kernel: |
  **Every SAA Feature element in `architecture/features/features.dsl` MUST declare `saa.status` from the 9-state lifecycle (`proposed → accepted → design_only → ready_for_impl → implemented_unverified → test_verified → shipped → deprecated → removed`) (sub-clause .a). Per-feature status transitions in git history MUST follow the forward chain OR be paired with an ADR `extends:` or `relates_to:` the original feature ADR (sub-clause .b). Status `shipped` MUST have non-empty `saa.verificationTestFqns`; the pipe-separated FQNs MUST resolve to existing test classes (sub-clause .c). Status `deprecated` MUST carry `saa.sunsetDate` (ISO yyyy-MM-dd) AND a `relates_to:` ADR (sub-clause .d). Advisory at Wave 1 of the L1 Feature Registry plan; promoted to blocking at Wave 5 after 14-day soak. Companion saa.rel types `decided_by` (FEAT- → ADR) and `contained_by` (FEAT- → CAP-) make AI traversal one-hop instead of string-matching.**
---

# Rule G-14 — Feature Lifecycle Validity

## What

Constrains the lifecycle metadata of every `SAA Feature` element in
`architecture/features/features.dsl` so the L1 Feature Registry remains
machine-readable and AI-traversable. The four sub-clauses enforce
state-set membership, forward-only transition discipline, shipped-state
verification rigor, and deprecation paperwork.

## Why

Without lifecycle constraints, features drift through ad-hoc statuses
("WIP", "soon", "broken"), backward transitions land silently in
git history, shipped features accumulate without verifying tests, and
deprecations have no sunset commitment. The 9-state machine specified
by the user 2026-05-27 (and accepted in ADR-0151) standardizes the
vocabulary; Rule G-14 enforces it.

The forward-only discipline matters for AI agents: an agent reading
`saa.aiBoundary.allowedStatusTransitions` on a feature can act safely
only if the transition vocabulary is known and the gate enforces it.

## Sub-clauses

### .a — Status set membership

Every FEAT- element MUST carry `saa.status` whose value is exactly
one of:

    proposed | accepted | design_only | ready_for_impl
    implemented_unverified | test_verified | shipped
    deprecated | removed

Any other value (or missing property) fails the rule. The advisory
mode at W1 logs `ADVISORY: rule_G_14_a_status_set_membership: <feature> = <status>`.

### .b — Forward-only transitions

For every FEAT- element, git history of
`architecture/features/features.dsl` is parsed by grepping the
`features.dsl` blob at each commit for `saa.status "<state>"`. The
per-feature sequence of states MUST follow the forward chain:

    proposed -> accepted -> design_only -> ready_for_impl ->
    implemented_unverified -> test_verified -> shipped ->
    deprecated -> removed

Skipping forward is allowed (e.g., `design_only -> shipped` when a
feature lands in a single commit with backing tests). Backward
transitions (e.g., `shipped -> ready_for_impl`) require an ADR whose
yaml contains `extends:` or `relates_to:` pointing at the feature's
`saa.sourceAdr`. The advisory mode logs the violating commit hash +
the cited (or missing) ADR.

### .c — Shipped requires verification

If `saa.status` == `shipped`, then `saa.verificationTestFqns` MUST be
non-empty (pipe-separated FQNs) AND each FQN MUST resolve to a
`.java` test class under the owning module's `src/test/java/`. The
gate parses the FQN, walks the corresponding file, and reports each
missing FQN.

### .d — Deprecated requires sunset

If `saa.status` == `deprecated`, then both:

  - `saa.sunsetDate` exists with ISO `yyyy-MM-dd` value parsing,
    AND
  - `saa.sourceAdr` (or a sibling ADR via `relates_to:`) carries a
    `consequences:` body mentioning the deprecation.

## Authority chain

  - ADR-0151 declares the 9-state machine + AI Execution Boundary
    schema + introduces this rule as advisory.
  - Wave 5 of the L1 Feature Registry plan (ADR-0153 closure) promotes
    Rule G-14 from advisory to blocking after a 14-day wall-clock soak
    window with zero unexpected violations on agent-service.
  - Future widening may add per-feature ADR DAG verification (the
    deprecation ADR's `relates_to:` chain must reach the original
    feature ADR).

## Test fixtures

Gate self-tests assert:

  - VALID  : a feature transitions `design_only -> shipped` with
             non-empty verificationTestFqns + resolving FQNs.
  - INVALID: a feature carries `saa.status "WIP"` (illegal value).
  - INVALID: a feature transitions `shipped -> ready_for_impl` without
             a citing ADR.
  - INVALID: a feature carries `saa.status "shipped"` with empty
             saa.verificationTestFqns.
  - INVALID: a feature carries `saa.status "deprecated"` without
             saa.sunsetDate.

## Cross-references

  - ADR-0151 — L1 Feature Registry canonical schema (Wave 1)
  - architecture/features/features.dsl — authored FEAT- inventory
  - architecture/profile/required-properties.yaml#SAA_Feature
  - architecture/profile/relationship-types.yaml (decided_by, contained_by)
