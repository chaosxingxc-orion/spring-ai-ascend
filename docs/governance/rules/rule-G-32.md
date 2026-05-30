---
rule_id: G-32
title: "AI Understanding Map Derivation Integrity"
level: L1
view: scenarios
principle_ref: P-C
authority_refs: [ADR-0157]
enforcer_refs: [E199]
status: active
scope_phase: design
kernel_cap: 8
governance_infra: true
scope_surfaces:
  - architecture/mappings/ai-understanding-map.yaml
  - architecture/mappings/ai-understanding-map.md
  - architecture/features/features.dsl
  - architecture/features/function-points.dsl
  - architecture/features/engineering-frames.dsl
  - gate/ai-understanding-map-allowlist.txt
  - gate/lib/check_ai_understanding_map.py
kernel: |
  The explicit dual-track understanding map under `architecture/mappings/` — the single readable projection that joins the VALUE axis (ProductClaim → Requirement → Feature → FunctionPoint), the STRUCTURE axis (Module → EngineeringFrame → FunctionPoint), and the EVIDENCE axis (FunctionPoint → Contract → Generated Fact → Gate), plus the derived Feature → traverses → EngineeringFrame reconciliation — is a READABLE-INTERPRETATION layer that invents no id and no relationship and never outranks a surface it reads (cascade: generated facts > DSL > Card/prose). Rule G-32 asserts the two axes the map joins stay DERIVED, never OWNED, over the authored Structurizr fragments `architecture/features/features.dsl` (Feature elements + Feature→FunctionPoint `requires` edges, the value axis), `architecture/features/function-points.dsl` (FunctionPoint elements), and `architecture/features/engineering-frames.dsl` (EngineeringFrame elements + Module→Frame `contains`, Frame→FunctionPoint `anchors`, and Feature→Frame `traverses` edges, the structure axis) — the agent-service Frame ELEMENTS are authored in features.dsl while their `contains`/`anchors` edges live in engineering-frames.dsl, so all three files are merged before the map is read (mirroring `check_frame_card_consistency.py`). It runs three checks: DERIVED TRAVERSE — every `Feature --traverses--> Frame` edge MUST be DERIVABLE from a shared FunctionPoint (the Feature `requires` some FunctionPoint the Frame `anchors`); a Frame that anchors NO FunctionPoint yet (a still-`design_only` structural placeholder) has nothing to derive from, so a traverse onto it is vacuously permitted, but the instant a Frame anchors any FunctionPoint a Feature that traverses it MUST share one — the value↔structure link can never be invented over a populated Frame (`NON-DERIVED-TRAVERSE`; blocking only for `shipped` source Features, advisory-only otherwise even under full-blocking); NO OWNERSHIP OF A FRAME — a Frame is `contains`-owned by exactly one Module and nothing else, so a Feature that is the source of a `contains`/`anchors`/`owns` edge into a Frame (`FEATURE-OWNS-FRAME`), a `contains` edge into a Frame whose source is not a `genModule_*` Module element (`NON-MODULE-CONTAINS-FRAME`), or a Frame element carrying a value-axis property `saa.productClaim`/`saa.requirement` (`FRAME-OWNS-VALUE` — ProductClaim and Requirement are value-axis identifiers, not graph elements, so forbidding the property is the structural form of "no ProductClaim/Requirement owns a Frame") are all findings; WELL-TYPED AXES — an `anchors` edge goes Frame→FunctionPoint and a `requires` edge goes Feature→FunctionPoint, and a mis-wired hop that would silently corrupt the derivation is a `MALFORMED-EDGE`. The single gate Rule 149 invokes `gate/lib/check_ai_understanding_map.py` (E199), reading the DSL elements + edges as the identity authority. The three DSL files are the only required inputs; when NONE exists the check is vacuously clean in every mode (greenfield — the map has not been authored yet), but the instant any exists it MUST be readable or the check fails closed (exit 2) in EVERY mode including advisory — a missing authority is never an advisory condition. ADR-backed exceptions are listed (one `feat*`/`ef*` var or `FEAT-*`/`EF-*` saa.id, or a `src->dst` edge pair, per line, each with a trailing ADR citation) in `gate/ai-understanding-map-allowlist.txt`; that file ships empty (the live map satisfies every invariant). Runs ADVISORY at this rung per the ADR-0157 dual-track ratchet (advisory → changed-files-blocking → full-blocking, the terminal rung once the map is clean): findings are reported to the gate log and never block. Under `changed-files-blocking` a finding blocks only when one of the three authoring DSL files changed relative to `--base` (default `origin/main`, else `HEAD`) — the map is a single shared surface, so a change to any of the three re-scopes the whole map. `full-blocking` blocks on any in-scope finding (NON-DERIVED-TRAVERSE from a not-yet-shipped Feature stays advisory even here). A missing helper fails closed; a missing python interpreter is a vacuous pass (Rule G-7 lists WSL as the canonical env).
