---
rule_id: G-29
title: "Frame-Card / DSL Parity"
level: L1
view: development
principle_ref: P-C
authority_refs: [ADR-0161]
enforcer_refs: [E196]
status: active
scope_phase: design
kernel_cap: 8
governance_infra: true
scope_surfaces:
  - architecture/docs/L1/frames
  - architecture/features/engineering-frames.dsl
  - architecture/features/features.dsl
  - architecture/features/function-points.dsl
  - architecture/facts/generated/code-symbols.json
  - architecture/facts/generated/tests.json
  - architecture/facts/generated/contract-surfaces.json
  - gate/lib/check_frame_card_consistency.py
kernel: |
  An EngineeringFrame Frame Card (`architecture/docs/L1/frames/<frame-id>.md`) is a READABLE INTERPRETATION of the Structurizr DSL and the generated facts — never an authority. It invents no id, no owner, no status, and no relationship edge, and it never outranks a generated fact: it sits at the lowest interpretation tier of the ADR-0154 cascade (`generated facts > DSL > Card/prose`), the same posture the normalized-ADR views hold. Every identity field a card copies (`frame_id` / `owner_module` / `status` / `primary_package`) MUST equal the `saa.id` / `saa.owner` / `saa.status` / `saa.primaryPackage` of the DSL EngineeringFrame element it names; every factual code / type / method / contract / test claim MUST be cited by generated-fact ID (`code-symbol/<kebab-fqn>`, the class fact's `public_methods[]` entry as `code-symbol/<kebab-fqn>#<jvm-method-descriptor>`, `test/<kebab-fqn>`, `contract-op/<kebab-op-id>`) and MUST resolve in `architecture/facts/generated/*.json`; every FunctionPoint (`FP-…`) a card names MUST be `anchors`-bound to THAT frame in the DSL, or declared a participating-frame reference (no invented anchor). The single gate Rule 146 invokes `gate/lib/check_frame_card_consistency.py` (E196), which reads the DSL frame / FunctionPoint elements + their `anchors` edges as the identity authority and the generated facts as the factual authority, and classifies the cards against them. The `architecture/docs/L1/frames/` directory is greenfield until the pilot card lands (ADR-0161 §4): with no authored card (only `README.md` / `_template.md`) the check is vacuously clean in every mode; the instant one card exists, the DSL elements and the generated facts MUST be readable or the check fails closed (exit 2) in every mode — a missing authority is never an advisory condition. Runs CHANGED-FILES-BLOCKING at this rung per the ADR-0161 §6 ratchet (advisory → changed-files-blocking → full-blocking, the terminal rung after a 14-day soak on a clean corpus + a green ProfileYamlParityTest): a PR may not ADD or worsen a card violation in a Frame Card it touches; pre-existing findings on untouched cards stay advisory. The helper self-derives the changed-card set from git against `--base` (default `origin/main`, else `HEAD`). A missing helper fails closed; a missing python interpreter is a vacuous pass (Rule G-7 lists WSL as the canonical env).
---

# Rule G-29 — Frame-Card / DSL Parity

## What

Pins the Frame Card to the lowest interpretation tier of the ADR-0154 authority
cascade — the executable form of "the Card invents nothing". A Frame Card at
`architecture/docs/L1/frames/<frame-id>.md` is the per-frame readable artifact
ADR-0161 binds to a thickened EngineeringFrame (a package-cluster engineering
anchor inside one Maven module). It is a DERIVED INTERPRETATION of the
Structurizr DSL and the generated facts, and the authority direction is fixed and
one-way:

    ADR
      -> architecture/profile/*
        -> architecture/workspace.dsl
          -> architecture/features/engineering-frames.dsl
            -> architecture/facts/generated/*.json
              -> architecture/docs/L1/frames/<frame-id>.md   (Card: derived)
                -> gate
                  -> docs/governance/architecture-status.yaml

The rule reads three authorities and treats the cards as data:

- The DSL EngineeringFrame element (`architecture/features/engineering-frames.dsl`
  and the re-tagged frame elements in `architecture/features/features.dsl`) — the
  identity authority. A card's copied `frame_id` / `owner_module` / `status` /
  `primary_package` MUST equal the element's `saa.id` / `saa.owner` /
  `saa.status` / `saa.primaryPackage`.
- The generated facts (`architecture/facts/generated/{code-symbols,tests,contract-surfaces}.json`)
  — the factual authority. Every fact ID a card cites
  (`code-symbol/<kebab-fqn>`, a `code-symbol/<kebab-fqn>#<jvm-method-descriptor>`
  method ref against the class fact's `public_methods[]`, `test/<kebab-fqn>`,
  `contract-op/<kebab-op-id>`) MUST resolve.
- The DSL `anchors` edges — the FunctionPoint-ownership authority. Every
  FunctionPoint (`FP-…`) a card names MUST be `anchors`-bound to THAT frame, or
  declared a participating-frame reference; a card may not mint an anchor.

## Why

ADR-0161 thickens the EngineeringFrame from a thin structural index into a
package-cluster anchor and binds a readable Frame Card to it. The 2026-05-29
product-owner + software-engineering review accepted that thicker artifact only
with a structural guard: a frame-explanation layer authored to fill the
"which packages/classes are in scope" gap could silently become a SECOND
architecture registry — inventing frame IDs, owners, statuses, or relationship
edges that diverge from the Structurizr DSL and the generated facts. The
repository already paid for registry divergence (the dual-home rule-registry
defect; the `F-numeric-drift` / `F-deleted-module-name-leakage` families
ADR-0154 collapsed). This rule closes that risk structurally: a card that drifts
from the DSL identity block or cites a non-existent fact fails the gate rather
than quietly establishing a rival truth. The Card stays subordinate to the
authority spine, never a rival to it.

## How it works

The single gate Rule 146 invokes one helper at the changed-files-blocking rung:

- `gate/lib/check_frame_card_consistency.py` (E196) — parses the DSL frame /
  FunctionPoint elements + their `anchors` edges, loads the three generated fact
  files, and classifies every authored card. It reports, file/line-oriented and
  naming the exact broken link, a card that (1) carries a `frame_id` resolving to
  no DSL EngineeringFrame, or whose copied identity fields disagree with that
  element; (2) cites a code-symbol / test / contract-op fact ID — or a method
  descriptor — that does not resolve in the generated facts; or (3) names a
  FunctionPoint the frame does not `anchors` and the card does not declare as a
  participating-frame reference. It invents no ID and no relationship and never
  outranks a generated fact — it is a classifier over the cards only.

Greenfield posture. `architecture/docs/L1/frames/` ships only its `README.md` and
`_template.md` until the pilot card lands (ADR-0161 §4). With no authored card the
check is vacuously clean in every mode. The instant one authored card exists, the
DSL frame elements and the generated facts MUST be readable, or the check fails
closed (exit 2) in EVERY mode including advisory — a card cannot be judged
against authorities that vanished, so a missing authority is never an advisory
condition.

## Ratchet

advisory → changed-files-blocking (this rung: a PR may not ADD or worsen a
finding on a changed card; pre-existing findings on untouched cards stay
advisory) → full-blocking (the terminal posture once the corpus is clean). The
helper `--mode` flags (`advisory` / `changed-files-blocking` / `full-blocking`)
implement the rungs; the changed-files rung derives its scope from `--base`
(default `origin/main`, else `HEAD`) — the same git-deriving pattern as Rule 145
/ E194 `check_layer_purity.py`, and it falls back to full-corpus validation when
git cannot resolve the base. Promotion to the terminal full-blocking rung is
gated by a 14-day soak on a clean corpus + a green `ProfileYamlParityTest` per
ADR-0161 §6, mirroring the ADR-0153 (Rule G-14) and ADR-0156 (Rules G-16..G-21)
ratchets. A missing helper fails closed; a missing python interpreter is a
vacuous pass (Rule G-7 lists WSL as the canonical env).

## Test fixtures

  - VALID  : no authored Frame Card yet is vacuously clean (greenfield,
             ADR-0161 §4) — full-blocking passes with zero findings.
  - VALID  : a DSL-faithful, fact-cited Frame Card passes full-blocking.
  - INVALID: a card inventing a `frame_id` absent from the DSL fails closed
             (full-blocking).
  - INVALID: a card citing a non-existent fact ID fails closed (full-blocking).
  - INVALID: a card naming a FunctionPoint the frame does not `anchors` fails
             closed (full-blocking).
  - VALID  : advisory mode reports the finding but never blocks (exit 0) — the
             ratchet soak posture.
  - INVALID: a vanished fact file fails closed (exit 2) even in advisory mode —
             a missing authority is never an advisory condition.
  - INVALID: changed-files-blocking blocks a NEW broken card (exit 1) while the
             same card once committed + untouched stays advisory (exit 0) — the
             changed-files scope (git scratch, `--base HEAD`).

## Cross-references

  - ADR-0161 — EngineeringFrame as Package-Cluster Anchor + Card-over-DSL (the
    authority this rule enforces: §3 card-over-DSL direction, §4 greenfield
    posture, §5 gate coverage, §6 staged ratchet)
  - ADR-0157 — EngineeringFrame Ontology (the structural-axis layer
    Module → EngineeringFrame → FunctionPoint this card explains)
  - ADR-0154 — Fact-Layer Authority (the cascade `generated facts > DSL >
    Card/prose` that places the Card at the lowest interpretation tier)
  - Rule G-15 — Fact-Layer Integrity (the generated facts the card cites are the
    apex factual authority)
  - Rule G-28 — ADR Normalization (the normalized-ADR view is the ADR-lane
    analogue of the Frame Card: a readable interpretation pinned below the facts)
