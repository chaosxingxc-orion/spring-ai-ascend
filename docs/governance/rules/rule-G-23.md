---
rule_id: G-23
title: "Shipped-Frame Anchor Integrity"
level: L1
view: logical
principle_ref: P-C
authority_refs: [ADR-0157, ADR-0158]
enforcer_refs: [E188]
status: active
scope_phase: design
kernel_cap: 8
governance_infra: true
scope_surfaces:
  - architecture/features/engineering-frames.dsl
  - architecture/features/features.dsl
  - gate/frame-shipped-zero-anchor-allowlist.txt
  - gate/lib/check_frame_shipped_anchors.py
kernel: |
  Every `SAA EngineeringFrame` element with `saa.status "shipped"` MUST have at least one `anchors` relationship edge to a FunctionPoint in `architecture/features/engineering-frames.dsl`, else FAIL — unless allowlisted in `gate/frame-shipped-zero-anchor-allowlist.txt` (ADR-backed exceptions; ships empty). Frame elements are authored across BOTH `engineering-frames.dsl` and `features.dsl`; the `anchors` edges live in `engineering-frames.dsl`.
---

# Rule G-23 — Shipped-Frame Anchor Integrity

## What

A frame marked `shipped` claims a delivered, durable responsibility slice. That
claim is only true if the frame anchors at least one FunctionPoint — the unit of
work the frame is responsible for. A shipped frame with zero anchored function
points is a structural lie.

## Why

Closes the 2026-05-29 review finding F8.3. The prior wave demoted four
zero-anchor frames to `design_only`; this rule makes that discipline permanent
by failing closed whenever a `shipped` frame loses (or never gains) its anchors
edge.

## How it works

`gate/lib/check_frame_shipped_anchors.py` parses the EngineeringFrame elements
from both DSL files (the agent-service Layer features are re-tagged as frames in
`features.dsl`) and the `anchors` edges from `engineering-frames.dsl`. Any
shipped frame whose element var is not the source of at least one `anchors` edge
fails — unless its var or `saa.id` is listed in
`gate/frame-shipped-zero-anchor-allowlist.txt`.

## Allowlist

`gate/frame-shipped-zero-anchor-allowlist.txt` ships EMPTY. Entries are
ADR-backed exceptions only; an empty list means the rule fails closed on the
next zero-anchor regression.

## Test fixtures

  - INVALID: a synthetic shipped frame with no anchors edge fails.
  - VALID  : the current corpus passes (every shipped frame anchors >=1
             FunctionPoint; zero-anchor frames are design_only).

## Cross-references

  - ADR-0157 — EngineeringFrame Ontology
  - Rule G-22 — Accepted-ADR Frame-Map Coherence (sibling structural-axis rule)
