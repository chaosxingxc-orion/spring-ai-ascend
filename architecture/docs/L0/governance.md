---
level: L0
view: governance
status: draft
authority: "Consolidated from ADR-0068, ADR-0147, ADR-0150, docs/architecture/TRIAGE.md, and docs/architecture/l0 governance drafts"
source_of_truth: true
---

# L0 Governance

## Purpose

This document defines how L0 architecture facts are governed, how draft material
is promoted, how L0/L1/L2 updates are sequenced, and which conflicts or gaps
remain open after the initial L0 consolidation.

## Architecture Authority

The machine-readable architecture authority is:

- `architecture/workspace.dsl`.
- Its closure under `architecture/profile/`, `architecture/features/`,
  `architecture/docs/`, `architecture/decisions/`, `architecture/generated/`,
  and `architecture/views/`.

The human-readable L0 package in this directory is a curated projection over
that authority plus promoted content from draft material.

## Authored and Generated Zones

| Zone | Rule |
|---|---|
| Authored architecture docs | Edited by humans or AI agents under architecture governance. |
| Generated fragments | Emitted by architecture tooling and not hand-edited. |
| Draft docs under `docs/architecture/` | Proposal material only until promoted. |
| Contract/interface docs | Not owned by L0; accepted contracts live in the contract system. |

## Promotion States

| State | Meaning |
|---|---|
| `pending_triage` | Needs comparison against architecture authority. |
| `candidate_promote` | Useful material that can be rewritten into canonical architecture or scope docs. |
| `conflict` | Cannot be promoted until resolved. |
| `archive_candidate` | Useful history, not a future implementation source. |

## Promotion Targets

| Draft Material | Likely Target |
|---|---|
| L0 overview and glossary facts | `architecture/docs/L0/overview.md` or `glossary.md`. |
| Module and state boundaries | `architecture/docs/L0/boundaries.md` or L1 module docs. |
| Cross-cutting constraints and invariants | `architecture/docs/L0/constraints.md` or governance rules. |
| BA scenarios and technical scenarios | Version scope system, with selected architecture stress scenarios promoted into `views.md`. |
| Capabilities and feature/use-case mapping | `architecture/features/` for architecture facts, version scope docs for release commitment. |
| Harness and verification matrix | `architecture/features/verification.dsl`, test docs, or version acceptance plan. |
| Contract and interface sketches | Accepted contract catalog and contract documentation, not this L0 package. |
| A2D process material | Governance docs after alignment with active rules. |
| Trustworthy/DFX material | Split between trustworthy/DFX architecture, governance, and evidence docs after SPI alignment. |

## Layer Update Protocol

When a change affects multiple architecture layers, update from the highest
affected layer downward.

```text
L0 change
  -> describe L1 impacts
  -> update affected L1 module docs
  -> describe L2 impacts
  -> update affected L2 designs, contracts, harnesses, or tests
```

When implementation or L2 design discovers a contradiction with L1 or L0,
stop and raise the issue upward. Do not silently change the lower layer to
violate the upper layer.

Each cross-layer update should record:

- Source layer and source change.
- Target layer and affected modules.
- Affected contracts or verification edges.
- Constraints inherited from the upper layer.
- Open questions requiring human or architecture-owner decision.

## Traceability Rule

New accepted architecture facts should be traceable to:

- Principle or constraint.
- Module or capability owner.
- ADR or decision record.
- L1/L2 document when applicable.
- Verification method or explicit unverified status.

If this chain is incomplete, mark the item as missing traceability and do not
call it fully accepted.

## Conflict Register

