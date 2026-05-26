---
level: L0
view: scenarios
status: active
language: en-US
authority_refs:
  - docs/governance/recurring-defect-families.yaml
  - docs/adr/0094-rc17-recurring-defect-family-truth-and-rule-consolidation.yaml
relates_to:
  - docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.en.md
---

# rc55 Wave-0 Sibling Sweep — Agent-Service L1 Canonical Materialization

> Date: 2026-05-26
> Wave: rc55 W0 (G-C sibling-sweep ritual)
> Scope: 7 NEW + 4 extended defect families registered in
> `docs/governance/recurring-defect-families.yaml` are fingerprint-driven
> swept across the WHOLE repo to find latent sibling occurrences the
> rc55 audit did not directly cite.
> Discipline reference: `feedback_data_driven_decisions.md`,
> `project_v2_0_0_rc10_response_2026_05_19.md`, Rule G-9.
> Methodology: per the standing G-A..G-F closure ritual — (a) classify
> cited findings into families (done in families.yaml top-level note);
> (b) register new families with fingerprints; (c) sweep with each
> fingerprint over the repo to find siblings; (d) fold siblings into
> rc55 W1-W5 fix scope OR record as future-wave residual.

## 1. Cited findings recap

The rc55 audit produced 19 findings (M1..M11 MODIFY + R1..R8 REJECT)
classified into 11 family touches (7 new families registered + 4
existing families extended). The classification table lives in the
plan file at
`D:\.claude\plans\agentservice-l1-4-1-java-3-d-chao-worksp-squishy-steele.md`
§2.5.3.

## 2. Per-family sibling sweep

### 2.1 F-l1-canonical-source-in-interaction-log — 1 true sibling, 2 false positives

**Fingerprint.** Grep `agent-*/ARCHITECTURE.md` + `docs/L1/**/*.md` +
`ARCHITECTURE.md` for markdown links to `docs/logs/reviews/.*\.md`
with the proximity words {canonical, authoritative, 4+1 source,
view source} within ±3 lines.

**Hits.**

| File | Line | Verdict | Note |
|---|---|---|---|
| `agent-service/ARCHITECTURE.md` | 23-24 | TRUE POSITIVE | The rc55 cited case — §0.5 promotes the rc53 review doc to canonical 4+1 source. Fixed by rc55 W2 (ADR-0143). |
| `ARCHITECTURE.md` (root) | 13 | FALSE POSITIVE | Narrative-history paragraph cites multiple review files, not a "canonical pointer". No fix needed. |
| `ARCHITECTURE.md` (root) | 937 | FALSE POSITIVE | Rule 64 prose `All proposals in docs/logs/reviews/ MUST declare affects_level:` — that's a discipline rule, not a canonical-source pointer. No fix needed. |

**Repo-wide systemic check.** No other `agent-*/ARCHITECTURE.md` file
points at a review log as its canonical 4+1 source — the rc55 audit
case is unique. Sweep extended to `docs/L2/**/*.md` (empty per current
repo state) and `docs/quickstart.md` (no review-log pointers) returned
0 hits.

**Sweep regex refinement (deferred to W5+ gate-rule).** The current
regex matches narrative paragraphs that merely mention a review log.
A tighter fingerprint MUST require BOTH a markdown link AND a
proximity-word in the same paragraph (not just ±3 lines), AND
exclude paragraphs whose containing heading is a Rule body (`Rule N`
or `#### Rule X`).

### 2.2 F-layer-decomposition-low-cohesion — 1 true sibling (rc55 cited), 0 systemic siblings

**Fingerprint.** Pattern-match in `agent-*/ARCHITECTURE.md` +
`docs/L1/**/*.md` for `Layer N` headings; count bullet items per layer;
flag if ≥4 OR if same component name appears under ≥2 layers.

**Hits.** Sweep found `### N.M` numbered sub-sections in
agent-evolve / agent-service / multiple review docs, but on inspection
none of these are LAYER decomposition tables — they are routine
section numbering (`### 2.A Platform-side concerns`,
`### 11.1 Lifecycle hierarchy`, etc.). The rc55 cited case (review
§15.1 5-layer diagram with kitchen-sink Layer 5 + double-homed
RuntimeMiddleware) is the ONLY actual layer-cohesion violation in
the repo. No sibling fix-ins required for W1-W5.

