---
level: L1
view: scenarios
status: shipped
authority: "ADR-0151 (L1 Feature Registry canonical schema)"
---

# `architecture/features/` — Feature Authoring Template

This directory is the **canonical authored source** for the L1 feature inventory.
The Markdown 9-section catalog at `architecture/docs/L1/<module>/features/README.md`
is **rendered** from these DSL fragments by
[`gate/lib/render_features_catalog.py`](../../gate/lib/render_features_catalog.py).
DSL is the structured source; Markdown is the prose surface; render-idempotency
(Rule G-13) enforces zero drift.

## What lives here

| File | Role | Authored or generated |
|---|---|---|
| `capabilities.dsl` | SAA Capability inventory (152 entries) — coarse-grained ownership units | AUTHORED |
| `function-points.dsl` | SAA FunctionPoint inventory (15 entries) — concrete API verbs / workflow steps | AUTHORED |
| `features.dsl` | SAA Feature inventory — the **value / demand axis** (value-threads), with full AI Execution Boundary + 9-state lifecycle. NOT the structural middle layer: the structural axis is `SAA Module → SAA EngineeringFrame → SAA FunctionPoint` (ADR-0157, see `engineering-frames.dsl`). A Feature drives FunctionPoints and traverses EngineeringFrames; it does not structurally own them. | AUTHORED |
| `engineering-frames.dsl` | SAA EngineeringFrame inventory — the **structural axis** (durable module-internal responsibility slices that anchor function points), per ADR-0157 | AUTHORED |
| `verification.dsl` | SAA Test inventory + `verifies` relationships | AUTHORED |

## The `saa.*` property schema

Every SAA-tagged element MUST satisfy
[`architecture/profile/required-properties.yaml`](../profile/required-properties.yaml).
Common keys (every tag): `saa.id`, `saa.kind`, `saa.level`, `saa.view`,
`saa.status`. Per-tag additions:

### `SAA Feature` (this directory's anchor tag)

Required (9 keys beyond common):
- `saa.owner` — owning module short name (matches a SAA Module saa.id)
- `saa.sourceAdr` — authority ADR (e.g., `ADR-0020`)
- `saa.capabilityDomain` — capability domain string; cross-checks against capabilities.dsl
- `saa.synopsis` — 100-200 word RAG-friendly description (the AI-readable summary)
- `saa.aiBoundary.canModifyCode` — `"true"` | `"false"`
- `saa.aiBoundary.canModifyContracts` — `"true"` | `"false"`
- `saa.aiBoundary.allowedStatusTransitions` — pipe-separated transitions
- `saa.aiBoundary.requiresHumanReviewAt` — pipe-separated lifecycle states
- `saa.aiBoundary.sandboxPolicyRef` — path#anchor (e.g., `docs/governance/sandbox-policies.yaml#default_policy`)

Optional (5 keys): `saa.devPaths`, `saa.goals`, `saa.nonGoals`,
`saa.verificationTestFqns`, `saa.verificationCommands`.

Pipe (`|`) is the list separator (DSL `properties` are scalar→scalar; multi-value
properties encode lists via the pipe delimiter).

> **Sunset notice (ADR-0154 / Rule G-15, Fact-Layer W1, 2026-05-27):** the
> three factual fields `saa.devPaths`, `saa.verificationTestFqns`, and
> `saa.verificationCommands` are classified as
> `factual_hand_authored_grandfathered` under
> [`architecture/profile/saa-property-authority.yaml`](../profile/saa-property-authority.yaml)
> with `sunset_date: 2026-07-31`. Wave 4 of the Fact-Layer plan lands the
> Java extractors (CodeSymbolFactExtractor via ASM + JavaParser overlay
> and TestInventoryFactExtractor); Wave 5 lands the thicker
> FunctionPoint schema with `code_entrypoint_refs[]` / `test_refs[]` /
> contract refs; Wave 6 retires the hand-authored fields. Until then,
> values remain hand-authored at FEAT- level; treat them as advisory and
> prefer FunctionPoint cross-fact resolution where available.

