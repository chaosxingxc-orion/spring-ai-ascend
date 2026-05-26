---
level: L0
view: scenarios
status: active
authority: "ADR-0068 (Layered 4+1 + Architecture Graph)"
---

# Session-Start Architectural Context

This is the canonical entry-point for any human contributor or LLM agent starting a new working session on `spring-ai-ascend`. Read this first. It contains the graph map; details live in the linked artefacts.

## TL;DR

The architecture lives in **two coupled forms** (per ADR-0068 + ADR-0147):

1. **Layered 4+1 corpus (human-facing)** — prose at three levels (L0 / L1 / L2), each organised by five views (logical / development / process / physical / scenarios).
2. **Architecture workspace closure (machine-facing; W5+)** — `architecture/workspace.dsl` and its closure (`architecture/profile/`, `features/`, `docs/L1/`, `decisions/`, `generated/`, `views/`). Authored capabilities + function points live under `features/`; modules, SPI catalog, enforcers, principles, rules, ADR graph are emitted under `generated/` from existing YAML authorities. Compatibility projection at `docs/governance/architecture-workspace-graph.yaml`.

**Do not read the 75+ ADRs sequentially.** Start with the workspace closure (`architecture/workspace.dsl` + `architecture/features/function-points.dsl`); drill into the prose only after you know which edge you are traversing.

## Reading order

| Step | Open | Load | Purpose |
|---|---|---|---|
| 1 | `CLAUDE.md` | ALWAYS-LOAD | Rule kernels (one paragraph each) + Layer-0 principle index. Full rule bodies live in per-rule cards (see step 1a). |
| 1a | `docs/governance/rules/rule-NN.md` + `docs/governance/principles/P-X.md` | ON-DEMAND | Expanded body for the specific rule / principle you are touching. Loaded only when needed. |
| 2 | `architecture/workspace.dsl` + `architecture/README.md` | ALWAYS-LOAD | **W5+** architecture authoring root. Workspace closure (profile/features/docs/decisions/generated/views) is the new machine-facing entry point per ADR-0147. |
| 2a | `architecture/features/function-points.dsl` + `architecture/features/capabilities.dsl` | ON-DEMAND | L1 feature inventory: which function points exist, who owns them, which ADR decided them, which tests verify them. |
| 3 | `ARCHITECTURE.md` §0.4 | ALWAYS-LOAD | Layered 4+1 view map of root-level sections |
| 4 | `docs/governance/architecture-workspace-graph.yaml` | ALWAYS-LOAD | Workspace-projection graph (W4+; primary projection going forward). |
| 5 | `docs/governance/architecture-graph.yaml` | ALWAYS-LOAD (legacy; retires at W7) | Legacy graph projection — still consulted by some gate rules until W6 yaml sunset; defence-in-depth only. |
| 6 | `docs/governance/architecture-graph.mmd` | OPTIONAL | Mermaid render of the legacy graph spine |
| 7 | `docs/governance/enforcers.yaml` | ALWAYS-LOAD (legacy; W6 sunset target) | Rows mapping constraints to enforcers. Workspace mirror at `architecture/generated/enforcers.dsl` is the W5+ source. |
| 8 | `docs/governance/architecture-status.yaml` | ALWAYS-LOAD | Capability ledger (what is shipped / verified). The `#capabilities` section's authority moves to `architecture/features/capabilities.dsl` at W6 sunset; baseline_metrics remains. |
| 9 | `docs/CLAUDE-deferred.md` | (ON-DEMAND) | Rules deferred to W1/W2/W3/W4 with re-introduction triggers — load only when re-introducing a deferred rule |
| 10 | the ADR YAML referenced by the edge you are traversing | ON-DEMAND | rationale and `extends:` / `relates_to:` |
| 11 | `docs/runbooks/debug-first-evidence.md` | ON-DEMAND (Rule 79) | Evidence-First Debug Sequence — open when a Run fails, a test regresses, or a self-audit finding is being drafted. Required by Rule 79. |

The always-loaded budget per file is declared in [`gate/always-loaded-budget.txt`](../../gate/always-loaded-budget.txt) and policed by Gate Rule 70 (`always_loaded_budget_enforced`). To measure the current state: `bash gate/measure_always_loaded_tokens.sh`.

## Graph traversal cheatsheet

To answer "which test ultimately enforces principle X?":

```
principle X
  --(operationalised_by)--> Rule-N           # principle → rule
  --(enforced_by)--> E<n>                    # rule → enforcer
  --(asserts_in)--> file:<path>#<anchor>     # enforcer → test/artefact
```

To answer "what does this test verify?":

```
file:<test-path>
  ←(asserts_in)-- E<n>                       # invert: artefact → enforcer
  ←(enforced_by)-- Rule-N                    # invert: enforcer → rule
  ←(operationalised_by)-- principle          # invert: rule → principle
```

To answer "what depends on / forbids importing module M?":

```
module:M
  --(may_depend_on)--> module:<allowed>
  --(must_not_depend_on)--> module:<forbidden>
```

To answer "which ADR superseded ADR-N?":

```
?
  --(supersedes)--> ADR-N
```

(Query the graph; `supersedes` and `extends` sub-graphs are DAGs validated by Gate Rule 38.)

## Editing rules in a session

Before editing any architectural artefact:

1. **Read the front-matter.** Every architectural file declares `level:` + `view:`. Edits change semantics; declare the level/view your change applies to.
2. **Write a `docs/logs/reviews/` proposal first** if the artefact is L0 or L1 and is frozen (`freeze_id:` is set). Use `docs/logs/reviews/_TEMPLATE.md`.
3. **Update the graph inputs, never the graph file.** Edit `enforcers.yaml`, `principle-coverage.yaml`, ADR YAML, or `module-metadata.yaml`. Then run `bash gate/build_architecture_graph.sh` to regenerate the graph. Rule 34 forbids hand-editing the graph.
4. **Run the gate.** `bash gate/check_architecture_sync.sh` exits 0. New Rule 33–34 gate rules (37–40) catch missing front-matter, broken edges, orphaned enforcers, and missing review-proposal tags.

## What is *not* a session-start input

These are part of the corpus but should NOT be read at session start:

- Individual ADRs unless an edge in the graph points at one.
- Archived plans under `docs/archive/`.
- Historical review files under `docs/logs/reviews/2026-05-1[23]-*.md` (they are frozen evidence, not active guidance).
- `docs/CLAUDE-deferred.md` unless you are about to land a deferred rule.

## Mental model

> "The graph is the city plan. Prose ADRs are the deeds for individual lots. Read the map before you visit a property."

Authority: CLAUDE.md Rule 33 + Rule 34, ADR-0068.
