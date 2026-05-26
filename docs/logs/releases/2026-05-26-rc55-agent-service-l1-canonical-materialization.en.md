---
level: L1
view: scenarios
status: closure
wave: rc55
release_date: 2026-05-26
authority: ADR-0140..0145 + ADR-0143 (review-log demotion) + ADR-0100 + ADR-0138 (parent ratifications)
---

# rc55 — Agent Service L1 Canonical Materialization — Closure Release Note

## 0. Canonical baselines (rc55 — per Gate Rule 28)

| Baseline | rc55 value |
|---|---:|
| §4 constraints | 65 |
| ADRs | 130 |
| gate rules | 143 |
| Gate self-test cases | 260 |
| self-tests | 260 |
| active engineering rules | 43 |
| Layer-0 governing principles | 13 |
| enforcer rows | 176 |
| Maven XML-counted tests | 461 |
| architecture graph nodes | 612 |
| architecture graph edges | 1153 |
| recurring defect families | 27 |

These match `docs/governance/architecture-status.yaml#baseline_metrics`
exactly. The rc55 wave bumped: adr_count 124→130, graph_nodes
606→612, graph_edges 1112→1153, recurring_defect_families 20→27.

**Canonical baseline phrasing** (matches the rc54 release-note style for
Gate Rule 28 grep): 65 §4 constraints · 130 ADRs · 143 active gate rules ·
260 gate self-tests · 43 active engineering rules · 176 enforcer rows ·
461 Maven XML-counted tests · 612 architecture graph nodes / 1153 edges ·
27 recurring defect families. **Gate self-tests: 260**.

## 1. Summary

rc55 closes the rc53 4+1 rewrite's governance category error: the
canonical L1 4+1 source for agent-service moved from a freeze-marked
`docs/logs/reviews/` interaction record to 7 per-view files under
`docs/L1/agent-service/`, per ADR-0143. The wave also lands 5 sibling
ADRs (ADR-0140..0142, ADR-0144, ADR-0145) that close the layer-cohesion,
single-aggregate-owner, layer↔package-mapping, and
discriminator-without-discriminated-type design defects identified by
the rc55 audit + W0 sibling sweep.

## 2. Four competitive pillars (Rule R-B) — performance · cost · developer_onboarding · governance

| Pillar | rc55 dimension | rc55 baseline movement |
|---|---|---|
| **performance** | Layer 5a/5b split (ADR-0140) decouples Spring AI evolution cadence from Rule R-M engine contract cadence — both can evolve independently without cross-layer drag | `architecture_graph_nodes` 606 → 612 (+6 ADR nodes); no runtime perf metric change at rc55 (design wave) |
| **cost** | Per-view edit isolation: changing the Logical View no longer requires editing a 1362-line review file that holds Process + Physical + Development. Reviewer cost per view-change ~7× lower | `recurring_defect_families` 20 → 27 (+7 new prevention surfaces); doc-edit cost regression eliminated |
| **developer_onboarding** | Canonical L1 4+1 source is now at the expected location `docs/L1/agent-service/` (matches Rule G-1.a discipline); per-view files are independently editable; rc53 review file remains as historical authoring record but no longer "canonical" | `adr_count` 124 → 130 (+6 rc55 ADRs); ADR slate documents the design ratification for future onboarding |
| **governance** | F-l1-canonical-source-in-interaction-log + 6 sibling new families registered; F-cross-authority-agreement extended with 5 in-doc occurrences (M2/M3/M4/M11/R4); F-terminal-verb-overclaim REOPENED to monitoring; all surfaces template-rendered byte-identical per Rule G-13 | `architecture_graph_edges` 1112 → 1153 (+41 edges); 25 template renders pass byte-identical |

## 3. Wave landing summary