**Repo-wide systemic check.** Cross-checked with `Mermaid` flowchart
TB blocks in all `agent-*/ARCHITECTURE.md`:
- agent-bus: no flowchart, three-channel table only — OK
- agent-client: no flowchart — OK
- agent-evolve: 2x2 matrix, not layered — OK
- agent-execution-engine: SPI table, no layers — OK
- agent-middleware: hookpoint enum table, no layers — OK
- agent-service: has rc55 cited 5-layer model — already fixed by ADR-0140 + ADR-0142

### 2.3 F-frontmatter-claim-body-mismatch — 2 true siblings (rc55 cited + 1 systemic), 5 false positives

**Fingerprint.** For every .md with `level:` frontmatter, parse
`view:` + `covers_views:` and scan body for `## *<View>*` headings;
FAIL on any frontmatter-declared view absent from body.

**Hits.**

| File | covers_views | Verdict | Note |
|---|---|---|---|
| `agent-bus/ARCHITECTURE.md` | `[logical]` | FALSE POSITIVE | Body has §1 Role + §2 Three-track channel isolation + §3a/b/c SPIs — all logical-view content under domain-specific headings. The frontmatter assertion is semantically true; the regex was naive. |
| `agent-client/ARCHITECTURE.md` | `[logical]` | FALSE POSITIVE | Same pattern as agent-bus. |
| `agent-evolve/ARCHITECTURE.md` | `[logical]` | FALSE POSITIVE | Same pattern. |
| `agent-execution-engine/ARCHITECTURE.md` | `[logical]` | FALSE POSITIVE | Same pattern. |
| `agent-middleware/ARCHITECTURE.md` | `[logical]` | FALSE POSITIVE | Same pattern. |
| `agent-service/ARCHITECTURE.md` | `[logical, development, process, physical, scenarios]` | TRUE POSITIVE | The rc55 cited case — frontmatter declares all 5 views; body has none of the literal view headings. Fixed by rc55 W2: frontmatter narrowed to `view: scenarios` + `covers_views: [scenarios]`; per-view content moves to `docs/L1/agent-service/{logical,process,physical,development}.md`. |
| `ARCHITECTURE.md` (root) | `[logical, development, process, physical, scenarios]` | TRUE POSITIVE | Same pattern at L0. SYSTEMIC SIBLING — root ARCHITECTURE.md also claims all 5 views but body has no literal view section headings. Defer to a future rc wave (L0 docs are frozen per `freeze_id`); rc55 documents the sibling here for awareness. |

**Folded into W2.** agent-service/ARCHITECTURE.md correction is in
rc55 W2 scope (already planned). Root ARCHITECTURE.md correction is
DEFERRED — its current `freeze_id: W1-russell-2026-05-14` makes it
subject to the frozen-doc edit protocol; correcting it requires a
separate `docs/logs/reviews/` proposal + a fresh freeze cycle. Not
in rc55 scope.

