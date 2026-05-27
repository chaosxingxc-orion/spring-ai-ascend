---
level: L0
view: scenarios
status: shipped
authority: "ADR-0151 (L1 Feature Registry W1 Foundation)"
---

# 2026-05-27 — L1 Feature Registry W1 Foundation Shipped

**Branch:** `w1/l1-feature-registry-foundation`
**PR:** TBD
**Plan:** `D:\.claude\plans\structurizr-d-chao-workspace-spring-ai-adaptive-hellman.md` (L1 Feature Registry & Uniform Module Mechanism Plan).

## 0. Canonical baselines (per Gate Rule 28)

| Metric | Value |
|---|---|
| §4 constraints | 65 |
| ADRs | 136 |
| gate rules | 143 |
| self-test cases | 260 |
| Layer-0 governing principles | 13 |
| active engineering rules | 44 |
| enforcer rows | 176 |
| Maven XML-counted tests | 461 |
| architecture graph nodes | 627 |
| architecture graph edges | 1203 |
| recurring defect families | 34 |
| workspace elements | 566 |
| workspace relationships | 413 |
| feature corpus size | 9 |

These match `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`.

**Canonical baseline phrasing** (Gate Rule 28 grep):
65 §4 constraints · 136 ADRs · 143 active gate rules · 260 gate self-tests ·
13 Layer-0 governing principles · 44 active engineering rules · 176 enforcer rows ·
461 Maven XML-counted tests · 627 architecture graph nodes / 1203 edges ·
34 recurring defect families.

## 1. What W1 shipped

The L1 Feature Registry foundation lands the structured Feature
layer between Capability (CAP-) and Function Point (FP-) in the
already-shipped Structurizr workspace. The registry is the
AI-readable surface the user asked for: every L1 feature carries a
machine-readable AI Execution Boundary + a 9-state lifecycle so AI
agents traversing `architecture/workspace.dsl` can identify what's
safe to auto-modify and what needs human review.

### 1.1 New DSL fragment — `architecture/features/features.dsl`

Seed inventory: 9 FEAT- elements covering the most-cited L1
features today. Each FEAT- element carries:

  - `saa.id` (FEAT-<SCREAMING-KEBAB>)
  - `saa.synopsis` (100-200 word RAG-friendly description)
  - `saa.capabilityDomain` (cross-link to capabilities.dsl by saa.id)
  - 5 `saa.aiBoundary.*` sub-properties (canModifyCode,
    canModifyContracts, allowedStatusTransitions,
    requiresHumanReviewAt, sandboxPolicyRef)
  - `saa.status` (one of the 9-state lifecycle)
  - Optional: saa.devPaths, saa.goals, saa.nonGoals,
    saa.verificationTestFqns, saa.verificationCommands

Pipe `|` is the list separator for multi-value properties (DSL
properties are scalar→scalar). The seed inventory:

| Feature | Owner | Status | Capability Domain |
|---|---|---|---|
| FEAT-RUN-LIFECYCLE-CONTROL | agent-service | shipped | runtime-run-lifecycle |
| FEAT-EDGE-COMPUTE-INGRESS | agent-bus | design_only | edge-compute-routing |
| FEAT-SERVER-CLIENT-CALLBACK | agent-bus | shipped | s2c-callback-protocol |
| FEAT-SUSPEND-RESUME-CONTROL | agent-service | shipped | run-suspension-orchestration |
| FEAT-IDEMPOTENCY-AND-REPLAY | agent-service | shipped | idempotency-protocol |
| FEAT-TENANT-ISOLATION | agent-service | shipped | tenant-isolation |
| FEAT-POSTURE-BOOTSTRAP | agent-service | shipped | posture-bootstrap |
| FEAT-GRAPH-MEMORY | graphmemory-starter | design_only | graph-memory |
| FEAT-ENGINE-DISPATCH-AND-HOOKS | agent-service | shipped | engine-contract |

Each feature has a `contains` relationship to its function points
(15 contains edges total).

### 1.2 Profile extension — `architecture/profile/`

`required-properties.yaml#by_tag.SAA_Feature` extends from 2 keys
(saa.owner + saa.sourceAdr) to 9 keys: + saa.capabilityDomain +
saa.synopsis + 5 saa.aiBoundary.* sub-keys.

`relationship-types.yaml` adds 2 new types:

  - `decided_by` (FEAT-/FP-/CAP-/Contract → ADR; inverse of `decides`)
  - `contained_by` (FEAT- → CAP-; inverse of `contains` cap→feature)

Both make AI traversal one-hop from any FEAT- to its decision
authority or containing capability.

