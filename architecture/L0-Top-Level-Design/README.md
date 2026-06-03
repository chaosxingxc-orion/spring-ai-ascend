---
level: L0-TLD
TAG:
  - entry
  - governance
  - reading-path
  - architecture-fact
status: 架构事实
dependency:
  - overview.md
  - views.md
  - boundaries.md
  - constraints.md
  - governance.md
  - glossary.md
---

# L0 Architecture Top-Level Design

## Purpose

This directory is the consolidated L0 architecture fact package for `spring-ai-ascend`.
It explains the system boundary, top-level 4+1 views, module and state
boundaries, cross-cutting constraints, governance rules, and shared vocabulary.

## Fact Package Boundary

This directory stores L0 top-level architecture facts for the current branch.
It is not a proposal workspace and does not store draft-state design material.

The repository keeps two accepted fact families:

- Architecture facts under `architecture/`.
- Code facts in source code, module metadata, tests, contracts, and other
  runtime-verifiable project artifacts.

Drafts, proposals, review notes, and archived historical material live under
`docs/`. They may inform future changes, but they do not override architecture
facts or code facts until explicitly promoted.

## Document Map

| File | Role |
|---|---|
| `README.md` | Entry, fact package boundary, reading path, and package boundaries. |
| `overview.md` | System goal, audience, runtime path, deployment variants, logical module boundary shape, quality attributes, and top-level risks. |
| `views.md` | L0 4+1 architecture views: logical, development, process, physical, and scenarios. |
| `boundaries.md` | Logical module admission, module responsibilities, downstream artifact treatment, and state ownership. |
| `constraints.md` | Cross-cutting verticals, invariants, and architectural constraints. |
| `governance.md` | Fact governance, promotion rules, layer update protocol, traceability, and open decisions. |
| `glossary.md` | Shared vocabulary and forbidden conflations. |

Contract and interface details are intentionally not defined in this directory.
Accepted runtime contracts belong in the contract catalog and related contract
documentation. L0 may reference contract categories, but it does not own wire
schemas, route behavior, SPI signatures, or machine-readable contract files.
Legacy ICD/YAML material under `docs/architecture/l0/05-contracts/` remains
draft source material only until selected semantics are promoted into the
accepted contract system.

## Reading Path

1. Read `overview.md` to understand the system shape.
2. Read `views.md` to understand the L0 4+1 view model.
3. Read `boundaries.md` before changing modules, state ownership, or runtime
   responsibility.
4. Read `constraints.md` before changing cross-cutting behavior.
5. Read `governance.md` before promoting draft material or changing multiple
   layers.
6. Read `glossary.md` whenever terms such as Task, Session, Platform Gateway,
   Service Task API, Context Engine, Tool Gateway, C-Side, or S-Side are
   involved.

## Promotion Rule

Draft material under `docs/architecture/l0/` and review proposals under
`docs/logs/reviews/` can be used in three ways:

- Promote architecture facts into this L0 package or into L1/L2 architecture
  documents after conflict review against current architecture facts and code
  facts.
- Promote scope, scenario, feature, harness, or delivery material into the
  version scope system.
- Archive material that is useful history but no longer describes the current
  architecture.

No draft material should be copied verbatim if it conflicts with accepted ADRs,
module metadata, code facts, contract facts, or this package's vocabulary.