## The `saa.rel` relationship vocabulary

See [`architecture/profile/relationship-types.yaml`](../profile/relationship-types.yaml)
for the canonical matrix. Key relationships for feature authoring:

| `saa.rel` | Source | Target | Semantic |
|---|---|---|---|
| `contains` | SAA Capability / SAA Feature | SAA Feature / SAA FunctionPoint | Hierarchical ownership |
| `contained_by` | SAA Feature | SAA Capability | Inverse of `contains` (one-hop AI traversal) |
| `implements` | SAA Module | SAA Feature / SAA FunctionPoint / SAA Contract | Module realises a feature |
| `verifies` | SAA Enforcer / SAA Test | SAA Rule / SAA Feature / SAA FunctionPoint / SAA Contract | Test verifies a feature |
| `decided_by` | SAA Feature / SAA FunctionPoint / SAA Capability / SAA Contract | SAA ADR | Authority for the feature's existence |

## The 9-state feature lifecycle

```
proposed -> accepted -> design_only -> ready_for_impl
                                    -> implemented_unverified
                                    -> test_verified -> shipped
                                    -> deprecated -> removed
```

Forward-only by default; backward transitions require an ADR `extends:` or
`relates_to:` the feature's source ADR. Enforced by Rule G-14 (advisory at W1;
blocking at W5 after soak).

## Recipe — add a new capability

1. Open `architecture/features/capabilities.dsl`.
2. Add an `element "Name" "Capability" "Description" "SAA Capability"` block with required `saa.*` properties.
3. Run `./mvnw -f tools/architecture-workspace/pom.xml exec:java -Dexec.args="validate architecture/workspace.dsl"` — profile validator reports 0 violations.
4. Re-emit the legacy graph: `python3 gate/build_architecture_graph.py`.
5. Commit; the new capability appears in the workspace projection.

## Recipe — add a new feature

1. Open `architecture/features/features.dsl`.
2. Add a `featXxx = element "Name" "Feature" "Description" "SAA Feature"` block with all 13 required + relevant optional `saa.*` properties (see schema above).
3. Add `contains` relationships from this feature to its function points: `featXxx -> fpYyy "..." "SAA Relationship"` block with multi-line `properties { "saa.rel" "contains" }`.
4. Optionally add `decided_by` to the authority ADR + `contained_by` to the parent capability.
5. Validate the workspace.
6. Re-render the per-module catalog: `python3 gate/lib/render_features_catalog.py --module <module>`.
7. Commit; the catalog at `architecture/docs/L1/<module>/features/README.md` updates byte-identically.

## Recipe — add a new verification test edge

1. Open `architecture/features/verification.dsl`.
2. Add a `testXxx = element "..." "Test" "..." "SAA Test"` block with `saa.sourceFile` pointing at the `.java` test class.
3. Add `testXxx -> featYyy "verifies ..." "SAA Relationship"` block with multi-line `properties { "saa.rel" "verifies" }`.
4. Validate the workspace.
5. Commit.

## How modules consume this

Each `kind: domain` module gets a rendered `features/README.md` under its
canonical L1 directory:

```
architecture/docs/L1/<module>/features/README.md
```

The catalog is rendered by `gate/lib/render_features_catalog.py`, filtering
SAA Feature elements by `saa.owner == <module>`. Render-idempotency (Rule G-13)
enforces byte-identical output on re-emit; hand-editing the catalog is forbidden
(the gate detects drift).

## Authority

- ADR-0147 — Structurizr Workspace Authority
- ADR-0151 — L1 Feature Registry canonical schema (this directory's anchor)
- ADR-0152 — Uniform L1 mechanism + L0 mounting + W3 catalog rendering
- Rule G-14 — Feature Lifecycle Validity (advisory at W1; blocking at W5)
- `architecture/profile/required-properties.yaml#SAA_Feature` — schema
- `architecture/profile/relationship-types.yaml` — saa.rel vocabulary
- `gate/lib/render_features_catalog.py` — renderer