### 1.3 Java ProfileValidator + parity test

`ProfileValidator.TAG_SPECIFIC["SAA Feature"]` mirrors the YAML
extension (9 required keys). `ProfileValidator.RELATIONSHIP_TYPES`
adds the 2 new types. `ProfileYamlParityTest` asserts Java ↔ YAML
parity; the 3 parity tests + 5 validator unit tests + 2
NormalizedModelWriter tests all PASS (10/10).

### 1.4 Rule G-14 advisory — `docs/governance/rules/rule-G-14.md`

Feature Lifecycle Validity. Four sub-clauses (see card body).
Advisory at W1; promoted to blocking at W5 after a 14-day soak.

### 1.5 ADR-0151

`docs/adr/0151-l1-feature-registry-canonical-schema.yaml` with
`extends: [ADR-0147, ADR-0149, ADR-0150]`. AdrMirrorCli mirrors
the 7 anchor ADRs (0068, 0119, 0147, 0148, 0149, 0150, 0151) to
`architecture/decisions/` so Structurizr's `!adrs decisions`
directive resolves.

### 1.6 Workspace `!include` order

`architecture/workspace.dsl` adds:

```structurizr
!include features/capabilities.dsl
!include features/function-points.dsl
!include features/features.dsl       # NEW W1 — features.dsl references fp* identifiers from function-points.dsl
!include features/verification.dsl
```

Order matters: features.dsl references function-point identifiers
defined in function-points.dsl.

## 2. Workspace counts

Pre-W1 (W8 baseline): 557 elements + 398 relationships.
Post-W1: 566 elements (+9 SAA Feature) + 413 relationships (+15 contains edges).

## 3. AI consumption — 7 query types resolvable

The L1 Feature Registry is the AI-readable surface the user asked
for. An AI session loading workspace.dsl can answer these 7 queries
in one traversal:

  1. List all L1 features for `<module>` — filter SAA Feature by saa.owner.
  2. What's the AI Execution Boundary for FEAT-X — read 5 saa.aiBoundary.* properties.
  3. What ADRs justify FEAT-X — traverse feature → ADR edges (saa.rel = decided_by).
  4. Which tests verify FEAT-X — read saa.verificationTestFqns + traverse feature → test edges (saa.rel = verifies).
  5. What's the synopsis of FEAT-X — read saa.synopsis (RAG-friendly).
  6. Which features are `shipped` — filter SAA Feature by saa.status.
  7. Is auto-transition allowed — read saa.aiBoundary.allowedStatusTransitions; if not, require human review at one of saa.aiBoundary.requiresHumanReviewAt states.

## 4. Authority chain

  - ADR-0147 — Structurizr Workspace Authority (W0)
  - ADR-0149 — W0-W5 closure
  - ADR-0150 — W8 docs consolidation
  - ADR-0151 — L1 Feature Registry canonical schema (this wave)
  - Rule G-14 advisory; promoted to blocking at W5

## 5. Tests / Gates

  - `./mvnw -f tools/architecture-workspace/pom.xml clean test` — 10/10 PASS
  - `bash gate/check_architecture_workspace.sh` (BLOCKING) — PASS
  - `python3 gate/lib/check_template_render_idempotency.py` — PASS
  - `python3 gate/lib/check_workspace_fragment_idempotency.py` — PASS (7 fragments byte-identical)
  - `python3 gate/lib/check_doc_coherence.py` — PASS
  - `bash gate/check_architecture_sync.sh` + `bash gate/test_architecture_sync_gate.sh` — PASS
  - CI `Maven build + integration tests` + `Quickstart smoke (/v1/health)` — to land on the W1 PR.

## 6. Forward (W2..W5)

Per the plan:

  - W2 — Uniform L1 directory + L0 mounting + `_template/`
  - W3 — 9-section catalog render (Jinja2) + 3 missing L1 view DSL fragments + meta-template
  - W4 — Migrate agent-service `features/` to new schema + 14-day soak
  - W5 — Rule G-14 advisory → blocking + closure ADR-0153

## 7. Cross-references

  - ADR-0151 — L1 Feature Registry canonical schema (Wave 1)
  - `architecture/features/features.dsl` — authored FEAT- inventory
  - `architecture/profile/required-properties.yaml#SAA_Feature`
  - `architecture/profile/relationship-types.yaml` (decided_by, contained_by)
  - `docs/governance/rules/rule-G-14.md` — feature lifecycle validity
  - `D:\.claude\plans\structurizr-d-chao-workspace-spring-ai-adaptive-hellman.md` — full plan
