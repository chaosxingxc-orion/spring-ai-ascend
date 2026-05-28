---
level: L1
view: scenarios
status: shipped
authority: "ADR-0151 (L1 Feature Registry canonical schema) + ADR-0152 (uniform L1 + W3 catalog rendering)"
---

<!-- DO NOT HAND-EDIT. Rendered from architecture/features/features.dsl by gate/lib/render_features_catalog.py. Re-emit via that script; render-idempotency is enforced by Rule G-13.b. -->

# `graphmemory-starter` — L1 Feature Catalog (9-section)

This catalog is the **rendered** human-readable view of the
`graphmemory-starter`-owned features registered in
[`architecture/features/features.dsl`](../../../../features/features.dsl).
The structured source is the DSL; this Markdown is byte-identical
on re-emit. The 9 sections follow the user-supplied L1 Feature
Catalog template (ADR-0151).

## 1. Feature Metadata

| Feature ID | Name | Status | Capability Domain |
|---|---|---|---|
| `FEAT-GRAPH-MEMORY` | Graph Memory | `design_only` | `graph-memory` |

## 2. Architecture Binding

### `FEAT-GRAPH-MEMORY`

**Development paths:**
- `graphmemory-starter/src/main/java`

**Source ADR:** `ADR-0064`

## 3. Functional Decomposition

This module's features and their function-point membership are wired
by `contains` relationships in
[`architecture/features/features.dsl`](../../../../features/features.dsl).
Walk the workspace projection from each feature ID to traverse the
function-point inventory.

- `FEAT-GRAPH-MEMORY` contains the function points listed
  under `feat... -> fp... "contains"` relationships in features.dsl.

## 4. Contract Surface

Runtime promise surfaces touched by this module's features. For the
full catalog, see
[`docs/contracts/contract-catalog.md`](../../../../../docs/contracts/contract-catalog.md).

## 5. Runtime Behavior

### `FEAT-GRAPH-MEMORY`

Owns graph-shaped tenant memory: GraphMemoryRepository provides tenant-scoped CRUD over graph nodes and edges, with semantic-fact extraction for agent-side reasoning. The starter auto-wires the repository when configured; the storage backend is pluggable through GraphMemoryStore SPI. W1 ships the SPI surface as design_only; the production backend (vector index + relational graph) lands in a subsequent wave once the SPI is stable across two consumers.

## 6. DFX Requirements

DFX dimensions for `graphmemory-starter` are declared in
[`docs/dfx/spring-ai-ascend-graphmemory-starter.yaml`](../../../../../docs/dfx/spring-ai-ascend-graphmemory-starter.yaml).
Per-feature DFX deltas (if any) are tracked alongside the FEAT-
element in `architecture/features/features.dsl`.

## 7. AI Execution Boundary

Machine-readable AI boundary per feature (5 saa.aiBoundary.* sub-keys).
AI agents acting on this module MUST consult these before auto-modifying:

| Feature | Can modify code | Can modify contracts | Allowed transitions | Requires human review at | Sandbox policy |
|---|---|---|---|---|---|
| `FEAT-GRAPH-MEMORY` | `true` | `false` | `design_only->ready_for_impl, ready_for_impl->implemented_unverified, implemented_unverified->test_verified, test_verified->shipped` | `test_verified, shipped, deprecated` | `docs/governance/sandbox-policies.yaml#default_policy` |

## 8. Verification Matrix

Tests + commands that verify each feature. AI agents MUST run these
commands after auto-modifying the feature's owning code.

### `FEAT-GRAPH-MEMORY`

**Verification test FQNs:**
- `com.huawei.ascend.service.runtime.graphmemory.GraphMemoryAutoConfigurationTest`

**Verification commands:**
- `./mvnw -pl graphmemory-starter -am verify`

## 9. Lifecycle / Governance

Feature lifecycle state machine (Rule G-14):

```
proposed -> accepted -> design_only -> ready_for_impl
                                    -> implemented_unverified
                                    -> test_verified -> shipped
                                    -> deprecated -> removed
```

Current state per feature:

- `FEAT-GRAPH-MEMORY` — `design_only`

Status transitions are governed by Rule G-14 (advisory at W1, blocking
at W5 after soak). Forward-only by default; backward transitions
require an ADR `extends:` or `relates_to:` the feature's source ADR.

