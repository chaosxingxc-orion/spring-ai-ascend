---
affects_level: L1
affects_view: [logical]
proposal_status: design-note
authors: ["Claude (autonomous EngineeringFrame execution)"]
related_sources:
  - docs/adr/0157-engineering-frame-ontology.yaml
  - architecture/profile/relationship-types.yaml
  - tools/architecture-workspace/src/main/java/com/huawei/ascend/tools/architecture/ProfileValidator.java
  - docs/governance/templates/contract-architecture-design.md.j2
---

# Design note — EngineeringFrame Integrity rule (P6)

This is the **P6 design-state record**. It specifies the EngineeringFrame-integrity
rule (provisional id **G-22**) and its activation plan. The rule is **not authored
as an active rule card in this branch** — see "Why deferred" — but its invariants
and wiring are fully specified here so a dedicated PR can land it mechanically.

## The rule (provisional G-22 — "EngineeringFrame Integrity")

Invariants:

1. Every `SAA EngineeringFrame` declares an owning module (`saa.owner` resolving to
   a `SAA Module` saa.id).
2. Structural axis uses only `contains` (Module → EngineeringFrame) and `anchors`
   (EngineeringFrame → FunctionPoint); the value axis reaches a frame only via
   `traverses` (Feature → EngineeringFrame). A Feature must not `contains` a
   FunctionPoint (it `requires` it) and a Module must not `anchors` (it `contains`).
3. Every **shipped** `SAA FunctionPoint` anchors to ≥1 EngineeringFrame.

## Enforcement tiers

- **Tier 1 — structural validity — ALREADY BLOCKING.** Invariants 1 and 2 are
  enforced today by the workspace gate (`gate/check_architecture_workspace.sh` →
  `ProfileValidator`, W5+ blocking, ADR-0147): a frame missing required properties,
  or any relationship using an unregistered `saa.rel` type, fails the gate now. The
  EngineeringFrame ontology shipped (P0-P4) is therefore not unguarded.
- **Tier 2 — coverage — TO BE ADVISORY THEN BLOCKING.** Invariant 3
  (shipped-FunctionPoint-has-an-anchor) is the new check. It should land advisory,
  then promote to blocking after a 14-day soak (mirroring the ADR-0151→0153 /
  ADR-0156 ratchets) and after the P5 audit-trail pilot (already recorded) confirms
  the model survives a real decomposition cycle.

## Activation plan (dedicated PR)

1. Author `docs/governance/rules/rule-G-22.md` (status: active, scope_phase: design,
   level: L1, principle_ref: P-C, authority_refs: [ADR-0157], governance_infra: true,
   product_claim: governance_infra).
2. Add the G-22 row to the **template** `docs/governance/templates/contract-architecture-design.md.j2`
   (the phase-contract Active-Rules table is a `phase_contract_table` templated
   surface — the `.md.j2` is the edit surface, NOT the rendered `.md`), then
   re-render via `gate/lib/render_template.py` + `load_render_context.py phase_contract_table`
   so `docs/governance/contracts/architecture-design.md` regenerates byte-identical
   (Rule G-13.b). This also satisfies Rule G-11 (no orphan rule card).
3. Bump baselines in lockstep: `active_engineering_rules` 50→51,
   `workspace_elements` +1 (the rule node in `rules.dsl`), plus README's
   active-engineering-rules cell (Rule 82).
4. Implement the Tier-2 coverage check (every shipped FP has an `anchors` edge) as a
   gate rule + a `gate/test_architecture_sync_gate.sh` self-test; land advisory.
5. After the 14-day soak, promote Tier-2 to blocking.

## Why deferred (not done in this branch)

The active-rule-card integration requires editing the **rendered** phase-contract
template machinery (`phase_contract_table` → 5 phase contracts share the render
path) and a multi-surface baseline lockstep. Doing that on top of the already-large
EngineeringFrame branch risked the byte-identical render gate across several
rendered documents. Tier-1 enforcement (the load-bearing structural guard) is
already live via the workspace gate, so deferring the rule-card paperwork + the
soak-gated Tier-2 coverage check to a focused PR is low-cost and keeps this branch
gate-green. The provisional rule body above is ready to paste into the card.
