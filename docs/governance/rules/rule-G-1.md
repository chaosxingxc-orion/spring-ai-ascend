---
rule_id: G-1
title: "Layered 4+1 Discipline + Architecture-Graph Truth"
level: L0
view: scenarios
principle_ref: P-C
authority_refs: [ADR-0068]
enforcer_refs: [E55, E57, E56, E58]
status: active
scope_phase: design
kernel_cap: 8
kernel: |
  **Every architecture artefact (`ARCHITECTURE.md` section, `docs/adr/*.yaml`, `docs/L2/*.md`, `docs/logs/reviews/*.md`) MUST declare front-matter `level: L0|L1|L2` and `view: logical|development|process|physical|scenarios` per the 4+1 discipline (sub-clause .a); root `ARCHITECTURE.md` is L0 canonical, `agent-*/ARCHITECTURE.md` is L1, `docs/L2/` is L2; phase-released L0/L1 artefacts are read-only with further edits flowing through `docs/logs/reviews/`. The machine-readable index `docs/governance/architecture-graph.yaml` MUST be generated (never hand-edited) by `gate/build_architecture_graph.sh` from principle-coverage / enforcers / status / module-metadata / ADR yaml inputs; the graph encodes principle→rule, rule→enforcer, enforcer→test/artefact, capability→test, module→module (allowed/forbidden), adr→adr (supersedes/extends/relates_to as DAGs), and (level,view)→artefact edges; the build MUST be idempotent (byte-identical re-run) (sub-clause .b).**
---

# Rule G-1 — Layered 4+1 Discipline + Architecture-Graph Truth

Operationalises principle **P-C** (Code-as-Everything, Rapid Evolution, Independent Modules) on the architecture-artefact surface.

## Sub-clauses

### .a — Layered 4+1 Discipline (was Rule 33)

**Enforcers**: E55, E57.

Every architecture artefact (`ARCHITECTURE.md` section, `docs/adr/*.yaml`, `docs/L2/*.md`, `docs/logs/reviews/*.md`) MUST declare two front-matter keys: `level: L0 | L1 | L2` and `view: logical | development | process | physical | scenarios`. The root `ARCHITECTURE.md` is the canonical L0 corpus; per-module `agent-*/ARCHITECTURE.md` files are L1; deep technical designs in `docs/L2/` are L2. Each level MUST organise its content under the 4+1 view headings; L2 MAY omit views not relevant to the feature. All change proposals in `docs/logs/reviews/` MUST declare `affects_level:` and `affects_view:`. Phase-released L0/L1 artefacts are read-only — further edits MUST flow through `docs/logs/reviews/`.

### .b — Architecture-Graph Truth (was Rule 34)

**Enforcers**: E56, E58.

`docs/governance/architecture-graph.yaml` is the single machine-readable index of architectural relationships. It MUST be generated, never hand-edited, by `gate/build_architecture_graph.sh` from authoritative inputs (`docs/governance/principle-coverage.yaml`, `enforcers.yaml`, `architecture-status.yaml`, `module-metadata.yaml`, and the `docs/adr/*.yaml` corpus). The graph MUST encode at minimum these edge classes: `principle → rule`, `rule → enforcer`, `enforcer → test`, `enforcer → artefact`, `capability → test`, `module → module` (allowed / forbidden), `adr → adr` (`supersedes` / `extends` / `relates_to`), and `(level, view) → artefact`. The `supersedes` and `extends` sub-graphs MUST be DAGs. Every edge endpoint MUST resolve to a real graph node or file path. The build script MUST be idempotent — re-running on the same inputs MUST produce a byte-identical output.

## Cross-references

- ADR-0068 (Layered 4+1 + Architecture Graph) — origin authority for both sub-clauses.
- Companion rule: Rule G-2 sub-clause .d (Root-ARCHITECTURE count + path truth) which uses the graph as one of its data sources.
