---
level: L0
view: governance
status: draft
document_role: pending_architecture_proposal
source_of_truth: false
canonical_l0: ../../../architecture/docs/L0/
created: 2026-06-02
---

# Pending L0 Consolidation Proposal

## Purpose

This proposal records the conflict points, missing points, and unabsorbed source
material found while consolidating L0 top-level design into
`architecture/docs/L0/`.

It is not canonical architecture truth. It is a triage proposal for later
architecture decisions, promotion, rewrite, or archival.

## Current Consolidation Output

The new L0 document skeleton under `architecture/docs/L0/` is:

| File | Role |
|---|---|
| `README.md` | L0 entry, authority, reading path, and promotion rule. |
| `overview.md` | System goal, audience, runtime path, deployment variants, quality attributes, and risks. |
| `views.md` | L0 4+1 views and candidate architecture stress scenarios. |
| `boundaries.md` | Module admission, module responsibilities, capability aggregates, and state ownership. |
| `constraints.md` | Cross-cutting verticals, core invariants, and grouped constraints. |
| `governance.md` | Authority, promotion rules, layer update protocol, traceability, conflicts, and gaps. |
| `glossary.md` | Shared terms and forbidden conflations. |

Contract/interface content was intentionally excluded from the L0 package and
should be promoted into the accepted contract system if reused.

## Conflict Register

| ID | Conflict | Sources | Required Decision |
|---|---|---|---|
| L0-CONFLICT-001 | Run vs Task canonical execution state. Current authority uses Run/RunRepository/RunStateMachine; draft delivery material proposes Task/TaskStateStore as server-side canonical and Run as compatibility/client invocation. | `architecture/docs/L0/ARCHITECTURE.md`, `docs/architecture/l0/00-overview/architecture-overview.md`, `docs/architecture/l0/06-state/state-ownership-matrix.md`, agent-service L1 docs. | Decide whether to keep Run canonical, promote Task canonical, or define a staged Run-to-Task compatibility model. |
| L0-CONFLICT-002 | Eight generated modules vs six core runtime architecture modules plus BoM/starter as artifacts. | `architecture/generated/modules.dsl`, `ARCHITECTURE.md`, old module responsibility cards. | Keep generated eight-module reactor truth while documenting six core runtime modules and two artifact roles. |
| L0-CONFLICT-003 | Neutral orchestration/engine SPI ownership drift. ADR-0158 places neutral SPI in `agent-bus`; some draft/trustworthy material assigns more to `agent-execution-engine`. | `architecture/workspace.dsl`, generated module facts, `docs/architecture/TRIAGE.md`, trustworthy decomposition. | Align all promoted text with ADR-0158. |
| L0-CONFLICT-004 | Same-service multi-agent collaboration vs bus-mediated A2A boundary. Draft material says same-service collaboration is service-owned and cross-boundary A2A is bus-owned; older text may overgeneralize bus responsibility. | old overview, module cards, `ARCHITECTURE.md` bus constraints. | Preserve service-owned same-service coordination and bus-owned cross-boundary control, or record ADR exception. |
| L0-CONFLICT-005 | Bus payload/stream role. Draft material forbids bus as large payload or token stream; older material may not consistently separate service SSE, bus control, and data-reference paths. | old overview, BA/S6 scenarios, `ARCHITECTURE.md` channel constraints. | Decide exact L0 wording and downstream L1/L2 stream contract implications. |
| L0-CONFLICT-006 | L1 agent-service canonical files contain unresolved merge markers. | `architecture/docs/L1/agent-service/*.md`, `docs/architecture/TRIAGE.md`. | Resolve L1 conflicts before treating agent-service L1 as stable downstream authority. |
| L0-CONFLICT-007 | Contract/interface draft location. Old L0 has ICD/YAML sketches, but the new L0 package intentionally excludes contract ownership. | `docs/architecture/l0/05-contracts/`, user direction for this consolidation. | Promote useful contract semantics into accepted contract system, not L0 docs. |
| L0-CONFLICT-008 | Root README and some old reading paths still point readers primarily at `architecture/docs/L0/ARCHITECTURE.md` instead of the new L0 package. | `README.md`, older docs. | After skeleton approval, update reading paths to reference the new L0 package while retaining `ARCHITECTURE.md` as raw constraint corpus until fully absorbed. |

## Missing Point Register

