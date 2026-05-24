# v2.0.0-rc37 — Ascend/Kunpeng strategic repositioning + developer-docs readability

**Date:** 2026-05-24
**Branch:** `rc37/ascend-kunpeng-strategic-pivot`
**Authority:** ADR-0117

## Summary

rc37 repositions `spring-ai-ascend` as a **vertical-agnostic, Huawei Ascend (NPU)
+ Kunpeng (CPU) hardware-synergy** enterprise agent platform, and refreshes the
forward-facing developer documentation for human readability. It resolves the two
open founder-level strategic decisions tracked in
`architecture-status.yaml#strategic_decisions` and drops finance/FSI as the lead
vertical. This is a docs + governance wave — no production Java changes.

## Strategic decision (ADR-0117)

- **`audience_w3_vertical_positioning`: open → resolved.** No single lead vertical;
  the platform's identity is the Ascend/Kunpeng self-host synergy. `ARCHITECTURE.md`
  §1.1 Audience C was rewritten from "financial-services vertical operators" to
  "regulated-industry self-host operators (vertical-agnostic)" via the frozen-doc
  proposal `docs/logs/reviews/2026-05-24-rc37-strategic-repositioning-audience-c-unfreeze.en.md`
  (Rule 44 / Rule G-1.a flow).
- **`brand_review`: open → resolved.** "Ascend" + "Kunpeng" is the deliberate brand
  identity — the Huawei-silicon hardware/software synergy IS the positioning. The
  honest shipped-vs-roadmap boundary (hardware-agnostic kernel today;
  Ascend-optimised serving on the roadmap) is preserved so the brand does not
  overclaim shipped NPU optimisation.

## Documentation refresh (forward corpus)

- `README.md` + new `docs/overview.md`: Ascend/Kunpeng-centric, finance-free, with
  version/wave metadata moved out of prose into release notes.
- `docs/quickstart.md`: `rc`/`Phase-C`/ADR version asides and deleted-module names
  stripped; stale legacy rule references modernized (`Rule 29` → `R-A`,
  `Rule 79` → `D-3`).
- Whitepaper finance examples recast to compute-neutral; `docs/trustworthy/*`
  "financial-grade" → "enterprise-grade".
- `docs/governance/evolution-modalities.yaml` + `ops/helm/.../Chart.yaml` de-financed.

## Historical corpus (intentionally unchanged)

FSI references in historical ADRs (0009/0028/0031/0051/0063), review records, and
`docs/archive/` are decision history accurate as of their date and are deliberately
NOT rewritten (ADR-0117 alternative D).

## Four competitive pillars (P-B)

- **performance** — unchanged; no runtime change in this wave.
- **cost** — unchanged; OSS-first self-host on commodity Kunpeng/Ascend silicon is
  reaffirmed as the cost pillar.
- **developer_onboarding** — improved: README + overview + quickstart rewritten for
  first-read clarity, with governance/version noise removed from the onboarding path.
- **governance** — strengthened: two open founder-level strategic decisions closed
  with an ADR + a frozen-doc review trail; `F-numeric-drift` gains the rc37
  README-single-line baseline-phrase lesson.

## Baseline deltas

| Metric | Count | Delta |
|---|---|---|
| §4 constraints | 65 | 0 |
| ADRs | 102 | +1 (ADR-0117) |
| active gate rules | 135 | 0 |
| gate self-test cases | 226 | 0 |
| active engineering rules | 42 | 0 |
| active governing principles | 13 | 0 |
| enforcer rows | 168 | 0 |
| adr_count (ADR files) | 102 | +1 (ADR-0117) |
| maven_tests_green | 382 | 0 |
| architecture_graph_nodes | 470 | +1 (ADR-0117 node) |
| architecture_graph_edges | 839 | +4 (ADR-0117 relates_to/affects edges) |
| recurring_defect_families | 12 | 0 |

## Verification

- `python gate/build_architecture_graph.py` — 470 nodes, 839 edges; validation OK.
- `bash gate/check_parallel.sh` — GATE: PASS (135/135).

## Methodology

The finance framing that lingered across the active forward corpus is the
`F-shadow-corpus-prose-staleness` pattern at the positioning layer: stale strategic
prose outlives the decision that motivated it. The fix is the same
categorize → sweep → batch-fix → prevention discipline — here the "prevention" is
closing the two founder-level decisions in `strategic_decisions` so the open
placeholder cannot silently re-seed finance framing in future docs.
