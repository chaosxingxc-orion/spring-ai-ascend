---
affects_level: L0
affects_view: scenarios
proposal_status: accepted
authors: ["chao"]
related_adrs: [ADR-0117]
related_rules: [Rule-44, Rule-G-1, Rule-G-2]
affects_artefact: ["ARCHITECTURE.md"]
---

# rc37 — Strategic repositioning: unfreeze ARCHITECTURE.md §1.1 Audience C (drop FSI lead vertical)

> **Date:** 2026-05-24
> **Status:** Accepted
> **Affects:** L0 / scenarios (see front-matter; ADR-0068 + CLAUDE.md Rule G-1)

## 1. Background

ADR-0117 resolves two founder-level strategic decisions tracked as OPEN in
`architecture-status.yaml#strategic_decisions`: `audience_w3_vertical_positioning`
(FSI as the named lead W3+ vertical) and `brand_review` (Ascend = deliberate
hardware-synergy brand identity). The founder's decision is to reposition the
platform as a vertical-agnostic, Ascend/Kunpeng hardware-synergy platform and
drop finance/FSI as the lead vertical.

`ARCHITECTURE.md` carries `freeze_id: W1-russell-2026-05-14`, so its §1.1
Audience C paragraph cannot be edited without this `docs/logs/reviews/`
proposal (CLAUDE.md Rule G-1 sub-clause .a / Gate Rule 44). This proposal is
that unfreeze record.

## 2. Scope statement

`affects_level: L0` — amends the system-boundary audience model in the root
canonical architecture document. Chief Architect (founder) approval is granted
via ADR-0117. Only the §1.1 Audience C paragraph (and the one sentence that
references FSI in the Audience-C rationale) changes; module counts, §4
constraint counts, and all other sections are untouched.

## 3. Root cause / strongest interpretation (Rule D-1)

1. **Motivation**: the L0 audience model names FSI as the lead W3+ vertical,
   contradicting the founder's vertical-agnostic repositioning.
2. **Execution path**: `ARCHITECTURE.md` §1.1 "Audience C — financial-services
   vertical operators".
3. **Root cause**: the audience model encoded a vertical-specific lead that was
   always a strategic placeholder (tracked as an open decision), now resolved
   to vertical-agnostic by ADR-0117.
4. **Evidence**: `ARCHITECTURE.md` §1.1; `architecture-status.yaml#strategic_decisions.audience_w3_vertical_positioning`.

## 4. Proposed change

- **Frozen artefact unfrozen**: `ARCHITECTURE.md` §1.1 Audience C — rewritten
  from "financial-services vertical operators (FSI as lead vertical)" to
  "regulated-industry self-host operators (vertical-agnostic)"; the FSI
  rationale sentence is genericised to vertical-agnostic language pointing at
  the now-resolved strategic decision.
- **ADRs introduced**: ADR-0117 (this wave's authority).
- **Governance**: `strategic_decisions.audience_w3_vertical_positioning` and
  `brand_review` flipped `open -> resolved` (pointer: ADR-0117).
- **Forward-doc readability refresh** (same wave): README, `docs/overview.md`,
  `docs/quickstart.md`, the whitepaper finance examples, and
  `docs/trustworthy/*` are recast Ascend/Kunpeng-centric and finance-free.
- **Gate rules amended**: none. **Tests amended**: none (docs/governance wave).

## 5. Alternatives considered

| Alternative | Why rejected |
|---|---|
| Name a different lead vertical | Founder chose horizontal hardware-synergy positioning, not a vertical swap. |
| Keep finance as one example vertical | Founder directed dropping finance entirely from the active framing. |
| Rewrite FSI in historical ADRs/reviews | Falsifies decision history; historical corpus is marked-by-location. |

## 6. Verification plan

- [x] `bash gate/check_parallel.sh` passes (Rule 44 sees this proposal naming
      `ARCHITECTURE.md` under `affects_artefact:`).
- [x] `python gate/build_architecture_graph.py` regenerates; G-1.b idempotent.
- [x] Cross-authority gates G-2 / G-8 clean (baseline lockstep for ADR-0117).

## 7. Rollout

- Wave: rc37 (W1 governance wave).
- Freeze impact: unfreezes one paragraph of `ARCHITECTURE.md`
  (`freeze_id: W1-russell-2026-05-14`); the file remains frozen for all other
  edits, which continue to flow through `docs/logs/reviews/`.

## 8. Self-audit (Rule D-5)

No open findings in a ship-blocking category. The change is strategic-framing
only; no shipped capability, count, or contract is altered.

---

## Authority

- CLAUDE.md Rule G-1 sub-clause .a — frozen L0/L1 edits flow through `docs/logs/reviews/`.
- Gate Rule 44 (`frozen_doc_edit_path_compliance`) — validates `affects_artefact:`.
- ADR-0117 — rc37 strategic repositioning (the decision this proposal records).
