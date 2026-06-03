---
level: L0-TLD
TAG:
  - governance
  - promotion
  - traceability
  - layer-update
  - architecture-fact
status: 架构事实
dependency:
  - README.md
  - overview.md
  - views.md
  - boundaries.md
  - constraints.md
  - glossary.md
---

# L0 Governance

## Purpose

This document defines how L0 architecture facts are governed, how draft material
from `docs/` is promoted, how L0/L1/L2 updates are sequenced, and which gaps
remain open after the initial L0 consolidation.

## Fact Governance

The current branch does not keep a separate workspace authority system. The
repository stores accepted facts in two families:

- Architecture facts under `architecture/`.
- Code facts in source code, module metadata, tests, contract files, generated
  runtime evidence, and other verifiable project artifacts.

Drafts, proposals, review notes, and archives live under `docs/`. They are not
architecture facts until promoted into `architecture/`, and they are not code
facts until represented by implementation, tests, metadata, or contracts.

## Fact and Draft Zones

| Zone | Rule |
|---|---|
| Architecture facts under `architecture/` | Accepted architecture material only. |
| Code facts | Source code, module metadata, tests, contracts, and runtime-verifiable artifacts. |
| Draft docs under `docs/architecture/` | Proposal material only until promoted into architecture facts. |
| Contract/interface docs | Not owned by L0; accepted contracts live in the contract system. |
| Review proposals under `docs/logs/reviews/` | Source material for promotion only after conflict review. |

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
| L0 overview and glossary facts | `architecture/L0-Top-Level-Design/overview.md` or `glossary.md`. |
| Module and state boundaries | `architecture/L0-Top-Level-Design/boundaries.md` or L1 module docs. |
| Cross-cutting constraints and invariants | `architecture/L0-Top-Level-Design/constraints.md` or governance rules. |
| BA scenarios and technical scenarios | Version scope system, with selected architecture stress scenarios promoted into `views.md`. |
| Capabilities and feature/use-case mapping | Architecture fact documents for accepted facts, version scope docs for release commitment. |
| Harness and verification matrix | Test docs, code facts, or version acceptance plan. |
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

No open L0 conflicts remain after the current consolidation pass. New conflicts
should be added here only when they require an L0 architecture decision.

## Missing Point Register

| ID | Missing Point | Impact | Proposed Next Action |
|---|---|---|---|
| L0-GAP-002 | Formal version scope system location and skeleton. | BA scenarios, feature use cases, function points, harnesses, and delivery slices remain mixed with architecture facts. | Create separate version scope document tree and move scope-facing drafts there. |
| L0-GAP-003 | Promotion decision for BA-001/BA-002/BA-003 and S1-S6. | Scenarios are useful but not accepted architecture or version scope yet. | Classify each as architecture stress scenario, version scope scenario, or archive. |
| L0-GAP-004 | Capability Placement accepted contract and verification chain. | C-Side/S-Side, local capability, weak department, and federated modes remain under-specified. | Promote CAP-12 semantics into architecture features plus accepted contract docs and tests. |
| L0-GAP-005 | Harness-first verification mapping. | Draft verification matrix is not wired into code facts or CI gates. | Map accepted invariants/scenarios to tests, harness docs, or verification evidence. |
| L0-GAP-006 | Trustworthy/DFX home. | AI risk, trust boundary, and evidence material remains candidate_promote and may drift. | Decide whether to split into L2 trustworthy architecture, governance, and DFX evidence. |
| L0-GAP-007 | Contract catalog promotion of old ICD/YAML drafts. | Draft contracts cannot drive runtime behavior. | Review each ICD/YAML and move accepted items to the contract system. |
| L0-GAP-008 | Regenerated visual views. | Draft PlantUML/SVG/PNG views may not reflect current architecture facts. | Regenerate views from accepted architecture facts after conflicts settle. |
| L0-GAP-009 | L0 verification status for each consolidated constraint. | Some constraints remain manual-review only or unverified. | Add verification rows or explicit unverified statuses. |
| L0-GAP-010 | L1 downstream impact list for this L0 split. | New L0 package may leave old references pointing to `ARCHITECTURE.md`. | Add follow-up L1/doc reference update after user approves skeleton. |
| L0-GAP-011 | Interrupt and rollback semantics. | The proposal calls for user/agent interrupts at trajectory or context granularity, including abort and safe rollback, but current L0 only has suspend/resume, cancellation, callback, and governed control-command language. | Decide the accepted granularity and whether rollback is checkpoint restore, compensation, cancellation, or a separate L1/L2 mechanism. |
| L0-GAP-012 | Skill topology scheduler and capability bidding detail. | The proposal describes tenant x global skill capacity arbitration and permission alignment; current L0 has capacity/backpressure and sandbox constraints but not a full scheduler contract. | Promote only after capability placement, skill capacity, and sandbox contract ownership are settled. |
| L0-GAP-013 | Evolution data-flywheel contract. | The proposal describes offline scoring, reinforcement learning, knowledge graph construction, and prompt optimization; current L0 only accepts a governed evolution-plane boundary. | Define export contract, privacy controls, and whether any online feedback path is allowed. |
| L0-GAP-014 | Developer lifecycle tooling scope. | The proposal asks for development/debugging tools, operations observability, and visualization; current L0 promotes evidence/harness but not tool inventory. | Decide which tooling belongs in L0, version scope, or product roadmap. |

## Current Non-Goals

- This consolidation archives the legacy `ARCHITECTURE.md`; it does not treat
  the archived file as current authority.
- This consolidation does not promote contract schemas.
- This consolidation does not resolve L1 merge conflicts.
- This consolidation does not create the version scope system yet.

## Review Checklist

Before promoting any draft into this package:

- Does it contradict accepted ADRs or generated facts?
- Is it architecture fact or version scope?
- Does it introduce a new module, state owner, writer, or bus responsibility?
- Does it require contract catalog changes?
- Does it require L1 or L2 downstream updates?
- Does it have verification or explicit unverified status?