**Sweep regex refinement (deferred to W5+ gate-rule).** Detection
needs a semantic-equivalence allow-list, or the frontmatter convention
needs strengthening (e.g. "every covers_views entry MUST have a
corresponding `<!-- view: <name> -->` marker in body, even if heading
text is domain-specific").

### 2.4 F-logical-vs-structural-decomposition-conflation — 1 true sibling (rc55 cited), sweep regex too strict to find systemic siblings

**Fingerprint.** Detect ≥2 distinct numbered N-table blocks (`#### 1.
... #### 5.` OR `| # | Layer/Component |`) inside the same .md without
a "logical ↔ package mapping" section between them.

**Hits.** 0 hits from the sweep. The rc55 cited case in
`agent-service/ARCHITECTURE.md` (§11 5-component model + review §15
5-layer model in a different file) is split ACROSS two files, so the
"same .md" regex missed it. The fingerprint needs widening to also
cross-reference between `agent-*/ARCHITECTURE.md` and the review
files it transitively imports via §0.5-style pointers.

**Manual cross-file check.** Browsed each `agent-*/ARCHITECTURE.md`
for §N "decomposition" blocks paired with sibling review files; no
other module has BOTH a §N N-element decomposition table AND a
sibling review file with a competing N-element decomposition. The
rc55 case is unique because §0.5 elevates a review file's competing
5-layer to "canonical" — once W2 demotes the review file (per
ADR-0143), the conflation pattern dissolves at agent-service.

**Folded into W2.** No additional W1-W5 fix required beyond the
already-planned ADR-0144 layer↔package matrix.

### 2.5 F-design-only-mechanism-shown-as-shipped — 3 true siblings beyond rc55 cited

**Fingerprint.** Cross-reference: read `docs/contracts/*.v1.yaml`
`status: design_only` entries → extract referenced types → grep
`agent-*/ARCHITECTURE.md` + `docs/L1/**/*.md` + recent review docs
for those type names → FAIL if caption lacks `(design_only)` marker.

**design_only contract inventory (17 contracts).**

```
a2a-envelope.v1.yaml          model-invocation.v1.yaml
agent-definition.v1.yaml      model-streaming.v1.yaml
backpressure-request.v1.yaml  plan-projection.v1.yaml
chat-advisor.v1.yaml          plan.v1.yaml
federation-envelope.v1.yaml   planning-request.v1.yaml
ingress-envelope.v1.yaml      prompt-template.v1.yaml
memory-store.v1.yaml          reflection-envelope.v1.yaml
                              skill-definition.v1.yaml
                              structured-output.v1.yaml
                              vector-store.v1.yaml
```

**Hits.**

| File:Line | Type | Verdict | Note |
|---|---|---|---|
| `agent-service/ARCHITECTURE.md:725` | `agent-invoke-request.v1.yaml` | TRUE POSITIVE | §11.3 cites the contract with explicit "(status `design_only` at rc22)" marker — PASSES the fingerprint test. Already correctly annotated. |
| `agent-service/ARCHITECTURE.md:812` | `AgentInvokeRequest` (in carrier table) | TRUE POSITIVE | §SPI Appendix carrier table lists `AgentInvokeRequest` without status marker; reader assumes it is fully shipped. SHOULD carry `(design_only — contract status per ADR-0100)` marker in the carrier table. Fold into W5 (SPI Appendix authoring). |
| `agent-service/ARCHITECTURE.md:723` | `### 11.3 AgentInvokeRequest contract (rc22 declares; rc24 wires runtime)` | TRUE POSITIVE | Heading is honest about "declares" vs "wires", but the body table at line 812 is silent. SAME finding as above — W5 fix. |
| `docs/logs/reviews/2026-05-22-agent-service-l1-expansion-proposal.en.md:393` | `public interface DualTrackRouter { ... }` Java declaration | FALSE POSITIVE | Inside a design proposal review log; review logs are by definition design surfaces, and the surrounding proposal text frames the interface as a proposal. Not in rc55 fix scope. |
| `agent-evolve/ARCHITECTURE.md:14` | `SlowTrackJudge SPI shipped rc26` | FALSE POSITIVE | Header explicitly says SHIPPED with wave anchor — not design_only. Fingerprint over-matched. |

**Folded into W5.** agent-service/ARCHITECTURE.md SPI Appendix carrier
table needs `(design_only)` annotation on `AgentInvokeRequest` row.
Already in W5 scope as part of `M11` finding extension.

**Repo-wide systemic check.** The fingerprint also matched
`DualTrackRouter` and `service.queue` in the rc53 review file
extensively, but ALL those mentions are already correctly annotated
`(design_only — ADR-0138 §3)` / `(W2)` / `(new in Wave 4)` per the
rc53 wave's own discipline. No additional fix-ins for those terms.

### 2.6 F-discriminator-without-discriminated-type — 1 true sibling (rc55 cited), 1 verified-clean adjacent type

**Fingerprint.** Find Java enums under `agent-*/src/main/java/**/*Export.java`
+ `*Kind.java` + `*Discriminator.java`; count non-self callers; FAIL
if ≤1 non-self callers AND a Rule kernel claims an enforcer over its
consumers.

**Hits.**

| Type | File | Non-self callers | Verdict |
|---|---|---|---|
| `EvolutionExport` | `agent-service/src/main/java/com/huawei/ascend/service/runtime/evolution/EvolutionExport.java` | 2 (package-info.java + `EveryRunEventDeclaresEvolutionExportTest.java`) | TRUE POSITIVE — the rc55 cited case. The test class enforces "every RunEvent declares EvolutionExport" but ZERO `RunEvent` types exist (confirmed via `find . -name 'RunEvent*.java'` returning empty). Vacuously true enforcer. Fixed by ADR-0145 + rc55 W1's `docs/contracts/run-event.v1.yaml`. |
| `SkillKind` | `agent-middleware/src/main/java/com/huawei/ascend/middleware/skill/spi/SkillKind.java` | 9 | NEGATIVE — not vacuous. SkillKind is actively used in `Skill` SPI; no fix needed. |

**Folded into W1.** ADR-0145 + contract creation handle the cited
case. No systemic siblings.

### 2.7 F-spi-package-bloat-with-carriers — 13 systemic hits (M10 cited surface was wrong; real offenders are middleware.*.spi)

**Fingerprint.** For every `*.spi.*` package: count interfaces vs
records vs sealed types vs enums; FAIL if (records + sealed + enums)
> interfaces.

**Hits.**

| SPI Package | Total | Interfaces | Records | Sealed | Enums | Carriers | Verdict |
|---|---:|---:|---:|---:|---:|---:|---|
| `engine.planner.spi` | 12 | 1 | 9 | 1 | 1 | 11 | TRUE POSITIVE — high carrier:interface ratio (11:1). Planner SPI in agent-execution-engine. |
| `middleware.advisor.spi` | 14 | 4 | 7 | 1 | 2 | 10 | TRUE POSITIVE — 10 carriers per 4 interfaces (2.5x). |
| `middleware.memory.spi` | 17 | 6 | 8 | 0 | 3 | 11 | TRUE POSITIVE — Agent-3 originally reported this as the rc55-cited surface; M10 ACTUAL OFFENDER. |
| `middleware.model.spi` | 8 | 2 | 3 | 2 | 1 | 6 | TRUE POSITIVE — 6:2 ratio. |
| `middleware.prompt.spi` | 3 | 1 | 1 | 1 | 0 | 2 | TRUE POSITIVE — 2:1 ratio. |
| `middleware.retrieval.spi` | 3 | 1 | 2 | 0 | 0 | 2 | TRUE POSITIVE — 2:1 ratio. |
| `middleware.skill.spi` | 9 | 2 | 5 | 1 | 1 | 7 | TRUE POSITIVE — 7:2 ratio. |
| `middleware.spi` | 4 | 1 | 1 | 1 | 1 | 3 | TRUE POSITIVE — 3:1 ratio. |
| `middleware.vector.spi` | 4 | 1 | 3 | 0 | 0 | 3 | TRUE POSITIVE — 3:1 ratio. |
| `service.agent.spi` | 11 | 2 | 8 | 0 | 1 | 9 | TRUE POSITIVE — 9:2 ratio. |
| `service.engine.spi` | 3 | 1 | 2 | 0 | 0 | 2 | TRUE POSITIVE — 2:1 ratio. |
| `service.runtime.resilience.spi` | 5 | 2 | 2 | 1 | 0 | 3 | TRUE POSITIVE — 3:2 ratio. |
| `engine.orchestration.spi` | 7 | 4 | 0 | 1 | 1 | 2 | BORDERLINE — 2 carriers per 4 interfaces (0.5x); acceptable. |
| `service.runtime.memory.spi` | 1 | 1 | 0 | 0 | 0 | 0 | CLEAN — only `GraphMemoryRepository`. The rc55 M10 finding had cited THIS package incorrectly; the actual offender is `middleware.memory.spi`. M10 cited surface CORRECTION. |

**Folded into W5.** M10 wording in plan file + family-yaml needs to
be CORRECTED: the cited surface is `agent-middleware/.../memory/spi/`
(17 files, 6 interfaces + 11 carriers), NOT
`agent-service/.../runtime/memory/spi/` (1 file, clean). The
systemic finding extends to 12 OTHER `*.spi.*` packages across
agent-middleware, agent-execution-engine, and agent-service. Bulk
Java refactor (carrier promotion) is OUT OF SCOPE for rc55 — defer
to a follow-up impl-mode wave. rc55 W5 documents the systemic gap
in the SPI Appendix with an explicit "(Rule R-D.d carrier-out-of-.spi
audit deferred to rcNN+1 impl-mode wave)" marker.

