---
rule_id: G-1
title: "Layered 4+1 Discipline + Architecture Workspace Truth"
level: L0
view: scenarios
principle_ref: P-C
authority_refs: [ADR-0068, ADR-0147]
enforcer_refs: [E55, E57, E56, E58]
status: active
governance_infra: true
scope_phase: design
kernel_cap: 8
kernel: |
  **Every architecture artefact (`architecture/docs/L0/ARCHITECTURE.md`, `docs/adr/*.yaml`, `architecture/docs/L2/*.md`) MUST declare front-matter `level: L0|L1|L2` and `view: logical|development|process|physical|scenarios` per the 4+1 discipline (sub-clause .a); `architecture/docs/L0/ARCHITECTURE.md` is L0 canonical (moved from repo root per ADR-0152), `architecture/docs/L1/<module>/` is L1 (canonical per-view directory per ADR-0152, replacing the W8 single-`.md` fallback), `architecture/docs/L2/` is L2; phase-released L0/L1 artefacts are read-only with further edits flowing through `docs/logs/reviews/` (interaction records — front-matter optional per `docs/governance/logs-folder-policy.md`). The machine-readable architecture authority MUST be rooted in `architecture/workspace.dsl` and its workspace closure (`architecture/profile/`, `architecture/features/`, `architecture/docs/`, `architecture/decisions/`, `architecture/generated/`, `architecture/views/`); generated projections including `docs/governance/architecture-graph.yaml` and `docs/governance/architecture-workspace-graph.yaml` MUST be built from that closure, never hand-edited, and byte-identical on regeneration (sub-clause .b).**
---

# Rule G-1 — Layered 4+1 Discipline + Architecture Workspace Truth

Operationalises principle **P-C** (Code-as-Everything, Rapid Evolution, Independent Modules) on the architecture-artefact surface.

## Sub-clauses

### .a — Layered 4+1 Discipline (was Rule 33)

**Enforcers**: E55, E57.

Every architecture artefact (`ARCHITECTURE.md` section, `docs/adr/*.yaml`, `architecture/docs/L2/**/*.md`) MUST declare two front-matter keys: `level: L0 | L1 | L2` and `view: logical | development | process | physical | scenarios`. The root `ARCHITECTURE.md` is the canonical L0 corpus; per-module L1 design lives under `architecture/docs/L1/<module>.md` (single-narrative shape) OR `architecture/docs/L1/<module>/` (per-view directory shape, currently only `agent-service`); deep technical designs in `architecture/docs/L2/` are L2 (amended W8 per ADR-0150 — was `docs/L1/<module>/` / `docs/L2/` / `agent-*/ARCHITECTURE.md` pre-Wave-8). Each level MUST organise its content under the 4+1 view headings; L2 MAY omit views not relevant to the feature. Files under `docs/logs/reviews/` are interaction records (`docs/governance/logs-folder-policy.md`): front-matter is **optional** and NOT required on plain records; a doc that opts into 4+1 proposal classification by declaring `affects_level:` or `affects_view:` MUST declare both, with valid values (the `review_proposal_front_matter` gate validates if-present). Phase-released L0/L1 artefacts are read-only — further edits flow through `docs/logs/reviews/`.

### .b — Architecture Workspace Truth (amended at W5 per ADR-0147; was Rule 34 / Architecture-Graph Truth)

**Enforcers**: E56, E58.

The machine-readable architecture authority is rooted in `architecture/workspace.dsl` and its workspace closure: `architecture/profile/`, `architecture/features/`, `architecture/docs/`, `architecture/decisions/`, `architecture/generated/`, `architecture/views/`. Engineers author new content under the **authored zone** (`features/`, `docs/L1/`, `decisions/`, `views/`); the **generated zone** (`generated/`) is emitted by `tools/architecture-workspace/.../fragment/AllFragmentsCli` from existing authoritative inputs (`*/module-metadata.yaml`, `docs/governance/enforcers.yaml`, `docs/governance/principle-coverage.yaml`, `docs/adr/*.yaml`, `CLAUDE.md`, `docs/governance/templates/surface-classification.yaml`) and MUST NOT be hand-edited.

Both compatibility projections — `docs/governance/architecture-workspace-graph.yaml` (workspace-native; emitted by `tools/architecture-workspace/.../GraphProjectionWriter.java`) and `docs/governance/architecture-graph.yaml` (legacy schema; emitted by `gate/build_architecture_graph.py` until W6 sunset) — MUST be generated, never hand-edited, and byte-identical on regeneration. The workspace closure MUST encode at minimum these edge classes via `saa.rel`: `operationalised_by` (principle→rule), `enforced_by` (rule→enforcer), `verifies` (test→feature/function point/rule), `implements` (module→feature/function point/contract), `depends_on` (module→module, with `allowed/forbidden` semantics carried on `saa.dependencyKind`), `declares_spi` (module→SPI), `publishes_contract` (module→contract), `decides` (ADR→rule/feature/capability/contract), `supersedes` / `extends` / `relates_to` (ADR→ADR, DAGs), and `projects_to` (any→generated projection). Every relationship endpoint MUST resolve to a real workspace element. Re-running `gate/check_architecture_workspace.sh` MUST produce byte-identical fragment files under `architecture/generated/` and byte-identical projection output under `docs/governance/architecture-workspace-graph.yaml`.

Migration lifecycle: W1 ships the tooling and the workspace closure in **advisory** mode. W5 (this amendment) flips the gate to **blocking** — profile violations or generated-zone drift fail closed. W6 deprecates `docs/governance/{enforcers,principle-coverage,architecture-status.yaml#capabilities}.yaml` as authority (they become generated projections of the workspace). W7 retires the legacy `gate/build_architecture_graph.py` once no active consumer treats the legacy graph YAML as source.

## Cross-references

- ADR-0068 (Layered 4+1 + Architecture Graph) — origin authority for sub-clause .a; extended by ADR-0147 for sub-clause .b.
- ADR-0147 (Structurizr Workspace Authority) — current authority for sub-clause .b.
- ADR-0148 (Wave 0 spike results) — measured evidence supporting the workspace direction.
- Companion rule: Rule G-2 sub-clause .d (Root-ARCHITECTURE count + path truth) which uses the projection as one of its data sources.