| Wave | Commit | Scope | Gate |
|---|---|---|---|
| W0 | (per `git log --all --oneline`) | Classify 19 rc55 audit findings into 11 family touches; register 7 new families (F-l1-canonical-source-in-interaction-log, F-layer-decomposition-low-cohesion, F-frontmatter-claim-body-mismatch, F-logical-vs-structural-decomposition-conflation, F-design-only-mechanism-shown-as-shipped, F-discriminator-without-discriminated-type, F-spi-package-bloat-with-carriers); extend 4 existing families; execute fingerprint-driven sibling sweep across the WHOLE repo; publish sibling-sweep report at `docs/logs/reviews/2026-05-26-agent-service-l1-sibling-sweep.en.md` | G-9.a/.b/.c + render byte-identical |
| W1 | (per `git log`) | 6 ADRs (0140-0145) + `docs/contracts/run-event.v1.yaml` + `docs/contracts/contract-catalog.md` row + baseline lockstep (adr_count 124→130, graph nodes 606→612, graph edges 1112→1153, recurring_defect_families 20→27); README + readme-root.md.j2 sync | G-1.b + M-2.b + render byte-identical |
| W2 | (per `git log`) | 7 skeleton files under `docs/L1/agent-service/{README,scenarios,logical,process,physical,development,spi-appendix}.md` + 7 corresponding Jinja templates; surface-classification.yaml registers 7 new template entries; `agent-service/ARCHITECTURE.md` frontmatter narrowed (covers_views: [scenarios]); §0.4 stale table DELETED; §0.5 rewritten to point at canonical per-view files | G-1.a + G-13 byte-identical + M-1 skeleton purity |
| W3 | (per `git log`) | scenarios.md (S1-S5 + cross-scenario invariants) + logical.md (5-layer Mermaid with ADR-0140 split + ER + state machines + SuspendSignal flow + RunEvent hierarchy per ADR-0145 + glossary with R4 distinct-mechanisms) | G-1.1.a + G-8.e + no red-line |
| W4 | (per `git log`) | process.md (6 sequence diagrams P1-P6 including cancel-race loser P6 per O3) + physical.md (5-plane + RLS table + 3-track bus binding + sandbox + cross-plane tenant propagation) | G-1.1.c L2 boundary contracts |
| W5 | (per `git log`) | development.md (package tree cross-walked + Layer↔Package matrix per ADR-0144 + 5 L2 Boundary Contracts + M9 dispatcher boundary clarification) + spi-appendix.md (9 active SPI 4-way parity with M4 TaskStateStore naming + M10 systemic gap documentation + cross-module SPI consumption + future SPIs) | G-1.1.b 4-way parity + R-D.d carriers-out-of-.spi (DEFERRED to follow-up impl-mode wave per the rc55 plan) |
| W6 | (this commit) | rc53 review file demotion note (.en + .cn) per ADR-0143 + this release note + final gate verification | G-9 freshness + all gates green |

## 4. ADR slate

| ADR | Title | Status |
|---|---|---|
| [ADR-0140](../../adr/0140-engine-adapter-layer-split.yaml) | Engine Adapter Layer split 5a + 5b; RuntimeMiddleware exclusively in Layer 4 | accepted |
| [ADR-0141](../../adr/0141-internal-event-queue-design-only.yaml) | Internal Event Queue is design_only sub-section + Boundary Contract published | accepted |
| [ADR-0142](../../adr/0142-run-aggregate-single-owner.yaml) | Run aggregate single-owner pinning to Layer 2; Layer 4 uses RunRepository typed reference | accepted |
| [ADR-0143](../../adr/0143-review-log-demotion-l1-canonical-move.yaml) | rc53 review file demoted; canonical 4+1 source moves to docs/L1/agent-service/ | accepted |
| [ADR-0144](../../adr/0144-layer-vs-package-matrix.yaml) | Layer↔Package matrix unifies ADR-0100 + ADR-0138 decompositions | accepted |
| [ADR-0145](../../adr/0145-run-event-sealed-hierarchy.yaml) | Sealed RunEvent hierarchy specification (10 variants); Java sealed type follows in impl-mode wave | accepted |

## 5. Family disposition (per W0 classification + W6 closure)

