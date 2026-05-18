---
level: L1
view: logical
module: agent-evolve
status: skeleton
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0075 (Evolution scope default boundary); Layer-0 principle P-I (Five-Plane Distributed Topology); Rule 47 (Evolution Scope Default Boundary)"
---

# agent-evolve — L1 architecture (skeleton)

> Owner: AgentEvolve team | Wave: W3+ | Maturity: skeleton (deferred)
> Created: 2026-05-17 (six-module materialization PR)

## Status

**This module is a deferred skeleton.** The Evolution plane hosts
Python ML / offline improvement loops; the Java side is just an adapter
shell. Bulk implementation is deferred indefinitely per
`CLAUDE-deferred.md` and the archived design under
`docs/v6-rationale/agent-runtime/evolve/`.

What *is* shipped today: the `EvolutionExport` discriminator
(`IN_SCOPE | OUT_OF_SCOPE | OPT_IN`) declared in
`docs/governance/evolution-scope.v1.yaml` (Rule 47 / P-M, gate Rule 59).
Pre-Phase-C this lived in `agent-runtime/evolution/`; post-ADR-0078 / ADR-0079
the EvolutionExport discriminator is consumed by `agent-service` (the
consolidated runtime module) and the eventual Java adapter for the evolution
plane will land here in `agent-evolve` once the W3 evolution scope ships.

## 0.4 Layered 4+1 view map

Only the **logical** view is meaningful at this stage.

| Section | View | Notes |
|---|---|---|
| §1 Role | logical | Evolution plane Java adapter |
| §2 Scope | logical | EvolutionExport discriminator + telemetry-export ref |

## 1. Role (target)

`agent-evolve` will be the **Java-side adapter** between the runtime's
emitted `RunEvent`s and the Python ML pipeline. It will:

- Honour the `EvolutionExport` discriminator (in-scope events flow to
  the evolution plane; out-of-scope events stay on the
  compute/control plane).
- Forward opt-in events to the future `telemetry-export.v1.yaml`
  contract (W3 placeholder).
- Provide health probes the Python ML pipeline can observe.

## 2. Forbidden today

- No Python in this module — Python lives in a sibling sub-project not
  managed by Maven.
- No direct LLM gateway calls — evolution is offline.
- No runtime-state mutations — read-only consumer of emitted events.

## Reading order for new contributors

1. `module-metadata.yaml` — identity + dependency promises.
2. `docs/governance/evolution-scope.v1.yaml` — discriminator schema.
3. `docs/dfx/agent-evolve.yaml` — Design-for-X declarations.
4. ADR-0075 + `docs/v6-rationale/agent-runtime/evolve/` — historical
   design notes.
