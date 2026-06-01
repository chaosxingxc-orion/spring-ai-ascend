---
level: L0
view: scenarios
status: active
authority: "ADR-0147 (Structurizr Workspace Authority) + ADR-0149 (W0-W5 shipped) + ADR-0150 (W8 docs consolidation)"
---

# Architecture Workspace

`architecture/workspace.dsl` is `唯一主入口` (THE sole main entry) for the entire architecture design system. Every architecture-design conversation starts here. The workspace closure carries the architecture's structure, contracts, decisions, and human-readable narrative as one coherent body.

> **Status (Wave 8 shipped, 2026-05-27):** the workspace gate is BLOCKING since W5; the L1 design corpus is consolidated under `architecture/docs/L1/` since W8; module-root `agent-*/ARCHITECTURE.md` files are merged into the L1 corpus; `!docs docs` + `!adrs decisions` directives are wired so Structurizr formally imports the narrative + ADR markdowns.

## Directory roles

| Path | Role | Edit policy |
|---|---|---|
| `workspace.dsl` | Sole architecture model entry point. | Hand-edit only when changing accepted architecture structure. |
| `docs/L0/` | Canonical L0 prose: system boundary, platform constraints, and cross-module commitments. | Accepted facts only. |
| `docs/L1/` | Canonical module architecture prose. | Accepted module design only; proposals must be promoted before landing here. |
| `docs/L2/` | Canonical deep technical designs promoted from accepted L0/L1 needs. | Accepted designs only. |
| `features/` | Authored capability, feature, function-point, and verification DSL fragments. | Keep aligned with workspace validation. |
| `decisions/` | ADR markdown mirror imported by the workspace. | Mirror accepted ADR anchors; rationale remains in `docs/adr/`. |
| `facts/generated/` | Deterministic facts extracted from code, contracts, tests, modules, and runtime config. | Generated only. |
| `generated/` | Generated DSL projections. | Generated only; never hand-edit. |
| `profile/` and `views/` | Workspace validation profile and view fragments. | Internal architecture tooling. |

## Human reading path

1. **`architecture/README.md`** (this file) - understand the authority model,
   directory roles, and edit policy.
2. **`architecture/workspace.dsl`** - inspect the system/container/component
   structure and the relationships that the workspace validates.
3. **`architecture/docs/L0/ARCHITECTURE.md`** - read the declarative system
   boundary and platform constraints.
4. **`architecture/docs/L1/README.md`** - pick the module you are implementing
   or reviewing.
5. **`docs/contracts/contract-catalog.md`** and the relevant contract schemas -
   confirm runtime promises.
6. **`docs/adr/`** and **`docs/governance/`** - confirm decision rationale,
   rules, enforcers, and status ledgers when cited by canonical architecture.

## AI reading path

1. **`architecture/facts/generated/`** - deterministic ground truth for code
   symbols, contract operations, tests, module dependencies, runtime config, and
   ADR graph. Read this before prose when making factual claims.
2. **`architecture/workspace.dsl`** - architecture structure and relationships.
3. **`architecture/docs/L0/ARCHITECTURE.md`** - declarative L0 constraints.
4. **`architecture/docs/L1/README.md`** and the relevant module - canonical
   module design.
5. **`docs/contracts/`**, **`docs/adr/`**, and **`docs/governance/`** -
   supporting authority for specific claims.

AI agents must treat `docs/architecture/`, `docs/logs/`, `docs/reviews/`, and
`docs/archive/` as non-overriding context unless a canonical file explicitly
promotes a specific artifact. Draft material can explain why a design is under
discussion; it cannot override the workspace, L0/L1 canonical prose, accepted
contracts, accepted ADRs, or governance status ledgers.

## What workspace.dsl carries

| Structurizr concept | Spring AI Ascend role |
|---|---|
| `softwareSystem` / `container` / `component` | L1 architecture structure (8 Maven modules + their components) |
| `element` + tags + properties | Feature / Capability / FunctionPoint instances (`SAA Capability`, `SAA Feature`, `SAA FunctionPoint` tags) |
| `->` relationship + `saa.rel` property | dependency / implements / verifies / constrains / declares_spi / contains / decides edges |
| `!docs docs` | imports `architecture/docs/` as the workspace's human-readable companion |
| `!adrs decisions` | imports `architecture/decisions/<id>.md` (mirrored from `docs/adr/`) as decision records |
| `views {}` | 4+1 organisation form — `systemContext`, `container`, `component`, `dynamic`, `deployment` views |

