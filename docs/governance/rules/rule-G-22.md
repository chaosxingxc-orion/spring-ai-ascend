---
rule_id: G-22
title: "Accepted-ADR Frame-Map Coherence"
level: L1
view: logical
principle_ref: P-C
authority_refs: [ADR-0157, ADR-0158]
enforcer_refs: [E187]
status: active
scope_phase: design
kernel_cap: 8
governance_infra: true
scope_surfaces:
  - docs/adr/0158-engine-port-transport-agnostic-boundary.yaml
  - architecture/features/engineering-frames.dsl
kernel: |
  When an ADR with `status: accepted` declares an EngineeringFrame re-home or a new frame in its decision text, `architecture/features/engineering-frames.dsl` MUST reflect it. The live case (ADR-0158): the DSL MUST declare `EF-ENGINE-PORT` with `saa.owner "agent-bus"` AND `EF-ORCHESTRATION-SPI` with `saa.owner "agent-bus"` placed under a `genModule_agent_bus -> efOrchestrationSpi` `contains` edge.
---

# Rule G-22 — Accepted-ADR Frame-Map Coherence

## What

Ties the structural axis (Module -> EngineeringFrame -> FunctionPoint) to the
accepted-ADR decision record. An accepted ADR that decides where an
EngineeringFrame lives is the authority; the frame map
(`architecture/features/engineering-frames.dsl`) is the projection that AI
agents traverse. They must agree.

The check is a targeted assertion keyed off ADR-0158: it verifies the
`EF-ENGINE-PORT` frame is present with owner `agent-bus`, and that
`EF-ORCHESTRATION-SPI` (owner `agent-bus`) is contained by the
`genModule_agent_bus` module element.

## Why

Closes the 2026-05-29 review finding F1.4. ADR-0158 re-homed the neutral
orchestration/engine SPI into `agent-bus`; without a gate, the frame map could
drift from the decision and an agent reading the structural axis would be
pointed at a frame owner that the accepted ADR no longer endorses.

## How it works

The gate locates the ADR-0158 YAML, confirms it is `accepted`, then asserts the
two frame elements and the `contains` edge exist in the frame DSL. The rule is
vacuously satisfied before ADR-0158 lands as YAML or while it is not yet
accepted.

## Test fixtures

  - INVALID: a synthetic `engineering-frames.dsl` missing `EF-ENGINE-PORT` fails.
  - VALID  : the current corpus passes (both frames present, owner agent-bus,
             contains edge in place).

## Cross-references

  - ADR-0157 — EngineeringFrame Ontology
  - ADR-0158 — transport-agnostic EnginePort boundary
  - Rule G-23 — Shipped-Frame Anchor Integrity (sibling structural-axis rule)