---

# Rule G-32 — AI Understanding Map Derivation Integrity

## What

Asserts that the explicit **dual-track understanding map** under
`architecture/mappings/` — the single readable projection that joins the three
axes the architecture is built on, so an AI (or a human) can answer *"what is
this, why does it exist, and what proves it"* about any FunctionPoint in one hop
— keeps its two graph axes **DERIVED, never OWNED**. The map is the surface named
at `docs/governance/ai-reading-path.yaml` step 5 (`demand_to_behavior_mapping`);
it joins:

- **VALUE** — ProductClaim → Requirement → Feature → FunctionPoint, the
  Feature → FunctionPoint hop carried by a `requires` edge.
- **STRUCTURE** — Module → EngineeringFrame → FunctionPoint, the Module → Frame
  hop carried by `contains` and the Frame → FunctionPoint hop carried by
  `anchors`.
- **EVIDENCE** — FunctionPoint → Contract → Generated Fact → Gate, cited by
  fact ID.

The two value/structure axes meet ONLY at the FunctionPoint and ONLY through a
**derived** `Feature --traverses--> EngineeringFrame` edge: a value demand is read
as a route across the structural map. A `traverses` edge is a derived view of
"this Feature's work lands in that Frame", NEVER a statement that the Feature (or
its ProductClaim / Requirement) OWNS the Frame. Frames are CLAIM-AGNOSTIC; product
value binds to the value axis (Feature), not to the structural slice it crosses.
This rule reads the authored DSL, never mints it (cascade: generated facts > DSL >
Card/prose — the map sits at the lowest interpretation tier and never overrides a
surface it reads).

The rule runs three checks over the merged DSL:

- DERIVED TRAVERSE. Every `Feature --traverses--> Frame` edge MUST be derivable
  from a shared FunctionPoint: the Feature `requires` some FunctionPoint that the
  Frame `anchors`. A Frame that anchors no FunctionPoint yet (a still-`design_only`
  structural placeholder) has nothing to derive from, so a traverse onto it is
  vacuously permitted — but the instant a Frame anchors any FunctionPoint, a
  Feature that traverses it MUST share one, so the value↔structure link can never
  be invented over a populated Frame. → `NON-DERIVED-TRAVERSE` (blocking only for a
  `shipped` source Feature; a traverse from a not-yet-shipped Feature is reported
  but advisory-only even under `full-blocking` — its structure is still settling).
- NO OWNERSHIP OF A FRAME. A Frame is `contains`-owned by exactly one Module and
  by nothing else: a Feature that is the source of a `contains`/`anchors`/`owns`
  edge into a Frame is the sharper `FEATURE-OWNS-FRAME`; a `contains` edge into a
  Frame whose source is not a `genModule_*` Module element is
  `NON-MODULE-CONTAINS-FRAME`; a Frame element that carries a value-axis property
  (`saa.productClaim` / `saa.requirement`) is `FRAME-OWNS-VALUE` (a ProductClaim /
  Requirement is a value-axis identifier, not a graph element, so the only way one
  can "own" a Frame is by appearing as a Frame property — forbidding that property
  is the structural form of "no ProductClaim / Requirement owns a Frame").