**Repo-wide systemic check.** All 21 `*.spi.*` packages across the
6 modules audited:
- agent-bus: 1 package (`bus.spi`) — empty (0 files) at scan time. NO HIT.
- agent-client: 1 package (`client.spi`) — empty. NO HIT.
- agent-evolve: 2 packages — both clean OR empty.
- agent-execution-engine: 3 packages — 1 hit (`engine.planner.spi`).
- agent-middleware: 9 packages — 8 hits.
- agent-service: 7 packages — 3 hits + the M10 cited surface CLEAN.

The systemic finding suggests Rule R-D.d (carriers stay in parent
package, .spi has only interfaces) has been ROUTINELY violated since
the agentic-primitives wave (rc43+) and the ergonomics wave (rc51).
This is a major-scope finding that warrants its OWN rc wave to fix.

## 3. Cited-finding corrections discovered by sibling sweep

The sweep revealed two corrections to my rc55 cited findings:

| Cited finding | Original wording | Correction discovered by sweep | Action |
|---|---|---|---|
| **M10** | "Memory SPI: 13 interfaces in `service.runtime.memory.spi`" | `service.runtime.memory.spi` has 1 interface (GraphMemoryRepository), 0 carriers. The actual 13-types case is `agent-middleware.memory.spi` (6 interfaces + 11 carriers). Original report was from a confused Agent-3 ground-truth read. | Update plan file §2.2 M10 wording. Update family yaml `F-spi-package-bloat-with-carriers` cited surface narrative. Refocus M10's W5 fix scope: audit + document the systemic 12-package bloat, but defer Java refactor. |
| **F3 sweep coverage** | "5 module ARCHITECTURE.md hits" | 5 of those are FALSE POSITIVE (domain-specific heading style obscures view content) | Refine fingerprint or add semantic-equivalence allow-list. Sweep regex documented as needing W5+ refinement. |