### 5.1 New families registered (7)

All 7 lead with `last_observed_rc: rc55-agent-service-l1-canonical-materialization`
and `cleanup_status: monitoring` per the standard rc20 / ADR-0097 cool-down
convention (3 subsequent waves without recurrence before promotion to `closed`).

| Family | Closure mechanism |
|---|---|
| F-l1-canonical-source-in-interaction-log | ADR-0143 + this wave moved canonical source out of `docs/logs/reviews/` |
| F-layer-decomposition-low-cohesion | ADR-0140 (5a/5b split) + ADR-0142 (Run aggregate single-owner pinning) |
| F-frontmatter-claim-body-mismatch | rc55 W2 frontmatter narrowing + per-view file separation |
| F-logical-vs-structural-decomposition-conflation | ADR-0144 (Layer↔Package matrix published as third corner) |
| F-design-only-mechanism-shown-as-shipped | ADR-0141 (Internal Event Queue demoted to design_only sub-section) + rc55 W3/W4 view-authoring discipline (every diagram caption annotates design_only mechanisms) |
| F-discriminator-without-discriminated-type | ADR-0145 (sealed RunEvent hierarchy spec) + `docs/contracts/run-event.v1.yaml` (design_only) — Java sealed type lands in follow-up impl-mode wave; Rule R-M.e becomes non-vacuous then |
| F-spi-package-bloat-with-carriers | rc55 W5 spi-appendix.md §3 documents systemic gap across 12 SPI packages; bulk Java refactor DEFERRED to follow-up impl-mode wave; M10 cited-surface correction (actual offender is agent-middleware.memory.spi, not the originally-cited agent-service.runtime.memory.spi) |

### 5.2 Existing families extended

| Family | rc55 occurrence narrative |
|---|---|
| F-cross-authority-agreement | 5 in-doc disagreements within `agent-service/ARCHITECTURE.md` (M2 §0.4 stale prose, M3 wave-status drift, M4 TaskRepository vs TaskStateStore naming, M11 unverified contract reference, R4 ChatAdvisor + RuntimeMiddleware aliased in glossary). New SCALE: intra-doc cross-authority drift (prior occurrences were inter-doc). |
| F-terminal-verb-overclaim | REOPENED from `closed` to `monitoring` — agent-service/ARCHITECTURE.md §runtime/resilience prose flanking deferred Rule R-K.c citation uses present-tense terminal verbs. rc15 closure was scoped to CLAUDE.md kernels + agent-*/ARCHITECTURE.md but did not cover narrative prose flanking deferred-clause citations. Cool-down required: 3 subsequent waves. |
| F-design-doc-orphan-from-authority (existed in review §8 but never registered in yaml; M2/M11 mapped here) | Folded into F-cross-authority-agreement as the more specific family per W0 classification |
| F-design-doc-language-bypasses-invariant | Unchanged at rc53 occurrence; rc55 audit's M7 mapped to the more specific NEW F-design-only-mechanism-shown-as-shipped family |

## 6. Baseline lockstep (Rule G-8.a + memory feedback_lockstep_baseline_surfaces)

| Surface | Field | rc54 | rc55 |
|---|---|---:|---:|
| `architecture-status.yaml#baseline_metrics` | `adr_count` | 124 | 130 |
| | `architecture_graph_nodes` | 606 | 612 |
| | `architecture_graph_edges` | 1112 | 1153 |
| | `recurring_defect_families` | 20 | 27 |
| `README.md` baseline cell | `... · N ADRs · ...` | 124 | 130 |
| | `N-node / N-edge graph` | 606 / 1112 | 612 / 1153 |
| `architecture-status.yaml#allowed_claim` prose | rc55 wave narrative | (rc54 narrative) | rc55 narrative replaces |

All template surfaces (`readme-root.md.j2`, `contract-catalog.md.j2`,
`recurring-defect-families.md.j2`) re-render byte-identical per Rule G-13.

## 7. Files touched (commit summary across W0-W6)