- WELL-TYPED AXES. The hops the derivation relies on must connect the declared
  kinds: an `anchors` edge goes Frame → FunctionPoint and a `requires` edge goes
  Feature → FunctionPoint. A mis-wired hop is a `MALFORMED-EDGE` (it would silently
  corrupt the derivation).

The authority direction is fixed and one-way; the rule reads the authored DSL and
asserts none of its own:

    ADR-0157 (EngineeringFrame Ontology — dual-track value/structure axes)
      -> architecture/features/{features,function-points,engineering-frames}.dsl  (the axes)
        -> architecture/mappings/ai-understanding-map.{yaml,md}                    (the readable join)
          -> gate Rule 149 / E199                                                 (this check)

## Why

ADR-0157 makes the dual-track model the binding structural axis: a Feature
`traverses` an EngineeringFrame, it never owns it, and an EngineeringFrame is
claim-agnostic. The 2026-05-29 engineering-governance systemic-remediation review
accepted the explicit understanding map (the one-hop "what / why / proof"
projection) only with a standing guard — without one, the map (or the DSL it
mirrors) could quietly let a value thread fabricate a structural route it does not
take (a `traverses` with no shared FunctionPoint), let a Feature or a ProductClaim
"own" a structural Frame (collapsing the two axes the model exists to keep
separate), or mis-wire an `anchors` / `requires` hop that silently corrupts the
derivation — and the corpus would claim a dual-track discipline it had not earned.
This rule closes those risks structurally: a non-derived traverse over a populated
Frame, a Feature/value-axis ownership of a Frame, a non-Module container, and a
malformed axis hop are findings the gate reports (and, at the blocking rungs, a PR
may not ADD), rather than silent corruptions of the map. The map stays subordinate
to the authority spine — it records the JOIN over the axes; it never asserts a
fact, and it never outranks a surface it reads (cascade: generated facts > DSL >
Card/prose).

## How it works

The single gate Rule 149 invokes one helper:

- `gate/lib/check_ai_understanding_map.py` (E199) — merges the three authoring DSL
  files, projects the dual-track map model (Feature / Frame / FunctionPoint
  elements; `requires` / `anchors` / `traverses` / `contains` / `owns` edges), and
  runs the three checks above. It reports, subject-oriented, a short machine code
  per finding (`NON-DERIVED-TRAVERSE`, `FEATURE-OWNS-FRAME`,
  `NON-MODULE-CONTAINS-FRAME`, `FRAME-OWNS-VALUE`, `MALFORMED-EDGE`). It invents no
  ID and no relationship and never outranks a generated fact — it is a classifier
  over the declared dual-track map.

Allowlist. ADR-backed exceptions are listed (one `feat*` / `ef*` var or `FEAT-*` /
`EF-*` saa.id, or a `src->dst` edge pair, per line, each with a trailing ADR
citation the header mandates) in `gate/ai-understanding-map-allowlist.txt`; an
entry suppresses any finding whose subject equals it (or the saa.id of either
endpoint of an edge subject). That file ships empty — the live map satisfies every
invariant.

Greenfield / vacuity posture. The three map DSL files are the only required
inputs; when NONE of them exists the check is vacuously clean in every mode (the
map has not been authored yet — greenfield). The instant any exists it MUST be
readable, or the check fails closed (exit 2) in EVERY mode including advisory — a
map cannot be judged against authorities that vanished, so a missing authority is
never an advisory condition.

## Ratchet

