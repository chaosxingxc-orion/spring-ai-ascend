---
rule_id: G-27
title: "Layer Purity"
level: L1
view: development
principle_ref: P-C
authority_refs: [ADR-0159]
enforcer_refs: [E194, E195]
status: active
scope_phase: design
kernel_cap: 8
governance_infra: true
scope_surfaces:
  - docs/governance/layer-purity-policy.yaml
  - docs/governance/layer-purity-temporary-violations.yaml
  - architecture/docs/L0
  - architecture/docs/L1
  - gate/lib/check_layer_purity.py
  - gate/lib/check_l2_detail_sink.py
kernel: |
  An L0 / L1 authority surface is a STRUCTURAL boundary document. It MUST NOT carry L2 runtime implementation detail — method call chains, runtime sequences, SQL / RLS / GUC / persistence DDL or semantics, HTTP status / route-verb / header behaviour, filter ordering, wire formats, method signatures, or test-class inventories. That detail belongs at `architecture/docs/L2/<slug>/`, the contract surfaces (`docs/contracts/`), and the generated facts (`architecture/facts/generated/`). DEFENSIBLE at L0/L1 and never reported: naming a public SPI as a boundary identity, development-view package decomposition, and ArchUnit / enforcer citations. The owns/forbids category vocabulary is `docs/governance/layer-purity-policy.yaml`; the closed, per-entry dated grandfather list is `docs/governance/layer-purity-temporary-violations.yaml`. Two helpers under the single gate Rule 145 encode the verdict: `gate/lib/check_layer_purity.py` (E194) reports the self-contradiction of an authority surface that DECLARES it carries no runtime contract / wire / SPI signature yet does; `gate/lib/check_l2_detail_sink.py` (E195) reports leaked L2 detail by signal family and treats `architecture/docs/L2/` as the in-layer home it never scans. Both run ADVISORY while the L0/L1 corpus is swept clean. Ratchet: advisory → changed-files-blocking → full-blocking, gated by a clean workspace gate + fact-layer byte-identity check per ADR-0159 §9.
---

# Rule G-27 — Layer Purity

## What

Pins the adjudicated layer-purity verdict (the "L0/L1 carries L2/code detail"
critique is TRUE) as a standing, executable invariant: the L0 + L1 architecture
documents are STRUCTURAL boundary surfaces and may not host the L2 runtime
implementation detail whose only authoritative homes are L2 designs, the
contract surfaces, and the generated facts.

The rule has one closed vocabulary surface and one closed tolerance surface, both
authored separately (the helpers are their executable consumers and invent no id,
no relationship, and never outrank a generated fact):

- `docs/governance/layer-purity-policy.yaml` — the owns/forbids category set per
  layer. It fixes the closed category vocabulary (defensible `D1`..`D3`, leaked
  `L1`..`L8`), the layer each category is OWNED / FORBIDDEN at, and the
  authoritative home a leaked category must migrate to. Authority: ADR-0159 §7
  (with ADR-0068 Layered 4+1 and ADR-0144 Layer-vs-Package as the layer-stack
  and matrix antecedents).
- `docs/governance/layer-purity-temporary-violations.yaml` — the closed, dated
  grandfather list. Each row freezes one known, not-yet-migrated leak in an
  L0/L1 document AND declares a per-entry `sunset_date`. A leak whose locus
  matches a still-open row of the matching category is TOLERATED (a
  grandfathered advisory, never a finding); a leak in no row — or one whose
  `sunset_date` has passed (UTC) — is a finding.

DEFENSIBLE detail that any layer, including L0/L1, may carry and that the helpers
never report: naming a public SPI as a boundary identity (`D1`), development-view
package decomposition / package roots (`D2`), and ArchUnit / enforcer citations
naming the mechanism that enforces a constraint (`D3`).

## Why