```
NEW (16):
  docs/L1/agent-service/README.md
  docs/L1/agent-service/scenarios.md
  docs/L1/agent-service/logical.md
  docs/L1/agent-service/process.md
  docs/L1/agent-service/physical.md
  docs/L1/agent-service/development.md
  docs/L1/agent-service/spi-appendix.md
  docs/governance/templates/l1-agent-service-{README,scenarios,logical,process,physical,development,spi-appendix}.md.j2
  docs/adr/0140-engine-adapter-layer-split.yaml
  docs/adr/0141-internal-event-queue-design-only.yaml
  docs/adr/0142-run-aggregate-single-owner.yaml
  docs/adr/0143-review-log-demotion-l1-canonical-move.yaml
  docs/adr/0144-layer-vs-package-matrix.yaml
  docs/adr/0145-run-event-sealed-hierarchy.yaml
  docs/contracts/run-event.v1.yaml
  docs/logs/reviews/2026-05-26-agent-service-l1-sibling-sweep.en.md
  docs/logs/releases/2026-05-26-rc55-agent-service-l1-canonical-materialization.en.md  (this file)

MODIFIED (9):
  agent-service/ARCHITECTURE.md  (+ .j2 sync)
  docs/contracts/contract-catalog.md  (+ .j2 sync)
  docs/governance/recurring-defect-families.{yaml,md}  (+ .md.j2 sync)
  docs/governance/architecture-status.yaml
  docs/governance/templates/surface-classification.yaml
  README.md  (+ readme-root.md.j2 sync)
  docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.{en,cn}.md  (demotion notes added)
```

## 8. Verification

- All 25 template renders pass byte-identical (Rule G-13 gate).
- `architecture-graph.yaml` rebuilds deterministically (612 nodes, 1153 edges).
- `families.yaml` structurally validates (27 entries; schema_version 1).
- `architecture-status.yaml` baselines match live graph head.
- All commits pass on Linux/WSL per Rule G-7.
- W0 sibling sweep covered 7 family fingerprints; 1 fix-in folded into W2 + 1 fix-in folded into W5; 2 systemic gaps documented for future waves (root ARCHITECTURE.md frontmatter narrowing + bulk SPI carrier-out-of-.spi refactor).

## 9. Out of scope (explicit non-goals)

- Bulk Java refactor to promote carriers out of 12 `*.spi.*` packages
  (F-spi-package-bloat-with-carriers systemic closure) — deferred to
  follow-up impl-mode wave; documented at `docs/L1/agent-service/spi-appendix.md` §3.
- Java sealed `RunEvent` interface + 10 record variants (per ADR-0145)
  — deferred to follow-up impl-mode wave; contract spec published at
  `docs/contracts/run-event.v1.yaml` (design_only).
- Root `ARCHITECTURE.md` frontmatter `covers_views` narrowing — root
  is frozen per `freeze_id: W1-russell-2026-05-14`; fix requires a
  separate `docs/logs/reviews/` proposal + fresh freeze cycle (sibling
  sweep documented but not folded into rc55).
- rc23+ Java refactor (move Run/Task/Session aggregates into the new
  `dispatcher/orchestrator/task/session/engine/` top-level sub-packages)
  — per ADR-0100 timeline; this rc55 wave is doc-only.

## 10. Cross-references

- W0 sibling sweep report: [`docs/logs/reviews/2026-05-26-agent-service-l1-sibling-sweep.en.md`](../reviews/2026-05-26-agent-service-l1-sibling-sweep.en.md)
- Historical authoring record (rc53): [`docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md`](../reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md) (demoted per ADR-0143)
- Plan file: `D:\.claude\plans\agentservice-l1-4-1-java-3-d-chao-worksp-squishy-steele.md` (rc55 wave plan)
- Canonical 4+1 source: [`docs/L1/agent-service/README.md`](../../L1/agent-service/README.md)
- Module-root grounding: [`agent-service/ARCHITECTURE.md`](../../../agent-service/ARCHITECTURE.md)