advisory (THIS rung: findings are reported to the gate log and never block) →
changed-files-blocking (a PR may not ADD a finding once it touches the map's
authoring surfaces — any of the three DSL files; because the map is a single shared
surface, a change to any of them re-scopes the whole map; the scope derives from
`--base`, default `origin/main`, else `HEAD` — the same git-deriving pattern as
Rule 145 / E194 `check_layer_purity.py`, Rule 146 / E196
`check_frame_card_consistency.py`, Rule 147 / E197 `check_feature_readiness.py`,
and Rule 148 / E198 `check_ai_reading_path.py`) → full-blocking (the terminal
posture once the map is clean; a `NON-DERIVED-TRAVERSE` from a not-yet-shipped
Feature stays advisory even here). The helper `--mode` flags (`advisory` /
`changed-files-blocking` / `full-blocking`) implement the rungs. A missing helper
fails closed; a missing python interpreter is a vacuous pass (Rule G-7 lists WSL as
the canonical env).

## Test fixtures

  - VALID  : an absent map (none of the three DSL files) is vacuously clean
             (greenfield) — every mode passes with zero findings.
  - VALID  : a clean dual-track map (Module `contains` two Frames; one shipped
             Frame `anchors` a FunctionPoint a shipped Feature `requires` and
             `traverses`; a design_only Frame that anchors nothing is traversed
             vacuously) passes full-blocking with zero findings.
  - VALID  : a `traverses` onto a Frame that anchors no FunctionPoint yet produces
             no finding (a design_only structural placeholder).
  - INVALID: a shipped Feature `traverses` a populated Frame it shares no
             FunctionPoint with yields `NON-DERIVED-TRAVERSE` and blocks
             full-blocking.
  - VALID  : the same non-derived shape from a not-yet-shipped Feature is reported
             but never blocks (advisory even under full-blocking).
  - INVALID: a Feature that is the source of a `contains` / `anchors` edge into a
             Frame yields `FEATURE-OWNS-FRAME`.
  - INVALID: a `contains` edge into a Frame from a non-`genModule_*` source yields
             `NON-MODULE-CONTAINS-FRAME`.
  - INVALID: a Frame carrying `saa.productClaim` / `saa.requirement` yields
             `FRAME-OWNS-VALUE`.
  - INVALID: an `anchors` edge whose target is not a FunctionPoint yields
             `MALFORMED-EDGE`.
  - VALID  : an allowlist entry (by var or by saa.id) suppresses the matching
             finding.
  - VALID  : advisory mode reports findings but never blocks (exit 0) — the
             first-cleanup-wave posture.
  - INVALID: a present-but-unreadable DSL file fails closed (exit 2) in every mode
             including advisory — a missing/broken authority is never advisory.

## Cross-references

  - ADR-0157 — EngineeringFrame Ontology (the authority this rule enforces: the
    dual-track value/structure axes, the derived `Feature --traverses--> Frame`
    reconciliation, and the claim-agnostic Frame)
  - ADR-0161 — EngineeringFrame package-cluster anchor + Card-over-DSL (the
    readable-interpretation discipline this map and this check share: invent
    nothing, never outrank the DSL or the facts)
  - ADR-0154 — Fact-Layer Authority (the cascade `generated facts > DSL >
    Card/prose` the map never outranks; the EVIDENCE axis cites generated facts by
    ID)
  - Rule G-31 — AI Reading Path Integrity (the sibling reading-path gate; that one
    guards the reading ORDER over the lanes, this one guards the JOIN's derivation
    integrity — the map this rule guards is step 5 of the path that rule guards)
  - Rule G-30 — FunctionPoint Readiness (the per-node completeness gate over the
    same eight-node chain; the ownership invariant "only an EngineeringFrame may
    anchor a FunctionPoint" is the FunctionPoint-side mirror of this rule's
    Frame-side "a Feature may only traverse, never own, a Frame")
  - Rule G-29 — Frame-Card / DSL Parity (the Frame-Card consistency gate; both
    pin a readable surface to the DSL it mirrors and forbid it inventing IDs)
  - Rule G-7 — Linux-First Dev Environment (the helper is run via WSL/Linux; a
    missing python interpreter is a vacuous pass)
