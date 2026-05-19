# L0 Architecture Views

This directory contains the source-of-truth diagram set for the `spring-ai-ascend` L0/C1 4+1 architecture views.

## Purpose

The diagrams provide a reviewable, diffable, long-lived visual companion to the L0 architecture corpus. They are intentionally limited to top-level capability blocks, external systems, system boundaries, deployment planes, and macro relationships. They do not replace the prose architecture documents; they give reviewers a stable visual entry point and reserve links for later L1/L2 drill-down diagrams.

## Why PlantUML + C4-PlantUML

- PlantUML files are plain text, so architecture changes can be reviewed in pull requests and compared over time.
- C4-PlantUML gives a common vocabulary for context, container, dynamic, and deployment views without adding Structurizr as a repository dependency.
- SVG is the primary export format because it preserves clickable drill-down links.
- PNG export is optional for tools that cannot render SVG.

## L0 capability rule

At L0/C1, `spring-ai-ascend` has exactly six internal first-class capability blocks:

1. Agent Client
2. Agent Service
3. Agent Execution Engine
4. Agent Bus
5. Agent Middleware
6. Agent Evolution Layer

Keeping L0 to these six blocks prevents lower-level protocol, implementation, scheduler, state-machine, and test-contract details from being promoted into the top-level architecture. Those details belong in L1/L2 diagrams, relationship labels, legends, or notes.

## View list

| View | Source | Primary export |
| --- | --- | --- |
| Scenario | `plantuml/l0/l0-scenario.puml` | `exports/svg/l0-scenario.svg` |
| Logical | `plantuml/l0/l0-logical.puml` | `exports/svg/l0-logical.svg` |
| Development | `plantuml/l0/l0-development.puml` | `exports/svg/l0-development.svg` |
| Process | `plantuml/l0/l0-process.puml` | `exports/svg/l0-process.svg` |
| Physical | `plantuml/l0/l0-physical.puml` | `exports/svg/l0-physical.svg` |

## Generate locally

The renderer script uses Docker first and does not require a local Java or Graphviz installation.

```bash
bash scripts/render-architecture-views.sh
```

Generate both SVG and PNG:

```bash
bash scripts/render-architecture-views.sh --png
```

Validate rendering without modifying checked-in exports:

```bash
bash scripts/render-architecture-views.sh --check
```

The Docker image can be overridden when needed:

```bash
PLANTUML_DOCKER_IMAGE=plantuml/plantuml:latest bash scripts/render-architecture-views.sh
```

## View exported diagrams

Open the generated SVG files directly from `docs/architecture-views/exports/svg/` in a browser or in any SVG-capable documentation viewer. SVG exports are preferred because the six L0 core capability blocks include placeholder links to future L1 diagrams.

PNG exports, when generated, are written to `docs/architecture-views/exports/png/`.

## Maintain diagram sources

- Edit `.puml` files under `docs/architecture-views/plantuml/`.
- Keep shared style in `plantuml/common/theme.puml`.
- Keep L1 drill-down placeholders in `plantuml/common/links.puml`.
- Keep shared L0 names and descriptions in `plantuml/common/l0-elements.puml`.
- Regenerate exports with `bash scripts/render-architecture-views.sh` after source changes.
- Do not edit generated SVG or PNG files by hand.
- Do not use draw.io, Mermaid, or hand-made images as the primary source.
- Keep terminology aligned with the latest `ARCHITECTURE.md`, `docs/reviews`, and accepted meeting decisions.
- Changes to architectural semantics should go through `docs/reviews`.

## Add L1/L2 drill-down diagrams later

The common link file already reserves these SVG targets:

- `../l1/agent-client/client-logical.svg`
- `../l1/agent-service/service-logical.svg`
- `../l1/execution-engine/execution-logical.svg`
- `../l1/agent-bus/bus-logical.svg`
- `../l1/agent-middleware/middleware-logical.svg`
- `../l1/evolution/evolution-logical.svg`

When an L1 or L2 diagram is added:

1. Create the PlantUML source under a new `plantuml/l1/` or `plantuml/l2/` subtree.
2. Export SVG to the matching `exports/svg/l1/` or `exports/svg/l2/` path.
3. Keep the L0 link variable stable unless the target architecture has been reviewed and renamed.
4. Do not backfill lower-level implementation objects into L0 just because a drill-down exists.

## Abstraction-level rules

- L0 only shows capability blocks, external systems, boundaries, deployment planes, and macro relationships.
- L1/L2 protocol objects, concrete service-provider classes, state machines, schedulers, mailbox mechanisms, and test contracts must not be promoted to L0 components.
- Lower-level concepts may appear only as relationship labels, legend text, notes, or drill-down entry descriptions.
- L0 diagrams should stay readable and should not become implementation inventories.

## Naming rules

- The L0 capability name is **Agent Service**.
- Repository or module names may still reference the deleted module names `agent-platform`, `agent-runtime`, and `agent-runtime-core` when describing pre-rc13 code layout in historical context — current layout names `agent-service` + `agent-execution-engine` + `agent-bus` (post-ADR-0078 / post-ADR-0088 dissolution).
- L0 architectural diagrams should not use the retired **Agent Runtime** wording as a core capability name.

## Gate

An optional standalone gate is provided:

```bash
bash gate/check_architecture_views.sh
```

It checks required files, rejects disallowed diagram-source formats and lower-level L0 component promotion, verifies the six capability names, and invokes the renderer in `--check` mode.