Closes the systemic-remediation verdict at its root. L0 §0.6 declares the L0
surface carries no runtime contracts / wire / SPI signatures, yet §0.5.3 + §4
did; L1 agent-service carried SQL/RLS, HTTP-status, filter-order, and method-CAS
detail plus a literal "L2 zone" section. Without a standing rule, every cleanup
is a one-off and the detail re-leaks. Layer purity makes the leak a gate-able
defect with a permanent home for the migrated detail (L2 / contracts / facts) and
a permanent rule against re-introducing it, exactly as ADR-0159 §7 ratifies.

## How it works

The single gate Rule 145 invokes two helpers, both advisory at this rung:

- `gate/lib/check_layer_purity.py` (E194) — loads the owns/forbids policy and
  the dated grandfather list, scans `architecture/docs/L0/**` and
  `architecture/docs/L1/**`, and reports a leaked block that is not redeemed by a
  still-open temporary-violation row. It surfaces the self-contradiction case
  directly: an authority surface that DECLARES it carries no runtime contract /
  wire / SPI signature yet leaks a forbidden category. PyYAML is required; its
  absence is a config error (exit 2), treated as a skip on hosts without it.
- `gate/lib/check_l2_detail_sink.py` (E195) — a pure-regex scan (no PyYAML
  dependency) that reports L2 implementation detail left in L0/L1 prose by signal
  family (`sql_persistence`, `http_runtime`, `wire_format`, `method_signature`,
  `filter_ordering`, `test_inventory`). It scans ONLY `architecture/docs/{L0,L1}`
  — never `architecture/docs/L2/`, because L2 is the authoritative HOME for that
  detail, so byte-identical detail under an L2 slug is in-layer by construction.
  A single finding can be suppressed in place with an HTML comment
  `<!-- l2-detail-sink-allow: <reason> -->`.

Neither helper edits an authority surface, mints an id, or replaces a generated
fact — they classify layer prose against the two policy surfaces only.

## Ratchet

advisory → changed-files-blocking (a PR may not ADD or worsen a leak in a changed
L0/L1 document) → full-blocking (the terminal posture once the corpus is clean
and every grandfather row is retired). The helper `--mode`
(`advisory` / `changed-files-blocking` / `full-blocking`) flags implement the
rungs. Promotion past advisory is gated by a clean workspace gate + fact-layer
byte-identity check per ADR-0159 §9, mirroring the ADR-0153 (Rule G-14) and
ADR-0156 (Rules G-16..G-21) ratchets. A missing helper fails closed; a missing
python interpreter is a vacuous pass (Rule G-7 lists WSL as the canonical env).

## Test fixtures

  - VALID  : an L1 doc carrying only a defensible SPI boundary identity +
             package root (`D1`/`D2`) passes full-blocking with zero findings.
  - INVALID: a method-call chain inlined at L1 is detected and fails closed
             (full-blocking).
  - VALID  : advisory mode reports the leak but never blocks (exit 0).
  - VALID  : an L1 leak whose locus matches a still-open grandfather row is
             tolerated (grandfathered advisory, never a finding).
  - INVALID: the SAME method-level string left in an L1 doc fails the l2-detail
             sink in blocking mode (`method_signature`).
  - VALID  : the byte-identical string under `architecture/docs/L2/<frame>/`
             passes blocking — L2 is the detail sink the sink helper never scans.
  - INVALID: changed-files-blocking blocks the L1 leak only when its file is
             `--changed`; an unrelated changed file leaves it advisory (exit 0).
  - VALID  : the sink helper reports the L1 leak in advisory mode but never
             blocks (exit 0).

## Cross-references

  - ADR-0159 — Progressive Learning Curve and Authority Lanes (§7 layer purity,
    §9 enforcement-posture ratchet)
  - ADR-0068 — Layered 4+1 and Architecture Graph (the L0/L1/L2 view stack the
    purity boundary draws from)
  - ADR-0144 — Layer-vs-Package matrix (the antecedent that distinguishes a
    development-view package decomposition from a runtime detail leak)
  - Rule G-28 — ADR Normalization (a forbidden lower-altitude `decision_type` in
    a normalized ADR view is the ADR-lane analogue of a layer-purity leak)
