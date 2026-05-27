---
level: L1
view: [logical, process, development, physical, scenarios]
module: agent-service
status: active
freeze_id: null
covers_views: [logical, process, development, physical, scenarios]
spans_levels: [L1]
authority: "ADR-0078 (agent-service consolidation) + ADR-0068 (Layered 4+1) + ADR-0099 (rc22 L1 depth + grounding) + ADR-0100 (rc22 5-component decomposition + Run≤Task≤Session≤Memory lifecycle) + ADR-0136..0139 (rc53 vocabulary reconciliation + 5-layer L1 ratification + Fast/Slow Path narrowed semantics) + ADR-0140..0145 (rc55 Engine Adapter split + Internal Event Queue design_only + Run aggregate single owner + review-log demotion + Layer↔Package matrix + sealed RunEvent hierarchy)"
---

# agent-service — L1 4+1 Architecture (Index)

> Wave: rc55 W3-W5 + audit-2026-05-27 corrections; per-view content is active.
> Canonical 4+1 source per ADR-0143: the 5 per-view files + spi-appendix in this directory.
> Module-root L1 spec: [`agent-service/ARCHITECTURE.md`](../../../agent-service/ARCHITECTURE.md) carries shipped-state grounding + cross-links here.
> Front-matter declares full 4+1 coverage per AUD-2026-05-27 PR77-P2-3 closure.

## 0. Why this directory exists

The rc53 4+1 rewrite landed the agent-service Logical / Process / Physical /
Development / Scenarios views as §§14-20 of an interaction record at
[`docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md`](../../logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md).
That arrangement promoted a freeze-marked review log to "canonical L1 4+1 source", which
violated Rule G-1.a (architectural artefacts live under L0/L1/L2, not review logs) and
made the canonical source non-evolvable.

rc55 closes this defect (registered as `F-l1-canonical-source-in-interaction-log` in
[`recurring-defect-families.yaml`](../../governance/recurring-defect-families.yaml))
via [ADR-0143](../../adr/0143-review-log-demotion-l1-canonical-move.yaml):
the canonical 4+1 source moves to the 5 per-view files in this directory; the rc53
review file is demoted to historical authoring record. Per-view edit isolation is
preserved (changing the Logical View no longer requires editing the 1362-line review
file that also holds Process + Physical + Development).

## 1. View files (Wave landing schedule)

| File | View | Wave | Frontmatter |
|---|---|---|---|
| [`scenarios.md`](scenarios.md) | Scenarios | rc55 W3 + post-merge audit Wave 3 (AS-SC01..AS-SC24 expanded inventory §0.1) | `level: L1, view: scenarios` |
| [`logical.md`](logical.md) | Logical | rc55 W3 + post-merge audit Wave 3 (§8 Configuration ownership + §9 Orthogonality red-lines) | `level: L1, view: logical` |
| [`process.md`](process.md) | Process | rc55 W4 | `level: L1, view: process` |
| [`physical.md`](physical.md) | Physical | rc55 W4 | `level: L1, view: physical` |
| [`development.md`](development.md) | Development | rc55 W5 | `level: L1, view: development` |
| [`spi-appendix.md`](spi-appendix.md) | Development (appendix) | rc55 W5 | `level: L1, view: development` |
| [`features/`](features/) | Development (feature inventory) | post-merge audit Wave 3 — absorbs PR #79 AS-L1-F01..AS-L1-F47 across 6 module files | `level: L1, view: development` (per file) |

Each file declares per-view frontmatter per Rule G-1.a; no file declares
`covers_views:` plural — that field is reserved for this index file only.

The `features/` subdirectory is an L1 **module-feature decomposition**, NOT a new architectural authority level. Where it conflicts with the per-view canonical files above, the per-view files win. See [`features/README.md`](features/README.md) for the layout and numbering convention.

## 2. rc55 design ratifications

The rc55 audit produced 6 ADRs that constrain how the per-view files MUST be
authored:

