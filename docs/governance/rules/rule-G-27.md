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
  An L0 / L1 authority surface is a STRUCTURAL boundary document. It MUST NOT carry L2 runtime implementation detail — method call chains, runtime sequences, SQL / RLS / GUC / persistence DDL or semantics, HTTP status / route-verb / header behaviour, filter ordering, wire formats, method signatures, or test-class inventories. That detail belongs at `architecture/docs/L2/<slug>/`, the contract surfaces (`docs/contracts/`), and the generated facts (`architecture/facts/generated/`). DEFENSIBLE at L0/L1 and never reported: naming a public SPI as a boundary identity, development-view package decomposition, and ArchUnit / enforcer citations. The owns/forbids category vocabulary is `docs/governance/layer-purity-policy.yaml`; the closed, per-entry dated grandfather list is `docs/governance/layer-purity-temporary-violations.yaml`. Two helpers under the single gate Rule 145 encode the verdict: `gate/lib/check_layer_purity.py` (E194) reports the self-contradiction of an authority surface that DECLARES it carries no runtime contract / wire / SPI signature yet does; `gate/lib/check_l2_detail_sink.py` (E195) reports leaked L2 detail by signal family and treats `architecture/docs/L2/` as the in-layer home it never scans. Both consume the SAME closed, per-entry-dated grandfather list (`docs/governance/layer-purity-temporary-violations.yaml`) for tolerance. Both run CHANGED-FILES-BLOCKING: a PR may not ADD or worsen a leak in an L0/L1 document it TOUCHES; pre-existing leaks in untouched documents (and still-open grandfather rows) stay advisory. Ratchet: advisory → changed-files-blocking → full-blocking, the terminal rung gated by a clean workspace gate + fact-layer byte-identity check per ADR-0159 §9.
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

The single gate Rule 145 invokes two helpers, both wired changed-files-blocking
at this rung:

- `gate/lib/check_layer_purity.py` (E194) — loads the owns/forbids policy and
  the dated grandfather list, scans `architecture/docs/L0/**` and
  `architecture/docs/L1/**`, and reports a leaked block that is not redeemed by a
  still-open temporary-violation row. It surfaces the self-contradiction case
  directly: an authority surface that DECLARES it carries no runtime contract /
  wire / SPI signature yet leaks a forbidden category. The `D3` enforcer-citation
  carve-out spares a constraint that names its single locked enforcing test via
  an explicit mechanism clause (`Enforced by integration X ... class FQN locked
  here per Rule R-C.a`) even when that test is an `*IT`, not an `*ArchTest` — the
  verdict keeps citing an enforcer as the MECHANISM, regardless of harness
  flavour; a genuine test INVENTORY (a table of tests, a test-leading bullet
  list, or 3+ behaviour tests on one line) still leaks. PyYAML is required; its
  absence is a config error (exit 2), treated as a skip on hosts without it.
- `gate/lib/check_l2_detail_sink.py` (E195) — a regex scan that reports L2
  implementation detail left in L0/L1 prose by signal family (`sql_persistence`,
  `http_runtime`, `wire_format`, `method_signature`, `filter_ordering`,
  `test_inventory`). It scans ONLY `architecture/docs/{L0,L1}` — never
  `architecture/docs/L2/`, because L2 is the authoritative HOME for that detail,
  so byte-identical detail under an L2 slug is in-layer by construction. Three
  precision guards keep it from firing on a boundary doc that is DELEGATING
  detail rather than leaking it: (1) a **delegation-pointer guard** spares a
  match whose bullet neighbourhood carries an explicit delegation cue
  (`delegated to` / `owned downstream` / `not restated here` / `does not carry` /
  `is contract material` / `L2 / contract material` / a `Wire shape:`
  forward-heading) plus a contract/L2/fact/`ADR-NNNN` home reference; (2) a
  **wire-pointer guard** treats a `wire_format` match that co-cites its own
  contract / `*.v1.yaml` / `ADR-NNNN` / enforcer on the same line as a reference,
  not an inlined format; and (3) the SAME **dated grandfather list** E194 honours
  — a finding whose `(file, family→category)` matches a still-open row is
  tolerated (best-effort PyYAML load: the grandfather tolerance is inert, never a
  verdict, when the parser is absent, keeping PyYAML optional here). A single
  finding can also be suppressed in place with an HTML comment
  `<!-- l2-detail-sink-allow: <reason> -->`.