## Layout (4-item user-facing surface + internal machinery)

```text
architecture/
  workspace.dsl                  # 唯一主入口 (THE sole main entry)
  README.md                      # this file
  docs/                          # L1 narrative + L2 designs (HUMAN-readable; imported via !docs)
    L1/
      README.md                  # L1 module index
      agent-bus.md               # narrative (1 file per module — single-narrative shape)
      agent-client.md
      agent-evolve.md
      agent-execution-engine.md
      agent-middleware.md
      graphmemory-starter.md
      agent-service/             # per-view 4+1 directory shape (graduated from single .md)
        README.md, architecture/docs/L0/ARCHITECTURE.md, logical.md, process.md, physical.md,
        development.md, scenarios.md, spi-appendix.md, features/, diagrams/
    L2/                          # deep technical designs
  decisions/                     # ADR markdown mirror (imported via !adrs)
    0068.md, 0119.md, 0147.md, 0148.md, 0149.md, 0150.md  # anchor set
  features/                      # OPTIONAL — Feature/FP DSL fragments or Markdown
    capabilities.dsl             # 152 capabilities (W2 mount from architecture-status.yaml)
    function-points.dsl          # 15 function points
    verification.dsl             # 7 tests
  profile/                       # SAA profile (internal — validator config)
  views/                         # view DSL fragments (internal — workspace !include)
  generated/                     # 7 generated fragments (internal — emitted by AllFragmentsCli; NEVER hand-edit)
```

## Reading path

Newcomers (human or AI) reach an unbiased architecture picture by reading in this order:

1. **`architecture/facts/generated/`** — machine-extracted factual ground truth: code symbols, contract operations, tests, module dependencies, runtime config, ADR graph. AI agents read THIS BEFORE prose for any factual claim. Populated by the deterministic extractors under `tools/architecture-workspace/.../facts/` (Fact-Layer Round-1 Waves 2-4 shipped 2026-05-27; Round-2 Wave A truth-up shipped 2026-05-28). See [`architecture/facts/README.md`](facts/README.md) for the wave-status table.
2. **`architecture/workspace.dsl`** — the architecture's structure as DSL. Parses everything below into one model.
3. **`architecture/README.md`** (this file) — navigation of the closure + DSL conventions.
4. **`architecture/docs/L1/README.md`** — L1 module entry index. Pick the module you're working on.
5. **`architecture/features/function-points.dsl`** — L1 function-point inventory (who implements what, what verifies it).
6. **`docs/adr/<id>-*.yaml`** — decision rationale for any edge you traverse.

Adjacent surfaces (not architecture-design but operationally needed):

- **`CLAUDE.md`** — enforceable rule kernels (Layer-0 principles + Layer-1 rules); read when you need to know "which rule does this enforce?"
- **`architecture/docs/L0/ARCHITECTURE.md`** (root) — declarative L0 system boundary + 65 §4 constraints; read when you need the platform-level constraint corpus.
- **`docs/contracts/`** — runtime contract surface (OpenAPI, engine envelope, hooks, S2C callback, etc.); read when you need the contract a module commits to at runtime.
- **`docs/quickstart.md`** — boot-and-first-run; read when you need to run the platform.

## DSL conventions (verified 2026-05-27)

- The DSL keyword for custom elements is `element` (NOT `customElement`).
- `Model.getElements()` in structurizr-dsl 6.2.1 INCLUDES CustomElements; `ProfileValidator` walks it exactly once.
- Identifier scope is the default `flat` (top-level identifiers are globally unique; nested-container short identifiers can be referenced from model-block-level relationships).
- Multi-word tags are one quoted string (`"SAA Module"`); the tags list is comma-separated.
- Property keys can be dotted (`"saa.id" "value"`); quote both name and value for safety.
- DSL string literals must escape `{` and `}` (use `(` `)` in relationship descriptions).
- DSL relationship blocks require multi-line `properties { ... }`; single-line form `{ "k" "v" }` fails parsing.
- `!include` order is significant — a fragment cannot reference identifiers defined in a later-included file.

## Authored vs generated zone

