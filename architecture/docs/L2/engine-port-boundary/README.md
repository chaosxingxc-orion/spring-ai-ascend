---
level: L2
view: development
status: active
authority: "ADR-0158 (Engine Boundary / EnginePort — transport-agnostic Service↔Engine contract)"
relates_to:
  - architecture/docs/L1/agent-bus/ARCHITECTURE.md
  - architecture/docs/L1/agent-execution-engine/ARCHITECTURE.md
  - architecture/docs/L1/agent-service/ARCHITECTURE.md
extends:
  - ADR-0158
  - ADR-0157
  - ADR-0101
---

# L2 — EnginePort Boundary (Detail Home)

This is the **L2 technical-detail home** for the EnginePort boundary defined by
**ADR-0158**. It exists to hold the runtime / wire / migration mechanics that the
L0 and L1 corpus deliberately do **not** carry.

## Why this sink exists

The L0 architecture (`architecture/docs/L0/ARCHITECTURE.md`) and the L1 module
designs (`agent-bus`, `agent-execution-engine`, `agent-service`) state the
boundary at their own altitude:

- **L0** declares the *constraint*: a neutral Service↔Engine boundary exists, the
  engine carries no reverse dependency on the Service, and one artifact set serves
  three deployment forms (ADR-0101 / ADR-0158). L0 names no wire fields and no
  method signatures.
- **L1** declares the *structure*: the public SPI package `bus.spi.engine` is the
  boundary identity (owned by `agent-bus`), the engine realizes it
  (`agent-execution-engine`), and the Service drives it through transport adapters
  (`agent-service`). L1 names the package and the frame (`EF-ENGINE-PORT`) but
  not the over-the-wire suspend mechanics or the migration sequence.

Everything below the structural line — the checkpoint-token suspend/resume
realization, the three concrete transport adapters and how a transport is
selected, the orchestration-SPI re-namespacing migration, and the per-form
physical placement — is **L2 detail** and lives here. ADR-0158's normalized view
(`docs/adr/normalized/ADR-0158.yaml#l2_refs`) explicitly points the
over-the-wire suspend realization and the SPI re-namespacing down to this layer.

## Upstream authority (read these first)

| Layer | Artefact | What it fixes |
|---|---|---|
| ADR | [`docs/adr/0158-engine-port-transport-agnostic-boundary.yaml`](../../../../docs/adr/0158-engine-port-transport-agnostic-boundary.yaml) | The decision: EnginePort is the neutral semantic contract; deployment form is a packaging + adapter choice. |
| ADR (normalized) | [`docs/adr/normalized/ADR-0158.yaml`](../../../../docs/adr/normalized/ADR-0158.yaml) | Curated active-guidance restatement; `l2_refs` delegates the wire/migration detail to this sink. |
| Frame | `architecture/docs/L1/frames/EF-ENGINE-PORT.md` (`EF-ENGINE-PORT`, owner `agent-bus`) | The structural frame this detail anchors. |
| DSL | [`architecture/features/engineering-frames.dsl`](../../../features/engineering-frames.dsl) (`efEnginePort`) | The authoritative frame element + `agent-bus contains EF-ENGINE-PORT` edge. |
| Contract | [`docs/contracts/engine-port.v1.yaml`](../../../../docs/contracts/engine-port.v1.yaml) | The neutral wire shape both networked transports mirror. |

Authority cascade (per ADR-0160 / ADR-0161): **generated facts > DSL > Card/prose**.
This sink is a *readable interpretation layer*. It invents no IDs and no
relationships; where it names a type, package, frame, or contract operation, that
identifier is owned upstream and merely cited here.

## Views in this sink

Per Rule 33 (Layered 4+1 Discipline) an L2 sink carries only the views relevant
to its feature.

| View | File | Detail it homes |
|---|---|---|
| development | [`development.md`](development.md) | Type inventory of `bus.spi.engine`; the three transport-adapter homes; the orchestration-SPI re-namespacing migration map. |
| process | [`process.md`](process.md) | The suspend/resume checkpoint-token realization; the in-process `SuspendSignal` mapping; error-as-terminal-event handling. |
| physical | [`physical.md`](physical.md) | Per-deployment-form placement of contract / engine / adapters; checkpoint-store ownership; repo-split readiness. |
| scenarios | [`scenarios.md`](scenarios.md) | Canonical execute, suspend-over-wire + resume, and cross-transport conformance (TCK) sequences. |

## Gate behaviour

- Rule 37 (`architecture_artefact_front_matter`): every file here declares
  `level: L2` + a valid `view:`.
- Rule 38 (`architecture_graph_well_formed`): every file here is indexed by its
  `(level, view)` into `docs/governance/architecture-graph.yaml`; the regenerated
  graph is owned by the reconcile/governance wave, not by these documents.

## Authority

- Rule 33 — Layered 4+1 Discipline (`CLAUDE.md`)
- Rule 34 — Architecture-Graph Truth (`CLAUDE.md`)
- ADR-0068 — Layered 4+1 + Architecture Graph as Twin Sources of Truth
- ADR-0158 — Engine Boundary (EnginePort) — transport-agnostic Service↔Engine contract