## 4. Sibling-sweep summary

| Family | New siblings folded into W1-W5 | Systemic gap deferred to future wave |
|---|---|---|
| F1 | 0 | none |
| F2 | 0 | none |
| F3 | 1 (root ARCHITECTURE.md frontmatter; DEFERRED due to freeze) | root ARCHITECTURE.md frontmatter mismatch fix needs separate proposal |
| F4 | 0 | sweep regex needs W5+ refinement |
| F5 | 1 (agent-service/ARCHITECTURE.md:812 carrier table marker; folded into W5) | none |
| F6 | 0 | none |
| F7 | 0 W1-W5 fix-ins; 12 systemic SPI-bloat hits documented for future impl-mode wave | bulk Java carrier-out-of-.spi refactor across agent-middleware + agent-execution-engine + agent-service |

Total: 2 fix-ins folded into rc55 W2 + W5 scope; 2 systemic gaps
deferred with explicit ADR placeholders.

## 5. G-A..G-F closure for Wave 0

- **G-A direct fix**: 7 NEW families registered + 4 existing families
  extended in `recurring-defect-families.yaml` + `.md` + `.md.j2`.
  Top-level `last_updated` bumped to 2026-05-26.
- **G-B classification**: every rc55 cited finding (M1..M11, R1..R8)
  mapped to exactly one family. No orphan findings.
- **G-C sibling sweep**: 7 family-fingerprint sweeps executed; 2
  fix-ins folded into W2/W5; 2 systemic gaps documented for future
  waves; 1 cited-finding correction (M10).
- **G-D continuous fix**: M10 cited-surface correction applied to
  the plan file and family yaml in this wave's next commit.
- **G-E non-vacuity**: every fingerprint matched at least one
  occurrence (the rc55 cited case at minimum); no fingerprint was
  vacuously satisfied.
- **G-F documentation**: this report.

## 6. Plan-file impact

The plan file at
`D:\.claude\plans\agentservice-l1-4-1-java-3-d-chao-worksp-squishy-steele.md`
will be updated in W1 to:
- §2.2 M10 wording correction (cited surface is middleware.memory.spi,
  not service.runtime.memory.spi).
- §3 Target State: add a section "12 systemic SPI-bloat siblings"
  with the per-package table; mark as W5 documentation + future-wave
  Java refactor.
- §4 Wave overview: add the W2 root-ARCHITECTURE.md frontmatter
  fix as a deferred sibling.