| ID | Missing Point | Impact | Proposed Next Action |
|---|---|---|---|
| L0-GAP-001 | Accepted Run/Task vocabulary ADR. | Blocks stable state ownership, glossary, L1 agent-service cleanup, and harness naming. | Create or update ADR resolving canonical lifecycle vocabulary and compatibility path. |
| L0-GAP-002 | Formal version scope system content. | BA scenarios, feature use cases, function points, harnesses, and delivery slices remain empty or mixed with architecture facts. | Fill `version-scope/v1/` after architecture skeleton is accepted. |
| L0-GAP-003 | Promotion decision for BA-001/BA-002/BA-003 and S1-S6. | Scenarios are useful but not accepted architecture or version scope yet. | Classify each as architecture stress scenario, version scope scenario, or archive. |
| L0-GAP-004 | Capability Placement accepted contract and verification chain. | C-Side/S-Side, local capability, weak department, and federated modes remain under-specified. | Promote CAP-12 semantics into architecture features plus accepted contract docs and tests. |
| L0-GAP-005 | Harness-first verification mapping. | Draft verification matrix is not wired into `architecture/features/verification.dsl` or CI gates. | Map accepted invariants/scenarios to verification DSL and test/harness docs. |
| L0-GAP-006 | Trustworthy/DFX home. | AI risk, trust boundary, and evidence material remains candidate_promote and may drift. | Decide whether to split into L2 trustworthy architecture, governance, and DFX evidence. |
| L0-GAP-007 | Contract catalog promotion of old ICD/YAML drafts. | Draft contracts cannot drive runtime behavior. | Review each ICD/YAML and move accepted items to the contract system. |
| L0-GAP-008 | Regenerated visual views. | Draft PlantUML/SVG/PNG views may not reflect canonical workspace facts. | Regenerate views from accepted DSL after conflicts settle. |
| L0-GAP-009 | L0 verification status for each consolidated constraint. | Some constraints remain manual-review only or unverified. | Add verification rows or explicit unverified statuses. |
| L0-GAP-010 | L1 downstream impact list for this L0 split. | New L0 package may leave old references pointing to `ARCHITECTURE.md`. | Add follow-up L1/doc reference update after skeleton approval. |

## `ARCHITECTURE.md` Absorption Assessment

`architecture/docs/L0/ARCHITECTURE.md` has not been fully absorbed by the new
L0 document package.

The new package absorbed the following at summary level:

- System boundary and target audience.
- 4+1 view map.
- Cross-cutting Tenant, Posture, and Telemetry verticals.
- Module layout and eight-module reactor truth.
- Core boundary concerns such as service, engine, middleware, bus, client, and evolve.
- High-level runtime path, deployment variants, control/data/stream separation, and version honesty.
- Architecture workspace authority and generated/authored zone split.

The following source material remains unabsorbed or only partially absorbed:

| ID | Unabsorbed / Partially Absorbed Material | Why It Matters | Proposed Handling |
|---|---|---|---|
| ARCH-UNABSORBED-001 | The full 65 numbered L0 constraints with per-item wording, ADR references, rule/enforcer references, and wave qualifiers. | The new `constraints.md` groups constraints but does not preserve all precise normative wording. | Keep `ARCHITECTURE.md` until each constraint is either migrated, superseded, or linked to generated rule/enforcer facts. |
| ARCH-UNABSORBED-002 | Constraint-to-rule cross-reference details and highest-cited enforcer pairs. | Needed for gate traceability and architecture workspace validation. | Move detailed mappings into `governance.md` or rely on generated `architecture/generated/enforcers.dsl` with explicit links. |
| ARCH-UNABSORBED-003 | Detailed W0 shipped subset and W0 shipped capabilities list. | Needed for design honesty and shipped-vs-target separation. | Promote into a dedicated status/verification document or link to `docs/governance/architecture-status.yaml`. |
| ARCH-UNABSORBED-004 | OSS dependency table and dependency risk notes. | Important but not pure L0 top-level architecture; overlaps cross-cutting BoM docs. | Move or reference from cross-cutting dependency governance, not L0 overview. |
| ARCH-UNABSORBED-005 | Detailed module tree snippets and package-level examples. | Too detailed for L0 overview but useful for L1/development grounding. | Move to L1/module docs or generated module projections. |
| ARCH-UNABSORBED-006 | Fine-grained payload, codec, ontology, placeholder, cognition-action, skill-resource, migration, and SPI precision constraints. | These are detailed constraints that may belong in L1/L2, contract docs, or generated facts. | Classify each as L0 invariant, L1 module rule, L2 contract detail, or archive. |
| ARCH-UNABSORBED-007 | Roadmap pointers and wave-specific reintroduction triggers. | Important for version/scope planning but not all are architecture facts. | Split between governance/status ledgers and `version-scope/`. |
| ARCH-UNABSORBED-008 | Competitive baseline publication and release-note truth constraints. | These are governance/release constraints and need exact rule linkage. | Promote into governance docs or generated rule mapping, not the L0 overview body. |
| ARCH-UNABSORBED-009 | Telemetry implementation specifics such as attribute names, OTLP/Langfuse details, replay tool names, and test class names. | Needed for enforcement but too detailed for L0 top-level prose. | Keep in contract/telemetry policy/L1/L2 docs; summarize only in L0 constraints. |
| ARCH-UNABSORBED-010 | Freeze marker and edit-path compliance language for old root `ARCHITECTURE.md`. | Determines whether removal/editing is allowed. | Do not remove `ARCHITECTURE.md` until an accepted migration decision says it has been fully replaced. |

## Recommendation

Do not remove `architecture/docs/L0/ARCHITECTURE.md` yet.

Treat it as the raw historical L0 constraint corpus until:

1. Every numbered constraint has a canonical destination or retirement decision.
2. Constraint-to-rule/enforcer traceability is preserved.
3. Shipped-vs-target capability status is linked to accepted status ledgers.
4. Run/Task vocabulary is resolved.
5. Root and L1 reading paths are updated to the new package.

After that, `ARCHITECTURE.md` can be replaced by a redirect/index or archived
through an explicit architecture migration decision.