Neither helper edits an authority surface, mints an id, or replaces a generated
fact — they classify layer prose against the two policy surfaces only.

## Ratchet

advisory → **changed-files-blocking (this rung)**: a PR may not ADD or worsen a
leak in a changed L0/L1 document → full-blocking (the terminal posture once the
whole L0/L1 corpus is clean and every grandfather row is retired). The helper
`--mode` (`advisory` / `changed-files-blocking` / `full-blocking`) flags
implement the rungs; the gate wrapper computes the changed-file scope once
(base ref `BASE_REF`, default `origin/main`, else `HEAD`) and shares it with
both helpers (`check_layer_purity.py` derives its set from `--base`;
`check_l2_detail_sink.py` takes the same set as explicit `--changed` args).
Promotion to the terminal full-blocking rung is gated by a clean workspace gate
+ fact-layer byte-identity check per ADR-0159 §9, mirroring the ADR-0153 (Rule
G-14) and ADR-0156 (Rules G-16..G-21) ratchets. A missing helper fails closed; a
missing python interpreter is a vacuous pass (Rule G-7 lists WSL as the canonical
env).

## Test fixtures

  - VALID  : an L1 doc carrying only a defensible SPI boundary identity +
             package root (`D1`/`D2`) passes full-blocking with zero findings.
  - INVALID: a method-call chain inlined at L1 is detected and fails closed
             (full-blocking).
  - VALID  : advisory mode reports the leak but never blocks (exit 0).
  - VALID  : an L1 leak whose locus matches a still-open grandfather row is
             tolerated (grandfathered advisory, never a finding).
  - VALID  : a constraint citing its single enforcing `*IT` via an
             `Enforced by ... per Rule R-C.a` clause is D3-defensible
             (full-blocking passes).
  - INVALID: a 3-row `*IT` inventory table still fails full-blocking — the D3
             carve-out spares citations, not catalogues.
  - INVALID: the SAME method-level string left in an L1 doc fails the l2-detail
             sink in blocking mode (`method_signature`).
  - VALID  : the byte-identical string under `architecture/docs/L2/<frame>/`
             passes blocking — L2 is the detail sink the sink helper never scans.
  - INVALID: changed-files-blocking blocks the L1 leak only when its file is
             `--changed`; an unrelated changed file leaves it advisory (exit 0).
  - VALID  : the sink helper reports the L1 leak in advisory mode but never
             blocks (exit 0).
  - VALID  : a `Wire shape (authority): <contract>` delegation pointer passes
             the sink helper in blocking mode (not a leak).
  - VALID  : a `deliberately does NOT carry ... L2 / contract material`
             disclaimer passes the sink helper in blocking mode.
  - VALID  : an L1 leak matching an open temporary-violations row is tolerated
             by the sink helper in blocking mode (grandfathered).
  - INVALID: a genuine method-chain leak with no delegation cue still fails the
             sink helper closed (the guards did not over-suppress).

## Cross-references

  - ADR-0159 — Progressive Learning Curve and Authority Lanes (§7 layer purity,
    §9 enforcement-posture ratchet)
  - ADR-0068 — Layered 4+1 and Architecture Graph (the L0/L1/L2 view stack the
    purity boundary draws from)
  - ADR-0144 — Layer-vs-Package matrix (the antecedent that distinguishes a
    development-view package decomposition from a runtime detail leak)
  - Rule G-28 — ADR Normalization (a forbidden lower-altitude `decision_type` in
    a normalized ADR view is the ADR-lane analogue of a layer-purity leak)