| ID | Conflict | Sources | Required Decision |
|---|---|---|---|
| L0-CONFLICT-001 | Run vs Task canonical execution state. Current authority uses Run/RunRepository/RunStateMachine; draft delivery material proposes Task/TaskStateStore as server-side canonical and Run as compatibility/client invocation. | `ARCHITECTURE.md`, `docs/architecture/l0/00-overview/architecture-overview.md`, `docs/architecture/l0/06-state/state-ownership-matrix.md`, agent-service L1 docs. | Decide whether to keep Run canonical, promote Task canonical, or define a staged Run-to-Task compatibility model. |
| L0-CONFLICT-002 | Eight generated modules vs six core runtime architecture modules plus BoM/starter as artifacts. | `architecture/generated/modules.dsl`, `ARCHITECTURE.md`, old module responsibility cards. | Keep generated eight-module reactor truth while documenting six core runtime modules and two artifact roles. |
| L0-CONFLICT-003 | Neutral orchestration/engine SPI ownership drift. ADR-0158 places neutral SPI in `agent-bus`; some draft/trustworthy material assigns more to `agent-execution-engine`. | `architecture/workspace.dsl`, generated module facts, `docs/architecture/TRIAGE.md`, trustworthy decomposition. | Align all promoted text with ADR-0158. |
| L0-CONFLICT-004 | Same-service multi-agent collaboration vs bus-mediated A2A boundary. Draft material says same-service collaboration is service-owned and cross-boundary A2A is bus-owned; older text may overgeneralize bus responsibility. | old overview, module cards, `ARCHITECTURE.md` bus constraints. | Preserve service-owned same-service coordination and bus-owned cross-boundary control, or record ADR exception. |
| L0-CONFLICT-005 | Bus payload/stream role. Draft material forbids bus as large payload or token stream; older material may not consistently separate service SSE, bus control, and data-reference paths. | old overview, BA/S6 scenarios, `ARCHITECTURE.md` channel constraints. | Decide exact L0 wording and downstream L1/L2 stream contract implications. |
| L0-CONFLICT-006 | L1 agent-service canonical files contain unresolved merge markers. | `architecture/docs/L1/agent-service/*.md`, `docs/architecture/TRIAGE.md`. | Resolve L1 conflicts before treating agent-service L1 as stable downstream authority. |
| L0-CONFLICT-007 | Contract/interface draft location. Old L0 has ICD/YAML sketches, but this L0 package intentionally excludes contract ownership. | `docs/architecture/l0/05-contracts/`, user direction for this consolidation. | Promote useful contract semantics into accepted contract system, not L0 docs. |

## Missing Point Register

| ID | Missing Point | Impact | Proposed Next Action |
|---|---|---|---|
| L0-GAP-001 | Accepted Run/Task vocabulary ADR. | Blocks stable state ownership, glossary, L1 agent-service cleanup, and harness naming. | Create or update ADR resolving canonical lifecycle vocabulary and compatibility path. |
| L0-GAP-002 | Formal version scope system location and skeleton. | BA scenarios, feature use cases, function points, harnesses, and delivery slices remain mixed with architecture facts. | Create separate version scope document tree and move scope-facing drafts there. |
| L0-GAP-003 | Promotion decision for BA-001/BA-002/BA-003 and S1-S6. | Scenarios are useful but not accepted architecture or version scope yet. | Classify each as architecture stress scenario, version scope scenario, or archive. |
| L0-GAP-004 | Capability Placement accepted contract and verification chain. | C-Side/S-Side, local capability, weak department, and federated modes remain under-specified. | Promote CAP-12 semantics into architecture features plus accepted contract docs and tests. |
| L0-GAP-005 | Harness-first verification mapping. | Draft verification matrix is not wired into `architecture/features/verification.dsl` or CI gates. | Map accepted invariants/scenarios to verification DSL and test/harness docs. |
| L0-GAP-006 | Trustworthy/DFX home. | AI risk, trust boundary, and evidence material remains candidate_promote and may drift. | Decide whether to split into L2 trustworthy architecture, governance, and DFX evidence. |
| L0-GAP-007 | Contract catalog promotion of old ICD/YAML drafts. | Draft contracts cannot drive runtime behavior. | Review each ICD/YAML and move accepted items to the contract system. |
| L0-GAP-008 | Regenerated visual views. | Draft PlantUML/SVG/PNG views may not reflect canonical workspace facts. | Regenerate views from accepted DSL after conflicts settle. |
| L0-GAP-009 | L0 verification status for each consolidated constraint. | Some constraints remain manual-review only or unverified. | Add verification rows or explicit unverified statuses. |
| L0-GAP-010 | L1 downstream impact list for this L0 split. | New L0 package may leave old references pointing to `ARCHITECTURE.md`. | Add follow-up L1/doc reference update after user approves skeleton. |

## Current Non-Goals

- This consolidation does not delete or rewrite `ARCHITECTURE.md`.
- This consolidation does not promote contract schemas.
- This consolidation does not resolve Run/Task or L1 merge conflicts.
- This consolidation does not create the version scope system yet.

## Review Checklist

Before promoting any draft into this package:

- Does it contradict accepted ADRs or generated facts?
- Is it architecture fact or version scope?
- Does it introduce a new module, state owner, writer, or bus responsibility?
- Does it require contract catalog changes?
- Does it require L1 or L2 downstream updates?
- Does it have verification or explicit unverified status?
