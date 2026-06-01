---
level: L0
view: scenarios
status: active
authority: "ADR-0150 (W8 docs consolidation) + architecture/workspace.dsl"
document_role: canonical_prose
source_of_truth: true
---

# Architecture Documentation

`architecture/docs/` is the human-readable companion to the machine-readable
architecture model at [`../workspace.dsl`](../workspace.dsl). The workspace is
the canonical model; this directory is the canonical prose mounted into that
model.

## Directory Roles

| Path | Purpose | Reader Use |
|---|---|---|
| `L0/` | System boundary, platform constraints, and cross-module architecture commitments. | Start here when checking what the platform structurally commits to. |
| `L1/` | Per-module architecture. Some modules use a compact README; mature modules use full 4+1 view files. | Start here when implementing or reviewing a module. |
| `L2/` | Deeper subsystem or mechanism designs promoted from accepted L1 needs. | Read only when L0/L1 points to a deeper design. |

## Human Reading Path

1. Read [`../README.md`](../README.md) for the authority model and repository-wide
   architecture reading path.
2. Read [`L0/ARCHITECTURE.md`](L0/ARCHITECTURE.md) for system-level boundaries
   and constraints.
3. Read [`L1/README.md`](L1/README.md), then the relevant module directory or
   module README.
4. Read [`L2/`](L2/) only when L0/L1 material points to a deeper design.

## AI Reading Path

1. Read [`../facts/generated/`](../facts/generated/) before prose when making
   factual claims about code, contracts, tests, modules, or runtime config.
2. Read [`../workspace.dsl`](../workspace.dsl) to build the architecture graph.
3. Read [`L0/ARCHITECTURE.md`](L0/ARCHITECTURE.md) and the relevant
   [`L1/`](L1/) module prose.
4. Read `../../docs/contracts/`, `../../docs/adr/`, and
   `../../docs/governance/` only as supporting authority for specific claims.
5. Treat `../../docs/architecture/`, review logs, and archives as non-overriding
   context unless a canonical file explicitly promotes them.

The full repository reading path is declared in repo-root
[`../../README.md`](../../README.md#reading-path).