| ADR | Subject | Affects |
|---|---|---|
| [ADR-0140](../../adr/0140-engine-adapter-layer-split.yaml) | Engine Adapter Layer split 5a/5b; RuntimeMiddleware exclusively in Layer 4 | logical.md, development.md |
| [ADR-0141](../../adr/0141-internal-event-queue-design-only.yaml) | Internal Event Queue is design_only sub-section (no peer-layer code home yet) | logical.md, development.md, physical.md |
| [ADR-0142](../../adr/0142-run-aggregate-single-owner.yaml) | Run aggregate owned exclusively by Layer 2; Layer 4 uses RunRepository typed reference | logical.md, process.md |
| [ADR-0143](../../adr/0143-review-log-demotion-l1-canonical-move.yaml) | This directory IS the canonical 4+1 source | all per-view files |
| [ADR-0144](../../adr/0144-layer-vs-package-matrix.yaml) | Layer↔Package matrix unifies ADR-0100 + ADR-0138 decompositions | development.md, logical.md |
| [ADR-0145](../../adr/0145-run-event-sealed-hierarchy.yaml) | Sealed RunEvent hierarchy specification (Java sealed type lands in follow-up impl-mode wave) | logical.md, process.md, development.md |

## 3. Wave status

| Wave | Status | Scope |
|---|---|---|
| W0 | ✅ completed | Family classification + sibling sweep ([`2026-05-26-agent-service-l1-sibling-sweep.en.md`](../../logs/reviews/2026-05-26-agent-service-l1-sibling-sweep.en.md)) |
| W1 | ✅ completed | 6 ADRs (0140-0145) + `docs/contracts/run-event.v1.yaml` + baseline lockstep |
| W2 | ✅ completed | Directory skeletons + Jinja templates + surface-classification.yaml + agent-service/ARCHITECTURE.md §0.5 rewrite + frontmatter narrowing |
| W3 | ✅ completed | scenarios.md + logical.md content authoring |
| W4 | ✅ completed | process.md + physical.md content authoring |
| W5 | ✅ completed | development.md + spi-appendix.md content authoring |
| W6 | ✅ completed | rc53 review file demotion note + family closure + release note |
| audit-2026-05-27 | ✅ completed | 4+1 view + ADR corrective wave per Wave-1+2 findings ledger (ADR-0146 SuspendReason canonicalization + 6 new defect families + 32 doc/ADR patches) |

## 4. Cross-links

- L0: root [`ARCHITECTURE.md`](../../../ARCHITECTURE.md) — platform-level 4+1
- L1 module root: [`agent-service/ARCHITECTURE.md`](../../../agent-service/ARCHITECTURE.md) — shipped-state grounding + dependencies + wave plan
- Sibling L1 modules: [`agent-bus`](../../../agent-bus/ARCHITECTURE.md) · [`agent-client`](../../../agent-client/ARCHITECTURE.md) · [`agent-evolve`](../../../agent-evolve/ARCHITECTURE.md) · [`agent-execution-engine`](../../../agent-execution-engine/ARCHITECTURE.md) · [`agent-middleware`](../../../agent-middleware/ARCHITECTURE.md)
- L2: TBD — first L2 design will likely cover (a) Postgres RLS migration sequence, (b) Reactive Orchestrator backpressure protocol, (c) Run lifecycle extended for Session decoupling (per review §20 L2 zones)
- Rule cards: [G-1](../../governance/rules/rule-G-1.md), [G-1.1](../../governance/rules/rule-G-1.1.md), [G-13](../../governance/rules/rule-G-13.md), [R-C.2](../../governance/rules/rule-R-C.2.md), [R-E](../../governance/rules/rule-R-E.md), [R-J](../../governance/rules/rule-R-J.md), [R-M](../../governance/rules/rule-R-M.md)
- Defect families: [`recurring-defect-families.md`](../../governance/recurring-defect-families.md) — see entries #5, #8, #21-27 for rc55-related families.
