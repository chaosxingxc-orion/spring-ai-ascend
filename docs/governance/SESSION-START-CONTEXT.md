---
level: L0
view: scenarios
status: active
authority: "ADR-0068 (Layered 4+1 + Architecture Graph)"
---

# Session-Start Architectural Context

This is the canonical entry-point for any human contributor or LLM agent starting a new working session on `spring-ai-ascend`. Read this first. It contains the graph map; details live in the linked artefacts.

## TL;DR

The architecture lives in **two coupled forms** (per ADR-0068 + ADR-0147 + ADR-0150):

1. **Architecture workspace closure** (machine-readable; W5+ blocking, W8 consolidated) — `architecture/workspace.dsl` is the sole main entry. The closure carries `architecture/docs/L1/` (human-readable narrative + 4+1 views for agent-service), `architecture/features/` (capability + function-point DSL), `architecture/decisions/` (ADR markdown imports via `!adrs`), `architecture/generated/` (7 emitted fragments from authoritative YAMLs), `architecture/profile/` (SAA tag/relationship/property schema), `architecture/views/` (4+1 view DSL).
2. **Operational + governance authorities** (`docs/`) — `docs/governance/` (rules + principles + status + enforcers + templates), `docs/adr/` (ADR corpus), `docs/contracts/` (runtime promises), `docs/logs/` (releases + reviews), `docs/quickstart.md` + `docs/overview.md` (onboarding). These are NOT architecture-design content — they're operational authorities that surround the workspace.

**Do not read the 75+ ADRs sequentially.** Start at `architecture/workspace.dsl` + `architecture/README.md`; drill into the prose only after you know which edge you are traversing.

## Reading order

This table matches `README.md#Reading-path` step-for-step. Loading these in order produces a complete, unbiased architecture picture.

| Step | Open | Load | Purpose |
|---|---|---|---|
| 1 | `architecture/workspace.dsl` + `architecture/README.md` | ALWAYS-LOAD | Architecture authority root (sole main entry per ADR-0147 + ADR-0150). Structurizr DSL workspace + closure navigation. |
| 1a | `architecture/features/function-points.dsl` + `architecture/features/capabilities.dsl` | ON-DEMAND | L1 feature inventory: which function points exist, who owns them, which ADR decided them, which tests verify them. |
| 2 | `architecture/docs/L0/ARCHITECTURE.md` (root, L0) | ALWAYS-LOAD | Declarative L0 system boundary + 65 §4 architectural constraints. §0.6 declares rhetorical stance; §0.7 lists Constraint↔Rule pairs. |
| 3 | `CLAUDE.md` | ALWAYS-LOAD | Enforceable rule kernel index. `## Rhetorical stance` block + `## Constraint ↔ Rule mapping` table. Full rule bodies under `docs/governance/rules/*.md` (load on demand). |
| 3a | `docs/governance/rules/rule-<id>.md` + `docs/governance/principles/P-<X>.md` | ON-DEMAND | Expanded body for the specific rule / principle being touched. |
| 4 | `architecture/docs/L1/README.md` + `architecture/docs/L1/<module>{.md,/}` (for the module you touch) | ALWAYS-LOAD | L1 module design. agent-service uses the per-view directory shape (5 4+1 view files + spi-appendix + features/ + architecture/docs/L0/ARCHITECTURE.md); other modules use the single-narrative `<module>.md` shape. |
| 5 | `docs/contracts/contract-catalog.md` | ALWAYS-LOAD | Runtime promise surface. Each contract names its authority ADR + enforcer + workspace `SAA Contract` element. |
| 6 | `docs/quickstart.md` | OPTIONAL | Operational onboarding — boot + first-run walkthrough. |
| 7 | `docs/overview.md` | OPTIONAL | Narrative tour for non-architecture readers. |
| 8 | `docs/governance/architecture-status.yaml` | ALWAYS-LOAD | Per-capability shipped/deferred ledger + canonical `allowed_claim` baseline. `#capabilities` authority migrates to `architecture/features/capabilities.dsl` at W6.c sunset. |
| 9 | `docs/governance/architecture-workspace-graph.yaml` | ALWAYS-LOAD | Workspace-projection graph (W4+; primary projection going forward). |
| 10 | `docs/governance/architecture-graph.yaml` | ALWAYS-LOAD (legacy; retires at W7) | Legacy graph projection — kept as defence-in-depth until W7 retirement. |
| 11 | `docs/governance/enforcers.yaml` | ALWAYS-LOAD (legacy; W6.a sunset target) | Rows mapping constraints to enforcers. Workspace mirror at `architecture/generated/enforcers.dsl` is the W5+ source. |
| 12 | the ADR YAML referenced by the edge you are traversing | ON-DEMAND | rationale and `extends:` / `relates_to:`. Anchor ADRs (0068, 0119, 0147, 0148, 0149, 0150) are mirrored under `architecture/decisions/` for Structurizr `!adrs` import. |
| 13 | `docs/CLAUDE-deferred.md` | (ON-DEMAND) | Rules deferred to W1/W2/W3/W4 with re-introduction triggers — load only when re-introducing a deferred rule. |
| 14 | `docs/runbooks/debug-first-evidence.md` | ON-DEMAND (Rule 79) | Evidence-First Debug Sequence — open when a Run fails, a test regresses, or a self-audit finding is being drafted. |

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
