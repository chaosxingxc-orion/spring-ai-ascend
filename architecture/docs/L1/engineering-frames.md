---
level: L1
view: logical
status: active
authority: "ADR-0157 (EngineeringFrame Ontology)"
---

# EngineeringFrames — the structural axis

This document is the canonical narrative for the **dual-track architecture model**
introduced by ADR-0157. Read it *before* the feature corpus: it gives AI agents and
engineers a stable structural anchor (`Module → EngineeringFrame → FunctionPoint`) so
that feature work does not drift into a user-story inventory.

## Two axes, one join point

The architecture model separates two axes that were previously conflated through the
overloaded `SAA Feature` tag:

- **Engineering / structure axis** — durable. `Module → EngineeringFrame → FunctionPoint`.
- **Product / value axis** — demand-driven. `ProductClaim → Feature → FunctionPoint`.

They meet at **FunctionPoint** (the join point). An `EngineeringFrame` is a *durable
module-internal responsibility slice that anchors function points*. It is the engineering
counterpart to a `Feature`: where a Feature is a value thread (why we build), a Frame is a
structural carrier (where the behaviour lives). Frames are **claim-agnostic** — product
claims bind to the value axis (Feature), not to structure.

Relationship vocabulary (`architecture/profile/relationship-types.yaml`):

```text
Module        --contains--> EngineeringFrame        (structural ownership)
EngineeringFrame --anchors--> FunctionPoint          (a frame anchors a behaviour)
Feature       --traverses--> EngineeringFrame        (a value thread crosses the map)
Feature       --requires/contains--> FunctionPoint   (a value thread drives behaviours)
```

## The structural map (all 7 domain modules)

Frames are authored in [`../../features/engineering-frames.dsl`](../../features/engineering-frames.dsl)
(agent-service frame *elements* live in `features.dsl`, re-tagged from the ADR-0138 Layer
features; their edges are in `engineering-frames.dsl`).

| Module (plane) | EngineeringFrames |
|---|---|
| `agent-runtime` (compute_control) | `EF-ACCESS-ADMISSION`, `EF-SESSION-TASK-STATE`, `EF-TASK-CONTROL`, `EF-ENGINE-DISPATCH` (design-only), `EF-INTERNAL-EVENT-QUEUE` (design-only), `EF-TRANSLATION-INTERCEPT` (design-only), `EF-ENGINE-REGISTRY` |
| `agent-bus` (bus_state) | `EF-INGRESS-GATEWAY`, `EF-S2C-TRANSPORT`, `EF-CHANNEL-ISOLATION` (design-only), `EF-ENGINE-PORT` (design-only), `EF-ORCHESTRATION-SPI` (design-only) |
| `agent-service` (serviceization façade) | registration/discovery frames deferred (ADR-0159) |
| `agent-middleware` (compute_control) | `EF-HOOK-SURFACE`, `EF-CAPABILITY-SPI` |
| `agent-client` (edge, skeleton) | `EF-CLIENT-INGRESS-ADAPTER` |
| `agent-evolve` (evolution, skeleton) | `EF-EVOLUTION-EXPORT` |
| `spring-ai-ascend-graphmemory-starter` (bus_state) | `EF-GRAPHMEMORY-AUTOCONFIG` |

Every shipped FunctionPoint anchors to exactly one primary frame (e.g. `FP-CREATE-RUN →
EF-ACCESS-ADMISSION`, `FP-ENGINE-DISPATCH → EF-ENGINE-REGISTRY`, `FP-HOOK-DISPATCH →
EF-HOOK-SURFACE`). Design-only frames may anchor no shipped FunctionPoint yet.

## The dual-track operating model

Both tracks share the same Frame + FunctionPoint map. They differ in **direction** and
**authority**: Track A *draws* the map; Track B *reads* it.

```text
Track A — Product Development (structure-building; design-state; ADR-gated)
Product Definition (ProductClaim)
  -> Technical Architecture (workspace.dsl / L0 §4 constraints)
    -> Module -> EngineeringFrame   [minting a NEW frame is an architecture decision -> ADR]
      -> FunctionPoint -> Contract -> CodeFact/TestFact (Rule G-15)
        -> Integration + System Verification -> Customer Release

Track B — Demand Response (structure-preserving; demand-driven)
Customer Demand
  -> Product Feature (MUST resolve to a ProductClaim, else mark out-of-scope)
    -> Feature decomposition (FeatureSlice)
      -> select / add FunctionPoint, anchored onto an EXISTING EngineeringFrame
        -> Contract -> Code/Test -> Verification / Acceptance
        -> cannot land on an existing frame? STOP -> ADR -> enters Track A
```

The load-bearing invariant: **Track A may create EngineeringFrames; Track B must land on an
existing one.** A demand that needs new structure escalates to an architecture decision
rather than silently reshaping the architecture.

## AI / engineer reading path

Read in this order so the durable anchor is understood before the demand list:

1. `product/PRODUCT.md` + `product/claims.yaml` — intent and claims.
2. `architecture/facts/generated/*.json` — ground-truth facts (Rule G-15).
3. `architecture/workspace.dsl` — module topology + planes.
4. **`architecture/docs/L1/engineering-frames.md` (this file) + `engineering-frames.dsl`** — the structural map.
5. Per-module L1 docs.
6. `architecture/features/function-points.dsl` — executable, verifiable behaviours.
7. `architecture/features/features.dsl` — value threads (Features) over the map.
8. `docs/contracts/` — runtime promises.
9. Code and tests.

## Authority

- ADR-0157 — EngineeringFrame Ontology (this concept).
- ADR-0158 — EnginePort Transport-Agnostic Boundary (re-homes `EF-ORCHESTRATION-SPI` from `agent-runtime` to `agent-bus`; adds `EF-ENGINE-PORT` owned by `agent-bus`).
- ADR-0138 — agent-service five-layer L1 ratification (the six re-tagged frames originate here).
- ADR-0147 — Structurizr Workspace Authority (profile tag + relationship home).
- ADR-0156 — Product Authority and Traceability (claims bind to the value axis).
- Profile: `architecture/profile/{profile,required-properties,relationship-types}.yaml`.