| Zone | Path | Edit by | Drift prevented by |
|---|---|---|---|
| Authored | `architecture/workspace.dsl` root + `features/` + `docs/L1/` + `decisions/` + `facts/README.md` + `facts/schema/` + `profile/saa-property-authority.yaml` | humans (or programmatic mount with explicit run) | profile validator + Rule G-1.b + Rule G-15.a |
| Generated DSL | `architecture/generated/*.dsl` | machine emitters in `tools/architecture-workspace/.../fragment/` | byte-identical regeneration gate (Rule G-13.b) |
| Generated Facts | `architecture/facts/generated/*.json` | deterministic extractors in `tools/architecture-workspace/.../facts/` (Waves 2-5) | byte-identical regeneration + provenance + LLM-no-author gate (Rule G-15.b/.c) |

## Running the tools

```bash
# Validate the workspace parses + conforms to the profile:
./mvnw -f tools/architecture-workspace/pom.xml exec:java \
  -Dexec.args="validate architecture/workspace.dsl"

# Emit normalized JSON (for downstream consumers):
./mvnw -f tools/architecture-workspace/pom.xml exec:java \
  -Dexec.args="normalize architecture/workspace.dsl out/architecture/normalized-model.json"

# Re-emit the 7 generated zone fragments:
java -cp tools/architecture-workspace/target/classes:$(cat /tmp/cp.txt) \
  com.huawei.ascend.tools.architecture.fragment.AllFragmentsCli --repo .

# Re-mirror anchor ADRs into architecture/decisions/:
java -cp tools/architecture-workspace/target/classes:$(cat /tmp/cp.txt) \
  com.huawei.ascend.tools.architecture.fragment.AdrMirrorCli --repo .

# Run the blocking-mode workspace gate (Linux/WSL per Rule G-7):
bash gate/check_architecture_workspace.sh
```

## How to add a new feature / function point / capability

| You want to … | Edit | Then |
|---|---|---|
| Add a new **capability** | `architecture/features/capabilities.dsl` — add an `element "X" "Capability" "..." "SAA Capability"` with required `saa.*` properties | re-emit fragments + validate; gate verifies the saa.id is unique |
| Add a new **function point** | `architecture/features/function-points.dsl` (the function point) + `architecture/features/verification.dsl` (the test edge) | same as above; ADR-link the function point via `saa.sourceAdr` |
| Add a new **module** | (1) add Maven module + populate `<module>/module-metadata.yaml`; (2) add a `container` declaration to `workspace.dsl`; (3) create `architecture/docs/L1/<module>.md` (or `<module>/` directory); (4) repoint `module-metadata.yaml#architecture_doc` | re-emit `generated/modules.dsl`; gate verifies the module appears |
| Add a new **L1 design view** for a module | If the module is on the single-narrative `<module>.md` shape, either extend that file OR graduate to a `<module>/` directory with per-view files | Update `architecture/docs/L1/README.md` to reflect the shape change |

## Lifecycle

- **W0** (2026-05-27, commit `9611096`): spike + ADR-0147 + ADR-0148 (4 PASS gates).
- **W1** (commit `3a8d4fe`): production tooling + profile + advisory gate + 141-rule impact matrix.
- **W2** (commit `ebf7533`): authored zone — 152 capabilities mounted + 15 function points + 7 tests + L1 narrative.
- **W3** (commit `d36303f`): generated zone — 7 fragment emitters + idempotency gate.
- **W4** (commit `8520aaa`): compatibility projection (`workspace -> architecture-workspace-graph.yaml`) + equivalence check.
- **W5** (commit `1026bbc`): authority transfer — Rule G-1.b amended, gate flips advisory → blocking, top-level docs updated.
- **W6** (commit `16cbe27`): YAML sunset roadmap (soak-blocked).
- **W7** (commit `825a8e2`): closure ADR-0149 + family registration + FULL GATE PASS.
- **W8** (current branch): docs consolidation under `architecture/docs/L1/`; ADR-0150 supersedes ADR-0143; `!docs` + `!adrs` directives wired; cross-doc reading-path + rhetorical-stance refresh.

Soak windows (real-world wall-clock; do not skip):
- W5 14-day soak: 2026-05-27 → 2026-06-10.
- W6 sub-wave soaks: 60 days (W6.a..W6.d each 15 days).
- W7 retirement candidates: eligible ~2026-07-25.

W8 lands DURING the W5 soak because it does not change authority direction — it only relocates files into the layout the direction already implies.
