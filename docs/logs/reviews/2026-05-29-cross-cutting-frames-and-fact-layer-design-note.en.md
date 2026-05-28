---
affects_level: L1
affects_view: [logical, development]
proposal_status: design-note
authors: ["Claude (autonomous EngineeringFrame execution)"]
related_sources:
  - docs/adr/0157-engineering-frame-ontology.yaml
  - architecture/features/engineering-frames.dsl
  - architecture/facts/generated/
  - docs/adr/0154-fact-layer-authority.yaml
---

# Design note — cross-cutting EngineeringFrames + frame fact-layer (P7)

This is the **P7 design-state record**. It resolves two open questions left by
ADR-0157 and specifies a post-v1.0 enhancement. No DSL/profile/gate change is
made here (design phase); it is the design that a future ADR would ratify.

## 1. How are cross-cutting concerns framed?

Concerns such as **audit / compliance, model-gateway, memory, and evolution**
span several modules. The doc that motivated ADR-0157 warned these "pull module
boundaries back and forth." Question: do they get a single cross-module frame?

**Decision: NO single cross-module frame.** An `EngineeringFrame` is *by
definition* a module-internal responsibility slice (`Module --contains-->
EngineeringFrame`). A concern that spans modules is therefore modelled as **one
frame per participating module, linked by a shared Contract** (the contract is
the cross-module seam; the frames are the per-module anchors). This keeps frames
module-local (so module dependency direction and the five-plane topology stay
enforceable) while the contract carries the cross-cutting promise.

Worked mappings (using existing frames + contracts):

| Concern | Per-module frames | Shared contract seam |
|---|---|---|
| Audit / compliance | `EF-AUDIT-COMPLIANCE` (agent-service, proposed by the P5 pilot) + export via `EF-EVOLUTION-EXPORT` (agent-evolve) | `audit-trail.v1.yaml`, `iam-bridge.v1.yaml` |
| Model gateway | `EF-CAPABILITY-SPI` (agent-middleware) + `EF-TRANSLATION-INTERCEPT` (agent-service) | `model-invocation.v1.yaml`, `model-streaming.v1.yaml` |
| Memory / knowledge | `EF-CAPABILITY-SPI` (agent-middleware) + `EF-GRAPHMEMORY-AUTOCONFIG` (graphmemory-starter) | `memory-store.v1.yaml`, `vector-store.v1.yaml` |
| Evolution | `EF-EVOLUTION-EXPORT` (agent-evolve) + hook taps in `EF-HOOK-SURFACE` (agent-middleware) | `run-event.v1.yaml` |

Consequence: a cross-cutting concern is traceable as a *set* of `anchors` edges
(one per module frame) plus the contract(s) those FunctionPoints are
`specified_by`. No new relationship type or ontology element is required.

## 2. Frame fact-layer (post-v1.0 enhancement)

Today EngineeringFrames are **authored** (in `engineering-frames.dsl` /
`features.dsl`), not **fact-extracted**. To make frames fact-grounded under the
Rule G-15 / ADR-0154 fact-layer, extend `ExtractFactsCli` with a
`EngineeringFrameFactExtractor` emitting `architecture/facts/generated/engineering-frames.json`:

```text
engineering-frames.json (proposed schema)
  frames[]:
    id            EF-...
    module        owning module short name (from saa.owner)
    status        shipped | design_only
    anchored_fps  [FP-...]   (derived from `anchors` edges)
    traversed_by  [FEAT-...] (derived from `traverses` edges)
```

This is a *derived* fact (computed from the authored DSL edges), so it would be
"DO NOT EDIT" generated and byte-identity-checked like the other fact files.
Value: AI sessions can query frame→FP coverage without parsing the DSL.
**Deferred to post-v1.0** (the authored DSL is sufficient for v1.0; the
extractor is an ergonomics enhancement, not a correctness requirement).

## 3. Optional: `saa.contractRefs` on frames (P7.3)

A frame could optionally declare `saa.contractRefs` listing the contracts its
anchored FunctionPoints fulfil — making the frame→contract seam explicit on the
frame element rather than only derivable through FunctionPoints. **Deferred**:
the FunctionPoint→Contract edges already carry this; adding it to frames is
redundant until a renderer or query needs the shortcut.

## Status

Design recorded. Items 2 and 3 are explicitly post-v1.0. Item 1 (cross-cutting =
per-module frames + shared contract) is the operative decision and is already
how the authored map is structured.
