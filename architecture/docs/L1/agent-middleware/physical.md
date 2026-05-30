---
level: L1
view: physical
status: active
authority: "ADR-0101 (deployment loci) + ADR-0073 (Engine Hooks + Runtime Middleware SPI)"
---

# `agent-middleware` — Physical View

## Deployment plane

`deployment_plane: compute_control` (matches
`module-metadata.yaml#deployment_plane`). The module is stateless and
SPI-pure; it exposes no network endpoint and holds no durable state.

## Topology

`deployment_loci: [platform_centric]` — middleware always rides with
whichever modules host the cross-cutting hooks. It is not deployed as a
standalone process; its types are loaded into the engine + service runtime
that fire and consume the hooks.

## Resource model

The module contributes no independent resource footprint: it has no pool,
no scheduler, and no persistence. The runtime cost of a hook is the cost of
its listeners, which is attributed to the hosting module's compute (the
engine for LLM / tool / memory hooks, the orchestrator for suspension /
resume hooks).
