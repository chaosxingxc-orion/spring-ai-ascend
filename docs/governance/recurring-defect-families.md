---
level: L0
view: scenarios
status: active
authority_refs: [ADR-0094]
---

# Recurring Defect Families — Human View

> **What this is.** A categorised summary of defect ROOT-CAUSE CLASSES that
> have recurred across multiple rc waves (rc4 → rc52). The canonical
> machine-readable form is [`recurring-defect-families.yaml`](recurring-defect-families.yaml);
> this `.md` is a rendered view for human readers and reviewers.
>
> **What this is NOT.** A release log. Per-wave narratives stay in
> `docs/logs/releases/`. Per-review findings stay in `docs/logs/reviews/`.
> This document is one level above both — it answers "what classes of
> defect have we seen, and is each one fully cleaned?"
>
> **When this is updated.** Every architecture refresh — new ADR, change
> in `architecture-status.yaml#baseline_metrics`, new release note in
> `docs/logs/releases/`, change in the `#### Rule X` heading set in
> `CLAUDE.md`. Gate Rule 111 (Rule G-9) FAILS if the refresh-signal
> commit predates this file's mtime by more than 24h.
>
> **Why this matters.** Reviewer scope is often narrower than defect
> scope (rc10 meta-lesson). Without a derived view of recurring families,
> the same root cause re-fires for 4-8 rc waves before someone realises
> it's a class, not a one-off. Rule 110 META gates the prevention-rule
> taxonomy; this document is the human-readable mirror.
>
> **Vocabulary note ("Family" disambiguation, rc18 Wave 3).** The word
> *family* is used at TWO scopes in this corpus; do not confuse them:
> 1. **Permanent root-cause classes** — `F-<slug>` (e.g.,
>    `F-numeric-drift`). Catalogued here; cross-wave. Twenty-seven of them.
> 2. **Wave-local finding clusters** — Greek-letter suffix on the rc
>    review letter (e.g., "Family A" or "L-α", "M-η" in rc14/rc15
>    release notes). Ephemeral; specific to one review pass. Reset each
>    wave. Twenty-seven permanent families are currently registered.
> When a release note says "Family A closed", it means the wave-local
> cluster. When this document says "F-numeric-drift partial", it means
> the permanent root-cause class. The two namespaces never overlap by
> regex (Greek letters / capital letters in release notes ≠ `F-` prefix
> here).

---

## §1 — Family Summary (27 families as of rc55 agent-service-l1-canonical-materialization)

rc55 agent-service-l1-canonical-materialization registers SEVEN new families
spanning the L1-design-discipline taxonomy (canonical-source provenance,
layer-cohesion, frontmatter↔body parity, logical-vs-structural decomposition,
design-only mechanism marking, discriminator-without-discriminated-type,
SPI-package bloat). rc54 agentic-completeness-corrective previously registered
F-agentic-contract-composition-gap: rc51 landed individual primitives, but
their cross-contract semantics did not compose. F-l0-agentic-primitive-gap
remains closed.

| # | Family ID | Title | RC Occurrences | Cleanup |
|---|---|---|---:|---|
| 1 | F-numeric-drift | Numeric Drift Across Authority Surfaces | 15 (rc40 codegraph-mcp-onboarding bumped active_gate_checks / enforcer_rows / gate_executable_test_cases / recurring_defect_families baselines) | ⚠️ partial |
| 2 | F-deleted-module-name-leakage | Deleted-Module-Name Leakage After Refactor | 6 | ✅ structurally addressed (rc17) |
| 3 | F-authority-surface-path-drift | Authority-Surface Path Drift After Refactor | 10 (rc39 stale Java SPI anchors) | ⚠️ partial |
| 4 | F-kernel-vs-implementation-drift | Prevention Rule Kernel vs Implementation Drift | 6 (rc6, rc7, rc11, rc15, rc35-second-pass, rc36) | ⚠️ partial |
| 5 | F-cross-authority-agreement | Cross-Authority Surface Disagreement | 15 (rc55 agent-service L1 canonical-materialization audit found 5 in-doc disagreements: M2 §0.4 stale transition prose, M3 RunRepository.updateIfNotTerminal wave-status drift, M4 TaskRepository vs TaskStateStore naming, M11 agent-invoke-request.v1.yaml cited without file existing, R4 ADR-0136 glossary aliases ChatAdvisor + RuntimeMiddleware as one mechanism) | ✅ structurally addressed (Rule 122/123/124 added for proposal documents; rc55 adds ADR-0140..0145 L1 canonical materialization closure) |
| 6 | F-deferred-clause-orphan | CLAUDE-deferred.md Orphan | 4 (rc12, rc15, rc16, rc36) | ⚠️ partial |
| 7 | F-shadow-corpus-prose-staleness | Shadow Corpus Prose Staleness (gate/rules/) | 6 | ⚠️ partial |
| 8 | F-terminal-verb-overclaim | Active Kernel Terminal Verb vs Deferred Decision | 5 (rc55 reopen — agent-service/ARCHITECTURE.md §runtime/resilience present-tense prose flanking deferred Rule R-K.c citation) | 🟡 monitoring (rc55 reopens from `closed` because the rc15 + rc53 closures did not cover narrative prose within agent-*/ARCHITECTURE.md flanking deferred-clause citations; cool-down required: 3 subsequent waves) |
| 9 | F-recursive-prevention-irony | META Prevention Rule Exhibits the Defect Class It Prevents | 3 (rc17, rc19, rc20) | 🟡 monitoring (rc20 reopen — Rule 112 missed Rule 111 itself; closed by adding [META] marker + dogfooding fix, kept under monitoring until 3-rc cool-down) |
| 10 | F-progressive-loading-weak-enforcement | CLAUDE.md Kernel Loaded but Rules Don't Fire at Work Time | 2 (rc21, rc39-formal-release-transaction) | ✅ closed — phase contracts + skills + formal release transaction workflow |
| 11 | F-l1-architecture-grounding-gap | L1 Architecture Document Lacks Code-Mapping or SPI Enumeration | 16 (rc55 PR-77 consistency review wave-1 follow-up: external reviewer found 7 cross-view + cross-doc grounding gaps in the post-rc55 4+1 set under `docs/L1/agent-service/` — ERD vs prose cardinality, RunRepository.updateIfNotTerminal signature drift, Task/Session PK type drift, stale test anchor, SSE W2-shipped vs W2-scope, README frontmatter underclaim, module-root legacy terminology) | 🟡 monitoring (rc55 resets review-artefact cool-down; derived L1 reviews must preserve enough evidence and scenario/data/control-flow grounding for reviewers to audit conclusions; Wave 1A finalises the candidate NEW family F-l1-view-set-internal-signature-drift) |
| 12 | F-bulk-scrub-orphan-syntax | Bulk Regex Scrub Leaves Orphan Punctuation in Code Comments | 4 (rc27, rc28, rc31, rc32) | ⚠️ partial (rc32 register — Rule D-9 bulk-regex scrub recurs every wave; structural fix is AST-aware tooling, partially addressed by rc51 Wave G2 JavaParser/libCST helpers under gate/lib/refactor/) |
| 13 | F-nonatomic-run-status-write | Non-Atomic Runtime State Write Loses Tenant or Terminal-State Invariants | 5 (rc35-correctness-batch, rc35-second-pass, rc36, rc38, rc39-formal-release-transaction) | 🟡 monitoring (rc39 broadened to tenant-owned runtime state; RunRepository SPI made abstract, save calls source-guarded to create-only sites, TaskStateStore writes made atomic) |
| 14 | F-project-tool-pin-drift | Project-Local Dev-Tool Pin Drift and Manifest Inconsistency | 2 (rc40-codegraph-mcp-onboarding + rc50-nodegraph-evidence) | ✅ structurally addressed (Rule 125 / E173 gates package.json exact-pin + lockfileVersion>=3 + .mcp.json relative-shim ref; rc50 adds local `.codegraph` nodegraph evidence without committing the SQLite database) |
| 15 | F-l0-agentic-primitive-gap | L0 Agentic-Primitive Contract Surface Gap | 3 (rc41-final-release-readiness + rc50-post-closure-senior-architect-review + rc51-agentic-completeness) | ✅ closed (rc51 — agentic-completeness wave adds 5 new SPI interfaces + 6 structural carriers + 4 contract YAMLs + 2 contract supplements + 7 ADRs 0129-0135 closing the developer-ergonomics-tier residual of the rc43 closure-by-construction) |
| 16 | F-agentic-contract-composition-gap | Agentic Contract Composition and Semantic-Closure Gap | 4 (rc51-agentic-completeness-review + rc52-agentic-completeness-corrective + rc53-post-closure-agentic-composition-review + rc54-agentic-composition-corrective) | 🟡 monitoring (rc54 closes the cited recurrence with AgentDefinition advisor bindings, typed same-package advisor payloads, shared hook ordering, and Rule 129 composition truth) |
| 17 | F-design-artifact-omits-tenant-spine | Design Artefact Omits tenantId First-Class Field | 1 (rc53-wave-1-agent-service-l1-4plus1-rewrite) | 🟡 monitoring (ADR-0136 + ADR-0138 §3 red line at the L1 design layer; gate-rule for tenantId-less ER blocks is a W5+ candidate) |
| 18 | F-design-doc-violates-three-track-bus | Design Artefact Proposes Queue / Event-Bus Abstraction Bypassing Rule R-E Three-Track Channels | 1 (rc53-wave-1-agent-service-l1-4plus1-rewrite) | 🟡 monitoring (ADR-0138 §3 red line binds Internal Event Queue to bus-channels.yaml three-track manifest; physical-isolation vs durability-tier conflation is structurally rejected at L1) |
| 19 | F-design-doc-language-bypasses-invariant | Design Artefact Wording Implies Bypass of Reactive / RLS / No-Sleep Invariants | 1 (rc53-wave-1-agent-service-l1-4plus1-rewrite) | 🟡 monitoring (ADR-0139 narrowed Fast-Path semantics forbid bypass-implying language; risk-phrase + invariant-preservation-clause gate-rule is a W5+ candidate) |
| 20 | F-placeholder-leaks-into-active-corpus | Anonymous-Name Placeholders Leak Into Active Documentation Corpus | 3 (rc53-wave-1-agent-service-l1-4plus1-rewrite + rc54-agentic-composition-corrective + pr71-agent-service-l1-review-wave-1) | 🟡 monitoring (rc54 adds Rule 127 current-release/current-response placeholder guard; broader slug grep remains a W5+ candidate) |
| 21 | F-l1-canonical-source-in-interaction-log | Live L1 Architecture Artefact Points at Freeze-Marked Review Log as Canonical Source | 1 (rc55-agent-service-l1-canonical-materialization) | 🟡 monitoring (ADR-0143 demotes the rc53 review file and rewrites agent-service/ARCHITECTURE.md §0.5 to point at docs/L1/agent-service/{scenarios,logical,process,physical,development}.md; proximity-based gate-rule for canonical/authoritative wording near review-log hyperlinks is a W5+ candidate) |
| 22 | F-layer-decomposition-low-cohesion | Logical-View Layer Owns Heterogeneous Responsibilities or Double-Homes With Another Layer | 1 (rc55-agent-service-l1-canonical-materialization) | 🟡 monitoring (ADR-0140 Engine Adapter Layer split + ADR-0142 Run aggregate single-owner; gate-rule for layer-description scan is a W5+ candidate) |
| 23 | F-frontmatter-claim-body-mismatch | Architecture-Doc Frontmatter Declares Views the Body Does Not Author | 1 (rc55-agent-service-l1-canonical-materialization) | 🟡 monitoring (rc55 W2 frontmatter discipline + view-per-file separation under docs/L1/agent-service/; gate-rule for frontmatter↔body parity is a W5+ candidate) |
| 24 | F-logical-vs-structural-decomposition-conflation | Single Architecture Artefact Carries Two Competing N-Element Decompositions Without a Mapping | 1 (rc55-agent-service-l1-canonical-materialization) | 🟡 monitoring (ADR-0144 publishes the canonical layer↔package matrix; gate-rule for ≥2 un-mapped N-decompositions in same .md is a W5+ candidate) |
| 25 | F-design-only-mechanism-shown-as-shipped | Design-Only Mechanism Depicted in Architecture Diagram Without Caption-Level Status Marker | 1 (rc55-agent-service-l1-canonical-materialization) | 🟡 monitoring (ADR-0141 Internal Event Queue layer demoted to design_only sub-section; rc55 W3/W4 view discipline annotates DualTrackRouter / SlowTrackJudge per ADR-0112 design_only status; cross-reference gate-rule for contract-status vs diagram is a W5+ candidate) |
| 26 | F-discriminator-without-discriminated-type | Typed Discriminator Ships Without the Polymorphic Type It Discriminates | 1 (rc55-agent-service-l1-canonical-materialization) | 🟡 monitoring (ADR-0145 specifies sealed RunEvent hierarchy + docs/contracts/run-event.v1.yaml; actual Java sealed type lands in a follow-up impl-mode wave; gate-rule for discriminator-zero-callsite is a W5+ candidate) |
| 27 | F-spi-package-bloat-with-carriers | SPI Package Contains More Structural Carriers Than Extension Interfaces | 1 (rc55-agent-service-l1-canonical-materialization) | 🟡 monitoring (rc55 W5 audits agent-service memory.spi package which has 1 interface + 12 carriers; carrier-promotion deferred to a follow-up impl-mode wave; gate-rule for carrier/interface ratio is a W5+ candidate) |

**Cleanup status legend.**
- ✅ **closed** — no recurrence expected; prevention rule covers all known surfaces; cool-down satisfied.
- ✅ **structurally addressed** — META rule in place that detects future variants; per-instance defects may still appear in newly-introduced surfaces but are now gateable.
- 🟡 **monitoring** — recently closed-then-reopened family; gate is in place but cool-down (rc + 3 waves of non-recurrence) not yet satisfied; flag for next reviewer pass.
- ⚠️ **partial** — prevention rules exist but cover known surfaces only; new surfaces remain at risk.
- ❌ **incomplete** — defects recur faster than prevention rules can be widened (no current family at this status as of rc20).

---

## §2 — Per-Family Detail

### F-numeric-drift — Numeric Drift Across Authority Surfaces

**Pattern.** Numeric claims (rule counts, gate counts, enforcer counts,
graph nodes/edges, self-tests) are estimated when a release note is
written and then never re-aligned. The first estimate becomes a de-facto
baseline; subsequent drift compounds until a reviewer catches it.

**Surfaces.**
- `docs/governance/architecture-status.yaml#baseline_metrics`
- `README.md` and `gate/README.md`
- Latest `docs/logs/releases/*.md`
- `CLAUDE.md` kernel numeric claims
- `docs/governance/architecture-graph.yaml` header

**Prevention chronology.** Rule 82 (rc6 single source) → Rule 91 (rc9
manifest match) → Rule 97 (rc10 release-note truth) → Rule 101 (rc12
namespace parity) → Rule G-8.a (rc14 graph baseline parity).

**Open residual.** rc16 P2-1 surfaced lingering drift. A structured
re-baseline ritual at release time has not yet been codified. pr57 added
a new vector: a contributor PR forked from a stale `main` and recomputed
baselines against the fork's base (claiming 133 gate rules / 238
self-tests vs live-on-merge-target 135 / 226). The prevention rules catch
doc-vs-baseline disagreement but not baselines computed against the wrong
tree — recompute every count against the merge target, not the branch point.

**rc40 recurrence.** Corrective proposal gates changed live counts
(135→138 active gate rules, 239→245 gate self-tests, 168→171 enforcer rows).
The fix is the same release-time discipline: regenerate the shadow gate corpus
and update `architecture-status.yaml`, `gate/README.md`, enforcer rows, and
release evidence together.

**rc50 supplement.** The CodeGraph nodegraph supplement kept the canonical
counts unchanged, but regenerated release evidence from frozen candidate
commit `b554d744` before publishing the latest release note. This is a
release-surface refresh, not a new numeric-drift occurrence.

---

### F-deleted-module-name-leakage — Deleted-Module-Name Leakage After Refactor

**Pattern.** Major structural refactors (Phase C / ADR-0078, ADR-0079,
ADR-0088) delete modules whose names persist in 7–10 corpus surfaces.
Each subsequent rc cycle finds another partition the previous prevention
rule did not scan, because reviewer scope was narrower than defect scope.

**Surfaces.**
- `agent-*/ARCHITECTURE.md`
- `docs/contracts/*.yaml`
- `ops/**/*.{yaml,yml,tpl,md}`
- `docs/quickstart.md`
- `.github/workflows/*.yml`
- `Dockerfile`
- `docs/**/*.puml`
- `**/module-metadata.yaml`
- `docs/telemetry/policy.md`
- `spring-ai-ascend-dependencies/module-metadata.yaml`

**Prevention chronology.** Rule 93 (rc9) → Rule 94 (rc9) → Rule 98 (rc10
broad-corpus widening) → Rule 103 (rc12 deploy entrypoints) → Rule 109
(rc16 namespace ratchet) → Rule G-2.1 (rc17 consolidation).

**Open residual.** Future refactors will still leak into new surfaces.
Rule 110 META + Rule G-9 freshness gate are the backstops; surface
discovery moves from reactive (reviewer finds it) to proactive
(prevention rule frontmatter enumerates it).

---

### F-authority-surface-path-drift — Authority-Surface Path Drift After Refactor

**Pattern.** Code refactors land first; authority surfaces lag 2–6 waves.
Adjacent to F-deleted-module-name-leakage but distinct: this family
covers path truth (correct module, wrong path) while the other covers
name truth (deleted module mentioned at all).

**Surfaces.**
- `docs/contracts/contract-catalog.md`
- `docs/contracts/*.v1.yaml`
- `agent-*/ARCHITECTURE.md` path claims
- `module-metadata.yaml#spi_packages`

**Prevention chronology.** Rule 84 (rc6) → Rule 94 (rc9) → Rule 98 (rc10)
→ Rule 103 (rc12) → Rule G-8.b/.d (rc14 parity) → Rule G-8.e (rc15
structural-carrier parity).

**Open residual.** Crosses with F-deleted-module-name-leakage. Reviewer-
narrow scope causes families to share residual surface.

---

### F-kernel-vs-implementation-drift — Prevention Rule Kernel vs Implementation Drift

**Pattern.** A prevention rule's kernel paragraph documents intent X
while its gate-script implementation checks only Y (Y ⊂ X). Worst class
of Code-as-Contract drift: the rule whose JOB is preventing drift itself
drifts.

**Surfaces.**
- `gate/check_architecture_sync.sh`
- `docs/governance/rules/*.md` kernel scalars
- `CLAUDE.md` kernel paragraphs

**Prevention chronology.** Rule 82 (rc6) → Rule 99 (rc11) → Rule 100
(rc11) → Rule 106 (rc14) → Rule 107 (rc16).

**Open residual.** Every newly-added rule carries the same risk. Rule
G-9 (rc17) requires `scope_surfaces:` frontmatter, making
kernel-vs-impl drift gateable at addition time, not only at the next
reviewer pass.

---

### F-cross-authority-agreement — Cross-Authority Surface Disagreement

**Pattern.** 117+ single-surface prevention rules can all pass while the
surfaces themselves contradict each other. Per-surface self-consistency
is necessary but not sufficient.

**Surfaces.**
- `architecture-status.yaml` ↔ `architecture-graph.yaml`
- `CLAUDE.md` kernel ↔ `rule-*.md` cards
- `module-metadata.yaml` ↔ `contract-catalog.md`
- `pom.xml` modules ↔ `architecture-status.yaml#repository_counts`

**Prevention chronology.** Rule 106 (rc14) → Rule G-8 (rc14 mega-rule,
4 sub-checks) → Rule G-8.e (rc15) → Rule 107 (rc16 clause parity) →
Rule 108 (rc16 java anchor truth) → Rule 109 (rc16 namespaced ref) →
rc34-follow-up adds in-wave manifestation evidence (ADR text vs shipped
Java; ADR `references:` paths not on disk; graph-builder dropping unknown
ADR-relationship keys) — strengthens the case for a W2 ADR
frontmatter-schema validation pass.

**Open residual.** Every new rule MUST enumerate its authority surfaces.
Drift will reappear only if a rule introduces a new surface without
declaring it (Rule 110 META + Rule G-9 freshness gate). rc34-follow-up
recurrence shows the pattern operates at TWO scales: cross-wave (rc33→rc34
follow-up commits) AND in-wave (single commit asserting present-tense
delivery for unauthored documents). rc34-merge-train adds a THIRD scale —
multi-PR squash-merge sequences where Rule G-9.b's first-parent semantics
demand a families.yaml content-diff in each post-merge commit, even when
the squash payload of the first PR in the train already carried one.
Candidate W2 remediation: cumulative-since-last-families-bump signal
detection, or merge-train pattern recognition in `gate/lib/check_recurring_families.sh`.

**rc40 recurrence.** The agent-execution-engine proposal mixed current,
accepted-forward, and W2+ exploratory claims: immediate W0/W1 scope appeared
beside pending boundary contracts, non-current package/executor names, and
future dynamic compiler/APG/sandbox capability language. Rule 122 rejects
proposal documents that claim immediate execution while still carrying pending
contracts; Rule 123 rejects non-current engine package/executor claims unless
marked proposed/future; Rule 124 rejects unsupported absolute safety/performance
phrasing unless backed by evidence or explicitly deferred.

rc35-second-pass adds a SIXTH micro-scale — *intra-PR squash-pattern
micro-recurrence*. The first-pass corrective commit (79a5dfc6) touched
signal surfaces (check_parallel.sh + a release note) without bumping
families.yaml, even though earlier commits (A/B/C) on the same branch
had already bumped it. Rule G-9.b's first-parent semantics fire on the
LATEST signal-touching commit, not on cumulative-since-last-bump. Same
mechanism as rc34-merge-train but at single-PR scope.

rc35-correctness-batch adds a FOURTH scale — *latent drift on a green main*.
Three parallel adversarial-review agents (correctness + adversarial-gate +
doc-coherence) found 8 cross-authority drifts against a corpus where the
gate (132/132) + self-tests (224/224) + CI all passed. Each was structurally
a different shape of the same root cause: ADR↔ADR contradiction (the
"shared/unified" audit MDC across ADR-0108/0109 sharing only 3 of 6 fields),
ADR↔code drift (ADR-0057 §2 promised TTL-recovery but no implementation
ever read `expires_at`), contract-field↔orchestrator drift (envelope
declared `deadline` but `.join()` had no timeout), rule↔orchestrator drift
(RunStateMachine is authoritative but terminal save used stale local
`run.status()`), claimed-atomic↔impl drift (`if get>0 then decrement`
TOCTOU in `release()`), fail-closed-intent↔shell-impl drift (gate scripts
silently passed when `rg` was missing), and config.yaml↔fallback-defaults
drift (`load_config.sh` hardcoded 60s but the YAML said 300s). Pattern:
single-surface gates remain green while cross-surface contracts silently
contradict; parallel adversarial review continues to be the only
structural defence. W2 candidate: a recurring "green-main hunt" CI workflow
that runs the same parallel-reviewer dispatch when nothing has changed,
on a weekly cadence.

**rc55 agent-service L1 canonical-materialization recurrence.** The
rc55 audit found FIVE in-doc cross-authority disagreements within
`agent-service/ARCHITECTURE.md` alone, distinct from prior cross-doc
patterns: (M2) §0.4 "Until full 4+1 reorganisation lands" stale
transition prose vs §0.5 rc53 4+1 ratification claim — the same doc
asserts both states simultaneously; (M3) `RunRepository.updateIfNotTerminal`
wave-staging drift — review §4.2 says "existing W1 method" while
ARCHITECTURE.md elsewhere says "abstract from rc39 per ADR-0118",
both true at different abstraction levels but unreconciled in prose;
(M4) `TaskRepository` vs `TaskStateStore` naming inconsistency
between ARCHITECTURE.md §11 prose and §11.2 SPI table +
`module-metadata.yaml`; (M11) `docs/contracts/agent-invoke-request.v1.yaml`
cited in §11.3 without verifying file exists on disk (verified
absent at rc55 audit); (R4) ADR-0136 §3 glossary aliases
`ChatAdvisor` + `RuntimeMiddleware` as one "Shadow Tool Interceptor"
mapping while they have different cardinality (per-ChatClient-call
vs per-HookPoint-per-Run) and different scopes (model-call boundary
vs hook-point boundary). Pattern: within-doc cross-authority drift
is a NEW scale of the family — prior occurrences were inter-doc
(authority surface A vs B); rc55 surfaces intra-doc (paragraph A
vs paragraph B within the same .md file). Closed at the design
level by ADR-0140..0145 wave + the rc55 L1 canonical materialization
(docs/L1/agent-service/ replaces the §0.5 review-doc pointer + per-
view file separation prevents future intra-doc accumulation).

---

### F-deferred-clause-orphan — CLAUDE-deferred.md Orphan

**Pattern.** Three-way sync (kernel ↔ card ↔ deferred-doc) is
author-driven; a kernel change can leave a deferred sub-clause
unacknowledged, or vice versa.

**Surfaces.**
- `CLAUDE.md` kernel
- `docs/governance/rules/*.md` cards
- `docs/CLAUDE-deferred.md`

**Prevention chronology.** Rule 99 (rc11 kernel-deferred coherence) →
Rule 107 (rc16 clause-parity ratchet).

**Open residual.** Sub-clauses can still be cited in one surface but not
the other two. Future ratchet would gate three-way set equality.

---

### F-shadow-corpus-prose-staleness — Shadow Corpus Prose Staleness (gate/rules/)

**Pattern.** `gate/rules/*.sh` is described as "IDE-only generated" but
prose comment blocks (slug names, ADR refs) drift as
`gate/check_architecture_sync.sh` evolves. Generated artefacts are not
actually regenerated on every change.

**Surfaces.**
- `gate/rules/*.sh`
- `gate/check_architecture_sync.sh` comment headers

**Prevention chronology.** Rule 92 (rc9) → Rule 97 (rc10) → Rule 109
(rc16).

**Open residual.** Genuine fix is to make `gate/rules/*` truly
auto-generated, or delete the directory. Low-severity but recurring.

---

### F-terminal-verb-overclaim — Active Kernel Terminal Verb vs Deferred Decision

**Pattern.** Active rule kernels use end-state verbs ("are SUSPENDED",
"transitions to FAILED", "consumes the * capacity") for behaviour whose
Run-state transition is W2-deferred. The shipped Java surface only
returns a decision envelope.

**Surfaces.**
- `CLAUDE.md` kernel paragraphs
- `docs/governance/rules/*.md` card kernels
- `agent-*/ARCHITECTURE.md` body text
- `docs/logs/reviews/*.md` (design proposals + 4+1 view drafts)
- `docs/L1/**/*.md` (per-view files when populated)

**Prevention chronology.** Rule 99 (rc11) → Rule 108 (rc16) → ADR-0138
§3 red-line c (rc53 — state-diagram annotation requirement) → rc55
W3/W4/W5 view-authoring discipline (every diagram + every prose
paragraph flanking a deferred-clause citation MUST carry an explicit
`(WN-deferred: <cause>)` marker).

**rc55 reopen.** `agent-service/ARCHITECTURE.md` §runtime/resilience
prose reads "over-cap callers receive a rejected decision envelope
`SkillResolution.reject(SuspendReason.RateLimited)` per Rule R-K
shipped surface. Translating that decision into `RunStatus.SUSPENDED`
is deferred to Rule R-K.c (W2 scheduler admission)" — but the
surrounding paragraph uses present-tense "consumes capacity" for the
rate-limited path. Reader can interpret the present-tense as "Run
actually transitions SUSPENDED at W0" when only the envelope is
shipped. `cleanup_status` bumped from `closed` (stale since rc53)
to `monitoring`. Closed at the design level by rc55 W4 process view
explicit `(W2-deferred: scheduler admission)` annotation per Rule
R-K.c on every diagram + ADR-0140..0145 wave. Cool-down required:
3 subsequent waves (rc55+1, rc55+2, rc55+3) with no recurrence
before re-promotion to `closed`.

**Open residual.** rc55 W3/W4/W5 ships the explicit annotation
discipline for newly-authored `docs/L1/agent-service/*` files. The
repo-wide sibling sweep in rc55 W0 audits other L1 docs for the
same prose-pattern. Gate-rule candidate for W5+ would close the
family structurally by automating the proximity check
(present-tense terminal verbs within ±5 lines of a deferred-clause
citation, FAIL without `(WN-deferred)` marker).

---

### F-recursive-prevention-irony — META Prevention Rule Exhibits the Defect Class It Prevents

**Pattern.** A META rule whose job is preventing defect class X is
itself vulnerable to class X. rc17 introduced Rule G-9 / Rule 111 to
prevent F-kernel-vs-implementation-drift on recurring-defect-family
ledgers — but reviewers found Rule 111 itself exhibited 6/8 defect
patterns: hand-edited mtime, empty-array vacuous pass, duplicate-field
compensation, scope-vs-impl gap, prose regex false-positive,
shallow-clone silent pass.

**Surfaces.**
- Newly-introduced META gate rules (rc16 Rule 110, rc17 Rule 111)
- Newly-introduced helper functions in `gate/lib/`
- Self-test fixtures that re-implement gate logic inline

**Prevention chronology.** Rule 111 (rc17 introduction, then hardened
in rc18 Wave 1 per ADR-0095 via helper extraction
+ 8 hardening fixes); Rule G-9 (rc17, strengthened by helper
extraction in rc18).

**Prevention chronology (updated rc20).** Rule 111 (rc17, hardened rc18
Wave 1, then [META]-marked rc20 Wave 1 per ADR-0097); Rule G-9 (rc17,
strengthened rc18); Rule 112 META-of-META (rc19 Wave 1 per ADR-0096 —
structurally enforces helper-extraction on `[META]`-marked rules);
Rule 113 paren guard (rc19 Wave 2); Rule 114 filename convention (rc19
Wave 4).

**Open residual (rc20 Wave 1 reopen).** rc19 promoted this family to
`closed` based on Rule 112 (META-of-META gate). The rc19 post-merge
review found Rule 112 did NOT actually gate Rule 111 itself — Rule 111
lacked the `[META]` header marker that Rule 112's scan requires.
Recursive irony moved one layer deeper instead of closing. rc20 Wave 1
adds `[META]` to Rule 111 (so Rule 112 now gates it) AND sources the
helper through a marker comment Rule 112's regex can resolve. Status
reopened to `monitoring` pending 3-rc cool-down (rc20 + rc21 + rc22
without recurrence) before re-promotion to `closed`.

---

### F-progressive-loading-weak-enforcement — CLAUDE.md Kernel Loaded but Rules Don't Fire at Work Time

**Pattern.** Progressive on-demand rule loading in CLAUDE.md: kernel
paragraphs auto-load into every session, but the full rule bodies in
`docs/governance/rules/rule-*.md` are only fetched when Claude
actively reads them. In practice the active read doesn't happen often
enough during work-time — the kernel paragraph is "available" in
context but not "attended to" when the model is reasoning about a
specific edit. Gate-time META defences (Rule 68 kernel-card
byte-match, Rule 110 META scope_surfaces, Rule G-9 family ledger)
catch the symptom AT GATE TIME but cannot enforce attention AT WORK
TIME.

**Occurrences.** 1 (rc21).

**Root cause.** The user observation 2026-05-21:
"现在的 claude.md 太臃肿了 ... 渐进式加载之后，很多契约并没有严格执行"
— CLAUDE.md is bloated; after progressive loading, many contracts
aren't strictly enforced. The 33-KB CLAUDE.md kernel index loads
into every session, but the model doesn't refocus on individual rule
paragraphs at phase entry.

**Surfaces.**
- `CLAUDE.md` kernel paragraphs (37 rule kernels + 13 principle pointers)
- `docs/governance/rules/*.md` (on-demand reads)
- `docs/governance/principles/*.md` (on-demand reads)

**Prevention rules.**
- ADR-0098 (rc21 — 6-phase scenario-loaded contracts)
- Rule G-10 (parallel-Linux-scripts mandate)
- Rule G-11 (phase-contract rule-allocation coherence)
- `.claude/skills/{design,impl,verify,commit,review}-mode.md` (5 phase-entry skills)
- `docs/governance/contracts/*.md` (5 phase contracts with Active Rules tables)
- `CLAUDE.md` Phase Entry directive table (Track 2 dual-track loading mechanism)

**Cleanup status.** ✅ closed — phase contracts + skills + dual-track
loading per ADR-0098.

**Open residual.** rc21 ships the structural fix. Empirical
verification of work-time enforcement requires observing the next
several work sessions — does Claude actually invoke `/design-mode`
when starting architecture work? Does the contract get cited during
the work? rc21 release note codifies the hypothesis; rc22+ may re-open
this family if the work-time behavioural change isn't observed.

**Self-review iteration on rc21.** The 3rd PR commit (5e4ec0e) closed
4 BUGs + 2 SMELLs found by adversarial self-review on the very rules
this wave introduced: Rule 114 fixture stale vs widened canonical
regex; ADR-0098 allocation map cited D-3.a/D-3.b as separate rows
while disk has unified D-3 card; README + status.yaml composition
arithmetic off-by-one on R count; allowed_claim arithmetic stale;
Rule 116 `wait` regex too permissive; `test_architecture_sync_gate.sh`
accidental fixture-string pass. Meta-lesson: structural fixes still
need adversarial self-review before merge — the
F-recursive-prevention-irony pattern can hide in helper logic the
rule's author didn't themselves write.

**CI corrective on rc21.** PR #31 CI surfaced one bug local WSL gate
could not reproduce: Rule 117's `printf | grep -Fxq` lookup pipeline
raced with `set -o pipefail` on fast GitHub Actions runners; the
resulting SIGPIPE truncated the cited-set capture, producing
false-orphan FAIL on R-C.1. Fix in commit d297b1d: materialise lookup
sets to temp files via `mktemp -d`. Meta-lesson: gate rules using
shell pipelines for set lookup under `set -o pipefail` are
timing-fragile across CI vs local runners — prefer materialised temp
files OR associative arrays OR `case` pattern matching for
deterministic behaviour.

---

### F-l1-architecture-grounding-gap — L1 Architecture Document Lacks Code-Mapping or SPI Enumeration

**Pattern.** Rule G-1.a (Layered 4+1 Discipline) mandates that every
architecture artefact declares `level:`/`view:` frontmatter, but does
NOT enforce DEPTH or GROUNDING of the content. An L1 ARCHITECTURE.md
can be 4+1-shaped yet still fail to map logical components to specific
package paths or enumerate the actual SPI surface the module ships.

**Observed.** At rc21 HEAD, the 6 `agent-*/ARCHITECTURE.md` files
showed uneven grounding: `agent-middleware/ARCHITECTURE.md` lacked a
Development View tree entirely; `agent-client/ARCHITECTURE.md` and
`agent-evolve/ARCHITECTURE.md` were explicitly skeleton-status with
no SPI appendix; the L1 ARCHITECTURE.md SPI section was not enforced
as a parity surface against `module-metadata.yaml#spi_packages` /
`docs/contracts/contract-catalog.md` / `docs/dfx/<module>.yaml`.
Rule R-D enforces three-way parity (catalog ↔ metadata ↔ DFX) but
not against the human-readable L1 architecture document.
rc40 adds a service-module recurrence: `agent-service/ARCHITECTURE.md`
omitted live platform/runtime package directories from the Development View and
counted structural carriers in the SPI appendix as active SPI interfaces.
rc55 adds three review-artefact recurrences: the generated OSS comparison review
preserved the conclusion but compressed away source-level project-by-project
non-equivalence, Layer 3 worker-contract, Layer 5 capability-discovery, and
Session/Memory concurrency evidence; the first capability-feature list carried
downstream-design advice, artificial status labels, priority-first grouping, and
weak scenario/data/control-flow grounding; the rewritten capability-feature list
still missed concrete enterprise Agent access, recovery, delegation, third-party
Agent, client-hosted skill, and configuration-ownership scenarios. The merge-train
PR-77 consistency review adds a fourth: even after canonical-4+1 materialisation,
the view set still carries internal signature drift (RunRepository.updateIfNotTerminal
across logical/process/physical), cardinality contradiction (ERD 1:1 vs prose 1:N),
PK type drift (logical vs physical), stale test anchors (cancel-scenario citing
non-existent method), and module-root staleness (legacy TaskRepository /
tenantMismatchReturns403 terminology).

**Surfaces.**

- `agent-*/ARCHITECTURE.md` — all 6 modules.
- `docs/logs/reviews/2026-05-26-agent-service-l1-oss-comparison-review.{cn,en}.md` — derived OSS comparison summaries.
- `docs/logs/reviews/2026-05-26-agent-service-l1-oss-comparison-source.cn.md` — preserved source comparison record.
- `docs/logs/reviews/2026-05-26-agent-service-module-capability-feature-list.{cn,en}.md` — derived module capability/feature reviews.
- `docs/logs/reviews/2026-05-26-agent-service-l1-4plus1-consistency-review-wave-1.cn.md` — PR-77 4+1 consistency review surfacing internal signature drift, ERD cardinality, PK type drift, and module-root staleness.
- `docs/governance/rules/rule-G-1.md` — parent rule lacked depth
  sub-clauses; the 2026-05-21 reviewer proposal flagged this gap
  ("hollow L1 architecture documents").

**Prevention.**

- Rule G-1.1 (rc22 / ADR-0099) — 3 sub-clauses:
  - .a Development View Code-Mapping (gate/lib/check_l1_dev_view_tree.sh, E166).
  - .b SPI Interface Appendix 4-way parity (gate/lib/check_l1_spi_appendix.sh, E167).
  - .c L2 Constraint Linkage prose (E168; vacuous until L2 docs land).
- 6 self-test fixtures (positive + negative per sub-clause) under
  `gate/test_architecture_sync_gate.sh` — `test_rule_G_1_1_a_pos`,
  `test_rule_G_1_1_a_neg`, etc.
- rc22 wave dogfoods: all 6 `agent-*/ARCHITECTURE.md` files were
  rewritten to satisfy Rule G-1.1 BEFORE the rule's enforcer went
  live (L3 live-corpus self-check per `/reviewer-feedback-self-check`
  methodology).
- rc55 OSS comparison review follow-up: preserve the full source comparison
  record in `docs/logs/reviews/` and expand generated summaries with
  project-by-project evidence so reviewers can audit why each OSS pattern is
  accepted, modified, or rejected.
- rc55 capability-feature review follow-up: rewrite derived L1 feature lists
  around full module ownership, S1-S5 scenario closure, data/control-flow closure,
  orthogonality checks, and OSS-average capability baseline.
- rc55 capability-feature scenario follow-up: require Agent Service capability
  reviews to expand canonical scenarios into concrete enterprise Agent access,
  recovery, delegation, third-party Agent, client-hosted skill, and configuration-
  ownership cases before deriving module features.

**Cleanup status.** `monitoring` — Rule G-1.1 ratifies the depth/grounding
discipline, but rc40 resets the cool-down because a live service architecture
document again drifted from code reality. The appendix now separates the 7
active Java SPI interfaces from SPI-adjacent structural carriers. rc55 resets
the derived-review side again for OSS comparison, module capability-feature
reviews, and the follow-up scenario-decomposition review.

**Open residual.** The SPI Appendix scanner now requires 4 surfaces
to agree (catalog + metadata + DFX + ARCHITECTURE.md appendix). A
maintainer who adds a new SPI must remember to update ALL four. The
4-way scanner is the structural backstop, but it does not auto-write
the appendix — that remains author discipline. Future improvement
could generate the appendix from the other three surfaces (machine-
derived L1 SPI section). **rc32 reopen:** rc30 surfaced a new
occurrence (sed delimiter collision in `check_l1_dev_view_tree.sh`
made Rule 118 silently pass every input). The family was prematurely
marked closed at rc29; reset to `monitoring` for a 3-rc cool-down
(rc32 + rc33 + rc34) per the rc20 / ADR-0097 convention. **rc40 reset:**
the same cool-down restarts after the service architecture tree/SPI appendix
drift. **rc55 OSS comparison reset:** derived review summaries must retain
enough source evidence to support their architectural conclusions; the current
branch closes the immediate gap by pairing the generated review with the
source record and expanded evidence matrices. **rc55 capability-feature reset:**
derived feature lists must avoid status-taxonomy and downstream-design overreach
and instead prove module ownership, scenario closure, normal/exception paths,
data flow, control flow, orthogonality, and OSS-average capability coverage.
**rc55 capability-feature scenario reset:** coarse S1-S5 anchoring is insufficient
unless the derived review enumerates concrete Agent access, recovery, delegation,
third-party Agent, client skill, and configuration ownership scenarios.

---

### F-bulk-scrub-orphan-syntax — Bulk Regex Scrub Leaves Orphan Punctuation in Code Comments

**Pattern.** Rule D-9 (no version/log metadata in code, rc20) is
enforced by a bulk per-line regex scrub across `.java`/`.py`/`.sh`
files. The scrub deletes tokens like `per ADR-NNNN`, `rcN`,
`(legacy Rule NN)`, `Wave N`, etc. — but the surrounding punctuation
that bound those tokens (open parens, leading commas, sentence-final
periods, trailing `+` connectors, `()` empty groups) is preserved
because the regex matches only the token, not its neighbours.

**Observed.** Recurs every wave a D-9 scrub passes over the corpus.
rc27 D-9 scrub left ~30 orphans; rc28 ADV-9 patched 6 files; rc31
ADV4-1..3 + 2 follow-ups patched 5 files; rc32 swept 8 residuals in
6 `package-info.java` + 1 SPI Javadoc file. rc31's own commit message
(`5597ed8`) explicitly named this as a candidate family but did NOT
register it — rc32 closes that meta-gap by formalising the pattern.

**Surfaces.**

- `**/*.java` javadoc and inline comments — package-info files most
  vulnerable because their Javadocs cross-reference ADRs / waves.
- `**/*.py` docstrings and comments — fewer instances historically
  but the bulk scrub applies the same regex.
- `gate/**/*.sh` comment headers — rc18 was supposed to strip
  `(legacy Rule NN)` parens but rc19 wave-3 found 8+ residuals.

**Prevention.**

- Rule D-9 (rc20 — the scrub being enforced; grandfather list of 54
  pre-existing files, sunset 2026-11-21).
- rc32 register the family in the ledger so the next D-9 scrub wave
  is aware of the orphan-punctuation pattern at design time.
- Future structural fix: switch from bulk regex to AST-aware tooling
  (JavaParser for Java, libCST for python, shfmt for shell). The
  AST tooling can re-bind the deleted token's neighbours instead
  of leaving them orphaned.

**Cleanup status.** `partial` — the regex scrub will continue to
recur on every new D-9 wave until AST-aware tooling replaces it.
Each manual sweep (rc27, rc28, rc31, rc32) clears the visible
residuals but does not prevent the next wave's recurrence.

**Open residual.** The structural fix (AST-aware tooling) is the
real prevention. Until then, the family will remain `partial` and
require a manual scrub each wave Rule D-9 is widened or the
grandfather list is reduced.

---

### F-nonatomic-run-status-write — Non-Atomic Runtime State Write Loses Tenant or Terminal-State Invariants

**Pattern.** Runtime invariants that span read + validation + write are not
protected by `ConcurrentHashMap` alone. A Run status transition done as a
separate re-read (`findById`) then blind write (`save`) is not atomic relative
to a parallel terminal write. The sibling TaskStateStore shape is `get` →
tenant-check → `put`, which lets two tenants racing the same new task id both
observe no owner and silently overwrite one another.

**Observed.** Recurred across four passes, each closing one set of
call-sites while a sibling set survived: rc35-correctness-batch closed the
three terminal SUCCEEDED/FAILED sites; rc35-second-pass closed the five
non-terminal sites; rc36 closed the `RunController.cancel` half via the new
atomic `RunRepository.updateIfNotTerminal` CAS; rc38 found that the
`SyncOrchestrator`'s own private `mutateIfNotTerminal` helper — the very
indirection the earlier waves routed writes through — was STILL a
non-atomic `findById`-then-`save`. The class was never registered as a
family before rc38, so the ledger could not flag the recurrence. rc39
broadened the same family to the TaskStateStore tenant-ownership race.

**Surfaces.**

- `SyncOrchestrator.java` — the private `mutateIfNotTerminal` helper (rc38).
- `RunController.java` — the cancel endpoint (rc36 closed).
- `RunRepository.java` — the SPI; `updateIfNotTerminal` is the atomic path.
- `InMemoryRunRegistry.java` — the dev-posture `computeIfPresent` impl.
- `InMemoryTaskStateStore.java` — tenant-owned task state must use atomic `compute`.

**Prevention.**

- Rule R-C.2.b — every `Run.withStatus` validates via `RunStateMachine`.
- rc36 (ADR-0116) — atomic `RunRepository.updateIfNotTerminal` CAS is the
  single sanctioned status-transition path.
- rc38 (ADR-0118) — `SyncOrchestrator` routed through the CAS + a
  deterministic read-modify-write-window regression test; family registered.
- rc39 — `RunRepository.updateIfNotTerminal` is abstract; production
  `RunRepository.save` calls are source-guarded to create-only sites;
  `InMemoryTaskStateStore.save` uses `compute` and has a two-tenant race test.

**Cleanup status.** `monitoring` — known RunRepository and TaskStateStore
surfaces now have focused source or concurrency guards.

**Open residual.** Keep the family under monitoring until three subsequent
release waves pass without another read-check-write sibling. Any new
tenant-owned or status-owned repository reference implementation must use
compute, compare-and-set, a conditional UPDATE, or an equivalent transaction
for invariants that span read + validation + write.

---

### F-project-tool-pin-drift — Project-Local Dev-Tool Pin Drift and Manifest Inconsistency

**Pattern.** Project-local development tools wired into the Claude Code MCP
layer (or any equivalent contributor surface) keep three independent
surfaces in lockstep: a `package.json` that pins versions, a
`package-lock.json` that pins the integrity-hashed dependency tree, and a
`.mcp.json` that names where the binary lives. Each surface is easy to
edit in isolation — a caret range "^0.9.4" replacing the exact pin, a
lockfile regenerated against a registry that doesn't mirror per-platform
optionalDependencies, a `.mcp.json` shim path edited to rely on `PATH`
lookup — and each change passes the shape-only review the file gets.
None of them announce themselves at runtime: Claude Code simply fails to
start the MCP server with an obscure error, and the next contributor's
`npm ci` silently installs a different binary than the previous
contributor used. The result is broken onboarding without an obvious
failure mode.

**Observed.** rc40-codegraph-mcp-onboarding registers the family
preventively, alongside the codegraph integration that wires the first
project-local MCP tool under `tools/codegraph/`. The motivation is to be
gateable BEFORE the first incident rather than after the third — the
rc18 META-lesson (recursive prevention irony) explicitly calls out that
prevention-time registration beats post-incident registration when the
pattern is foreseeable.

rc50-nodegraph-evidence extends the family from manifest/install truth to
the local regenerated nodegraph artifact. `.codegraph/codegraph.db`
remains git-ignored, but `gate/lib/build_codegraph_nodegraph_evidence.py`
records its file/node/edge/unresolved-reference counts into release
evidence so reviewers can see the artifact was included without freezing
machine-local SQLite state into the repository.

**Surfaces.**

- `tools/codegraph/package.json` — must declare `@colbymchenry/codegraph`
  at an exact `X.Y.Z` pin (no `^`/`~`/`>=` prefix).
- `tools/codegraph/package-lock.json` — must exist with `lockfileVersion >=
  3` so optionalDependencies get integrity hashes.
- `.mcp.json` — `mcpServers.codegraph` args must reference a relative path
  under `tools/codegraph/node_modules/@colbymchenry/codegraph/` so the
  install is cross-platform and PATH-independent.
- `gate/lib/build_codegraph_nodegraph_evidence.py` — converts the local DB
  shape into auditable YAML, including a true `repository.dirty: false`
  value for clean git worktrees.

**Prevention.**

- Rule R-A — the governing self-service kernel (contributor reaches first
  agent execution without platform-team intervention).
- Rule 125 / E173 — `codegraph_install_truth` gate verifies all three
  surfaces in lockstep. Pinning-truth gate (does the manifest declare a
  reproducible install?), not install-state gate (CI without `npm ci`
  still passes).

**Cleanup status.** `structurally_addressed` — gate Rule 125 runs against
every gate parallel/serial pass; any contributor edit that breaks one of
the three surfaces fails the gate with actionable repair guidance.

**Open residual.** Rule 125 remains codegraph-specific for install truth:
it hardcodes the `@colbymchenry/codegraph` package name and the
`tools/codegraph/` path. rc50 closes the local-artifact evidence gap by
adding nodegraph evidence for `.codegraph/codegraph.db` without committing
the database, and it keeps unknown git status distinct from clean status
in the generated YAML. A second project-local MCP tool either re-uses the
same structural template (a sibling rule with its own package name + path
pair plus local artifact evidence) or the rule is generalized to walk all
entries of `.mcp.json#mcpServers` and verify a `tools/<name>/` manifest
exists per entry. Generalization is deferred until a second tool lands;
the current concrete enforcer and evidence builder match the single
concrete tool the platform ships. The local DB and generated release
evidence bundles remain outside G-9.b signal surfaces: they are local
state and release output, respectively.

---

### F-l0-agentic-primitive-gap — L0 Agentic-Primitive Contract Surface Gap

**Pattern.** A platform can ship coherent governance, runtime kernel, and
engine plumbing while still leaving the brand-promised extension primitives
unmodelled. Mechanism: **scope conflation** between "L0 = structural
skeleton + governance" and "L0 = agent-tier contract layer" (the deleted
pre-Phase-C `agent-platform` Maven module is unrelated — see ADR-0078 for
its historical merger; this paragraph uses the modern agent-tier noun
phrase in the Audience-B narrative sense). Rule R-A
"Business/Platform Decoupling" can be VACUOUSLY satisfied (no extension
surface ⇒ no possibility of failure), so existing parity gates do not flag
the absence — only an independent reviewer asking "where does an Audience B
integrator extend X?" surfaces the gap.

**Observed.** rc41 final-release-readiness shipped as `formal_release: true`
at commit `d20d1e3`. An independent senior-architect review
(`docs/logs/reviews/2026-05-25-l0-senior-architect-reopen-recommendation.en.md`)
flagged 7 missing L0 contract shapes: Agent, ModelGateway, Tool vs Skill
semantic resolution, unified MemoryStore, VectorStore / Retriever /
EmbeddingModel, Planner, plus the Spring AI integration boundary.

**Second occurrence (rc50-post-closure-senior-architect-review → rc51-agentic-completeness).**
After rc43-rc48 closed the primitive tier (Agent/Skill/Memory/Vector/Planner)
and rc50 supplemented the CodeGraph nodegraph evidence, a second senior-architect
review found that the rc43 closure-by-construction left the **developer-
ergonomics tier** unmodelled: no `ModelGateway.stream(...)` for streaming output,
no `StructuredOutputConverter<T>` for typed-bean extraction, no `PromptTemplate`
SPI, no `ChatAdvisor` interceptor SPI. Audience B was still going to import
`org.springframework.ai.chat.{prompt,model.advisor,converter}.*` directly to
build a real agent, defeating Rule R-A "Business/Platform Decoupling"
non-vacuously and re-creating the very gap the rc43 wave was meant to seal.
Closed at rc51 by the agentic-completeness wave.

**Surfaces (rc43).**

- 14 new SPI Java interfaces under correct semantic-home modules:
  Agent → `agent-service.agent.spi`; Planner → `agent-execution-engine.planner.spi`;
  Model / Skill / Memory / Vector / Retriever / Embedding →
  `agent-middleware.{model,skill,memory,vector,retrieval,embedding}.spi`.
- 7 new `docs/contracts/*.v1.yaml` design_only contracts.
- 9 new ADRs (ADR-0120 through ADR-0128).
- 5 new Spring AI reference adapter shells under
  `agent-service.service.integration.springai`.

**Surfaces (rc51).**

- 5 new SPI Java interfaces under `agent-middleware`:
  `StructuredOutputConverter<T>` → `model.spi`; `PromptTemplate` →
  `prompt.spi`; `ChatAdvisor` + `AdvisorChain` → `advisor.spi`;
  `ConversationMemory` → `memory.spi`.
- 6 new structural carriers: `ModelResponseChunk` (sealed),
  `PromptTemplateSource` (sealed), `RenderedPrompt`, `AdvisedRequest`,
  `AdvisedResponse`, `ConversationTurn`.
- 1 SPI method addition: `ModelGateway.stream(ModelInvocation)` default
  returning `java.util.stream.Stream<ModelResponseChunk>`.
- 4 new `docs/contracts/*.v1.yaml` design_only contracts:
  `model-streaming.v1.yaml`, `structured-output.v1.yaml`,
  `prompt-template.v1.yaml`, `chat-advisor.v1.yaml`.
- 2 contract supplements: `memory-store.v1.yaml` `conversation_memory:`
  section; `model-invocation.v1.yaml` `tool_call_loop:` section.
- 7 new ADRs (ADR-0129 through ADR-0135).
- 2 new Spring AI reference adapter shells:
  `SpringAiBeanOutputConverterAdapter`, `SpringAiPromptTemplateAdapter`.

**Prevention.**

- Rule R-A — now non-vacuously satisfied across the developer-ergonomics
  tier too: real PromptTemplate + ChatAdvisor + StructuredOutputConverter
  + streaming extension seams exist; Audience B has no remaining
  Spring-AI-direct reach.
- Rule R-D — iterating logic auto-validates new SPIs across catalog ↔
  metadata ↔ DFX (now 9 spi_packages in agent-middleware).
- Rule G-1.1.b — L1 SPI Appendix 4-way parity gate (now 17 rows in the
  agent-middleware §SPI Appendix table).
- ADR-0120 / ADR-0122 / ADR-0125 — strategic decisions.
- ADR-0121 / ADR-0123 / ADR-0124 / ADR-0126 / ADR-0127 / ADR-0128 — primitive-tier SPI shapes.
- ADR-0129 / ADR-0130 / ADR-0131 / ADR-0132 / ADR-0133 / ADR-0134 / ADR-0135 — ergonomics-tier SPI shapes + AgentSession-as-Run-projection capture.

**Cleanup status.** `closed` (registered at rc43 by construction;
re-validated and broadened at rc51 to cover the developer-ergonomics tier).

**Open residual.** Implementations of the 19 SPIs (14 rc43 + 5 rc51) are
W2-W4 staged per the ADRs (W2 LLM gateway + streaming + structured-output,
W2 skill registry, W2 prompt-rendering, W2 advisor binding, W2 chat memory,
W2 memory adapters, W3 RAG vertical with RetrievalOptions.cacheStrategy
field, W3 SDK GA, W4 planner). The META-lesson from rc51: closure-by-
construction at one primitive tier can mask gaps at the adjacent
ergonomics tier; every L0 closure review MUST scan the developer-
ergonomics surface in addition to the primitive surface. Future addition
of new L0-level primitives (e.g., Observability SPI, Audit SPI, Workflow
SPI) MUST follow the same "land contract shape at L0 even when
implementation defers" pattern this family establishes; scope-conflation
between "structural skeleton" and "agent-tier contract layer" (historical
pre-Phase-C `agent-platform` Maven module was merged via ADR-0078; this
paragraph uses the modern agent-tier noun phrase) must be flagged at every
L0 final-release-readiness review.

---

### F-agentic-contract-composition-gap — Agentic Contract Composition and Semantic-Closure Gap

**Pattern.** Individually valid agentic primitives can still fail as a
composed developer contract when adjacent surfaces use incompatible carrier
types or terminal semantics. rc51 shipped the missing ergonomics-tier shapes,
but its first pass left four cross-surface contradictions: streaming model
output could not pass through advisors, conversation memory used a map-store
generic while prose required ordered windows, model finish reasons were free
strings while tool-loop logic treated them as an enum, and formal release
notes were published without clean evidence tied to the candidate commit.

**Surfaces.**

- `agent-middleware/src/main/java/com/huawei/ascend/middleware/{advisor,memory,model,retrieval}/spi`
- `docs/contracts/chat-advisor.v1.yaml`
- `docs/contracts/memory-store.v1.yaml`
- `docs/contracts/model-invocation.v1.yaml`
- `docs/contracts/model-streaming.v1.yaml`
- ADR-0129, ADR-0132, ADR-0133, ADR-0134
- `gate/lib/check_formal_release_transaction.py`
- Latest formal `docs/logs/releases/*.md` plus its evidence bundle

**rc52 deep sweep.** The corrective sweep grouped the rc51 findings into
four recurring classes before fixing code: strict same-package SPI purity,
cross-contract carrier mismatch, terminal-stream semantic mismatch, and
non-atomic formal-release publication. The sweep found additional latent
surfaces in retrieval (`Retriever` returning vector `Document`), human
family-view/template drift, and D-9 kernel-vs-gate wording drift.

**rc53 recurrence.** The post-closure agentic-composition review
(`docs/logs/reviews/2026-05-26-l0-rc53-post-closure-agentic-composition-review.en.md`)
found the same root cause in a narrower form after the rc52 repair:
`ChatAdvisor` exists as a primitive, but `AgentDefinition` has no advisor
binding despite ADR-0132 / `chat-advisor.v1.yaml` claiming
agent-definition-time composition; `AdvisedRequest` / `AdvisedResponse`
are schema-less maps with no canonical mapping to `ModelInvocation` /
`ModelResponse`; and the streaming advisor chain is not ordered relative
to `BEFORE_LLM` / `AFTER_LLM`.

**rc54 corrective closure.** The recurrence is closed by adding
`AgentDefinition.advisorBindings`, same-package `AdvisorBinding`, typed
same-package advisor payloads (`AdvisedModelRequest`, `AdvisedModelResponse`,
messages, tool calls, usage, and finish reason), and a non-SPI adapter for
model-carrier translation. ADR-0129, ADR-0132, chat-advisor, and
model-streaming contracts now share `advisor-model-hook-order/v1`; planner
and skill carrier invariants are constructor-owned where the contracts say
they are carrier-owned.

**Prevention.**

- Rule R-D — agentic SPI package purity is interpreted as no dependencies
  outside Java/JDK and same-package sibling carriers for the rc52
  agent-middleware surfaces.
- Rule G-8 — cross-authority agreement now includes composed contract
  semantics, not only per-file presence.
- Formal release transaction validation rejects dirty evidence, candidate
  SHA mismatch, non-formal latest notes, and evidence-path mismatch.
- `SpiPurityGeneralizedArchTest` pins the same-package dependency boundary
  for every `agent-middleware..spi..` class.
- Rule 129 now checks advisor composition truth: `chat-advisor.v1.yaml`
  cannot claim AgentDefinition composition unless the AgentDefinition contract,
  Java field, typed advisor carriers, and shared advisor/model hook sequence
  agree.

**Cleanup status.** `monitoring` — the cited rc51 surfaces were corrected by
rc52, but rc53 shows that strict same-package SPI purity can still leave
developer-facing composition semantics underspecified.

**Open residual.** The rc52 strict no-dependency enforcement covers the
new agent-middleware contract surfaces. Broader historical SPI packages in
`agent-bus`, `agent-execution-engine`, and `agent-service` still carry
intentional cross-package relationships from earlier architecture waves; a
future repo-wide SPI-purity rule would need a separately scoped migration.
The rc52 formal-publication follow-up also re-ran the recurring-family
ledger freshness check after the release note and CLAUDE template publication
surfaces changed; no additional problem type was found beyond this family,
so the canonical recurring-family count remained 16. rc53 wave-1 (agent-service
L1 4+1 rewrite) registered 4 new design-side families and the canonical count
became 20. The rc53 post-closure agentic-composition review does not add a
new family; it extends this existing family and keeps the canonical count at
20. rc54 closes that recurrence while preserving strict no-cross-SPI-dependency
design for the advisor surface. The family remains in monitoring for future
adjacent-contract composition surfaces.

### F-design-artifact-omits-tenant-spine — Design Artefact Omits tenantId First-Class Field

**Pattern.** Mermaid ER blocks / state-diagram blocks / field tables in
L1 and L2 design surfaces omit `tenantId` as a first-class field on
Run / Task / Session / StateStore, burying tenant scope in opaque
`metadata` strings or eliding it entirely. Future implementations
inherit the design gap and risk Rule R-C.2.a + R-J.a violations at code
time. Family is the L1-design-side sibling of the implementation-side
family F-nonatomic-run-status-write — both rooted in "tenant invariant
treated as a runtime concern, not a design concern".

**Surfaces.**
- `docs/logs/reviews/*.md` (design proposals + review responses)
- `docs/L2/*.md` (when L2 directory is populated)
- `agent-*/ARCHITECTURE.md` (L1 architecture docs)
- `docs/contracts/*.yaml` (schema declarations)

**Prevention.**
- ADR-0136 declares Run vs Task entity distinction + reaffirms tenantId
  as first-class field on each.
- ADR-0138 §3 red line: "No tenantId-less data model."
- Wave 2 Logical View ER block ships the canonical tenantId-first model.
- Gate-rule candidate (W5+): multi-line regex over `erDiagram` blocks
  in `docs/**/*.md` checking that every entity declares
  `tenantId` / `tenant_id`.

**Open residual.** The structural fix is at the ADR + L1-rewrite level.
A gate-rule that detects tenantId-less ER blocks in design surfaces is
a W5+ candidate.

### F-design-doc-violates-three-track-bus — Design Artefact Proposes Queue / Event-Bus Abstraction Bypassing Rule R-E Three-Track Channels

**Pattern.** Design docs introduce "internal event queue" or "message
bus" abstractions with their own durability axis
(in-memory / semi-persistent / persistent) without binding to the
canonical `docs/governance/bus-channels.yaml` three channels (`control`
/ `data` / `rhythm`). Conflates **physical isolation** (channels) with
**durability tier** (per-channel backend choice). Implementations would
lose the priority / heavy / heartbeat isolation guarantee.

**Surfaces.**
- `docs/logs/reviews/*.md` (esp. design proposals)
- `docs/L2/*.md` (when populated)
- `docs/contracts/*.yaml` (queue/bus schemas)
- `agent-*/ARCHITECTURE.md`

**Prevention.**
- ADR-0138 §3 red line: "No single-tier internal queue + mode-based
  durability." Binds to `bus-channels.yaml`.
- Rule R-E (Three-Track Channel Isolation) — runtime enforcement.
- Wave 3 Physical View three-track binding diagram for downstream
  design surfaces.

**Open residual.** The 2026-05-22 reference template's "internal queue"
prose was annotated with the three-track binding citation in Wave 5.
Future design surfaces fall under the same Per-Wave Acceptance Criteria
sweep discipline.

### F-design-doc-language-bypasses-invariant — Design Artefact Wording Implies Bypass of Reactive / RLS / No-Sleep Invariants

**Pattern.** Design docs use casual language ("no mandatory
persistence", "fast-path skips checkpoint", "lightweight synchronous",
"memory-only path") that, when read by an implementer under time
pressure, would license bypassing Rule R-G (reactive I/O), Rule R-H
(no Thread.sleep), Rule R-J.a (RLS on tenant_id tables), or Rule R-C.2
(RunRepository.updateIfNotTerminal CAS). The language is the upstream
cause; the implementation bug would be the downstream effect.

**Surfaces.**
- `docs/logs/reviews/*.md` (design proposals)
- `docs/L2/*.md`
- `docs/adr/*.yaml` (`context:` / `decision:` blocks)
- `agent-*/ARCHITECTURE.md`

**Prevention.**
- ADR-0139 narrows Fast-Path / Slow-Path semantics; explicitly forbids
  bypass-implying language.
- Wave 2 Logical View narrowing prose ships canonical L1 vocabulary.
- Gate-rule candidate (W5+): risk-phrase grep with same-paragraph
  invariant-preservation-clause requirement.

**Open residual.** The 2026-05-22 reference template's "compact edge
deployment" Fast-Path language was annotated with the
invariant-preservation pin in Wave 5. Gate-rule for risk-phrase +
same-paragraph invariant-preservation clause is a W5+ candidate.

### F-placeholder-leaks-into-active-corpus — Anonymous-Name Placeholders Leak Into Active Documentation Corpus

**Pattern.** Anonymous-name placeholders (`xiaoming` 小明,
`wanshoulu` 万寿路, `foo`, `bar`, `TBD`, `TODO-template`) leak into active
design surfaces — file slugs, prose, code-block author tags — without
being scrubbed before review. File slugs are particularly costly to fix
post-hoc because they become stable URLs.

**Surfaces.**
- `docs/logs/reviews/*.md` (file slugs)
- `docs/L2/*.md` (when populated)
- `docs/contracts/*.yaml` (`title:` / `description:` text)
- `agent-*/src/main/java/**/*.java` (variable / class / package names)

**Prevention.**
- Wave 1 review-draft thematic-slug rename
  (`agent-service-l1-4plus1-rewrite-wave-N`).
- Rule 127 current-release/current-response placeholder guard rejects live
  placeholder tokens in the active release note and latest review response
  while allowing documented family-vocabulary citations.
- Broad-corpus gate-rule candidate (W5+):
  `Grep "\bxiaoming\b|\bwanshoulu\b|\bTODO-template\b|\bTBD\b"` over
  `docs/{logs,L2,contracts,adr}/**/*.md` +
  `agent-*/**/*.java` with allow-list for documented citation contexts.

**Open residual.** The `2026-05-13-{wanshoulu}-wave-N-request.md` file
remains in the active corpus with an explanatory marker (Wave 5
closure); slug preserved for stable-URL stability. rc54 closes the live
release-note placeholder recurrence by replacing the rc53 Wave 8 token and
adding the Rule 127 guard. PR #71's original `xiaoming` review slugs remain
in the current contributor PR for URL stability; the family records the
recurrence while broader slug guards remain a W5+ candidate.

---

### F-l1-canonical-source-in-interaction-log — Live L1 Architecture Artefact Points at Freeze-Marked Review Log as Canonical Source

**Pattern.** An `agent-*/ARCHITECTURE.md` (L1 root per Rule G-1.a)
delegates its "canonical 4+1 view source" to a file under
`docs/logs/reviews/*.md`. The review file is an INTERACTION RECORD
per `docs/governance/logs-folder-policy.md` and is frozen / read-only
after wave closure. Promoting it to "authoritative L1 source" creates
two compounding defects: (a) Rule G-1.a's L0/L1/L2 view discipline is
bypassed — architecture lives where governance can't gate it;
(b) the freeze-mark means the "live" architecture cannot evolve
without writing a fresh review file + chasing the pointer.

**Surfaces.**
- `agent-*/ARCHITECTURE.md`
- `docs/L1/**/*.md`
- `ARCHITECTURE.md` (root)

**Prevention.**
- ADR-0143 (rc55 — review-log demotion + L1 canonical move;
  `agent-service/ARCHITECTURE.md` §0.5 rewritten to point at
  `docs/L1/agent-service/{scenarios,logical,process,physical,development}.md`).
- Candidate gate-rule for Wave 5+: Grep `agent-*/ARCHITECTURE.md` +
  `docs/L1/**/*.md` for markdown links to `docs/logs/reviews/.*\.md`
  with the proximity words {canonical, authoritative, 4+1 source,
  view source} within ±3 lines.

**Open residual.** ADR-0143 demotes the rc53 review file and
rewrites the §0.5 pointer; the canonical 4+1 source now lives at
`docs/L1/agent-service/` per the rc55 wave. Future review files
MUST NOT be promoted to "canonical" status again. Sibling sweep
across other modules (agent-execution-engine, agent-bus,
agent-middleware, agent-client, agent-evolve) is documented in
`docs/logs/reviews/2026-05-26-agent-service-l1-sibling-sweep.en.md`.

---

### F-layer-decomposition-low-cohesion — Logical-View Layer Owns Heterogeneous Responsibilities or Double-Homes

**Pattern.** A logical-view layer in an L1 architecture document
either (a) lists ≥4 heterogeneous component types as its
responsibilities, becoming a "kitchen-sink" layer with no clear
ownership boundary; OR (b) shares one or more responsibilities with
a different layer in the same diagram, creating undefined ownership
at the layer boundary. PR #72 rc53 surfaces both shapes in the same
diagram: review §15.1 "Engine Adapter Layer" lists 7+ heterogeneous
concerns (EngineRegistry + ExecutorAdapter + ChatAdvisor +
RuntimeMiddleware + ContextProjector + PromptTemplate +
StructuredOutputConverter) AND RuntimeMiddleware appears again in
"Task-Centric Control Layer" — double-homing.

**Surfaces.**
- `agent-*/ARCHITECTURE.md`
- `docs/L1/**/*.md`
- `docs/L2/**/*.md`
- `docs/logs/reviews/*.md` (design proposals authoring L1 views)

**Prevention.**
- ADR-0140 (rc55 — Engine Adapter Layer split into 5a Engine Dispatch
  & Execution + 5b Translation & Tool-Intercept; RuntimeMiddleware
  exclusively in Layer 4 Control).
- ADR-0142 (rc55 — Run aggregate single-owner: Manager layer owns
  the aggregate; Control layer holds typed reference + invokes
  `RunRepository.updateIfNotTerminal`).
- Candidate gate-rule for Wave 5+: pattern-match in
  `agent-*/ARCHITECTURE.md` + `docs/L1/**/*.md` for `Layer N`
  headings; count bullet items per layer; FAIL if ≥4 OR if same
  component name appears under ≥2 layers.

**Open residual.** The rc53 5-layer model (Access / Manager / Queue
/ Control / Adapter) is preserved with the ADR-0140 + ADR-0142
narrowings. Repo-wide sibling sweep audits the other 5 modules.

---

### F-frontmatter-claim-body-mismatch — Architecture-Doc Frontmatter Declares Views the Body Does Not Author

**Pattern.** An L0/L1/L2 architecture document's YAML frontmatter
declares `view:` and/or `covers_views: [...]` that the document body
does NOT actually author — no matching `## *Scenarios*` /
`## *Logical*` / `## *Process*` / `## *Physical*` / `## *Development*`
headings, no Mermaid diagrams for the claimed view. The frontmatter
therefore lies to the graph builder (`gate/build_architecture_graph.sh`)
and to any L0/L1/L2 consumer relying on frontmatter as the
authoritative view declaration. PR #72 rc53 surfaces the family in
`agent-service/ARCHITECTURE.md` whose frontmatter declares
`view: scenarios` AND `covers_views: [logical, development, process,
physical, scenarios]` while the body has no logical / process /
physical / development view section — §0.5 punts every non-scenarios
view to the rc53 review file.

**Surfaces.**
- `agent-*/ARCHITECTURE.md`
- `docs/L1/**/*.md`
- `docs/L2/**/*.md`
- `ARCHITECTURE.md` (root)

**Prevention.**
- rc55 W2 frontmatter discipline: every L1 view file declares
  exactly one `view:` value matching its body; `covers_views:` only
  used on index files where the body lists each covered view as a
  cross-link.
- Candidate gate-rule for Wave 5+: for every .md with `level:`
  frontmatter, parse `view:` + `covers_views:` and scan body for
  matching view headings; FAIL on any frontmatter-declared view
  absent from body.

**Open residual.** rc55 W2 ships the structural fix for agent-service.
Repo-wide sibling sweep audits the other 5 modules' ARCHITECTURE.md
for the same pattern.

---

### F-logical-vs-structural-decomposition-conflation — Single Artefact Carries Two Competing N-Element Decompositions Without a Mapping

**Pattern.** A single architectural artefact contains TWO different
N-element decompositions of the same scope (e.g. a 5-layer LOGICAL
decomposition and a 5-component PACKAGE-STRUCTURAL decomposition)
without an explicit mapping section linking the two. Reader cannot
tell which is canonical; implementer doesn't know which sub-package
a "layer" maps to. Family adjacent to but distinct from
F-cross-authority-agreement: that family is about TWO surfaces
disagreeing; this one is about ONE surface carrying two un-mapped
views internally. PR #72 rc53 surfaces the family in
`agent-service/ARCHITECTURE.md`: §11 "L1 Runtime-Role Decomposition"
lists 5 components (Dispatcher/Orchestrator/Task/Session/Engine) per
ADR-0100, while review §15 lists 5 layers (Access/Manager/Queue/
Control/Adapter) per ADR-0138.

**Surfaces.**
- `agent-*/ARCHITECTURE.md`
- `docs/L1/**/*.md`
- `docs/L2/**/*.md`

**Prevention.**
- ADR-0144 (rc55 — Layer↔Package Matrix: publishes the unified
  mapping table; declares 5-layer model as logical-view decomposition
  and 5-component model as package-structural decomposition; extends
  ADR-0100 + ADR-0138).
- Candidate gate-rule for Wave 5+: detect ≥2 distinct numbered
  N-table blocks inside the same .md without a "logical ↔ package
  mapping" section between them.

**Open residual.** Future architectural artefacts that introduce a
NEW decomposition MUST extend ADR-0144 or supersede it.

---

### F-design-only-mechanism-shown-as-shipped — Design-Only Mechanism Depicted in Diagram Without Caption-Level Status Marker

**Pattern.** Sequence / state / component diagrams in L1 / L2 design
surfaces depict interactions involving a mechanism whose contract
carries `status: design_only` (or whose Rule sub-clause is W2+
deferred) WITHOUT a caption-level `(design_only — ADR-NNNN)`
annotation. Implementers reading the diagram interpret it as a
shipped flow and inherit the design gap. Family adjacent to but
distinct from F-design-doc-language-bypasses-invariant: that family
is about LANGUAGE bypassing reactive/RLS/no-sleep invariants; this
one is about DIAGRAMS treating design-only mechanisms as shipped.

**Surfaces.**
- `docs/L1/**/*.md`
- `docs/L2/**/*.md`
- `docs/logs/reviews/*.md` (design proposals)
- `agent-*/ARCHITECTURE.md`

**Prevention.**
- ADR-0141 (rc55 — Internal Event Queue layer demoted to
  design_only sub-section per Rule R-E channel manifest).
- rc55 W3/W4 view discipline: every Mermaid sequence / state /
  component diagram caption MUST carry `(design_only — ADR-NNNN)`
  for any mechanism whose contract is `status: design_only` OR whose
  Rule sub-clause is W2+ deferred.
- Candidate gate-rule for Wave 5+: cross-reference parse — read
  `docs/contracts/*.v1.yaml` `status: design_only` entries → extract
  referenced types → grep `docs/L1/**/*.md` + `docs/L2/**/*.md`
  diagrams for those type names → FAIL if caption lacks `design_only`
  marker.

**Open residual.** ADR-0141 closes the Internal Event Queue case
structurally. DualTrackRouter / SlowTrackJudge annotation discipline
ships in rc55 W4 process view. Gate-rule for contract-status-vs-
diagram cross-reference is a W5+ candidate.

---

### F-discriminator-without-discriminated-type — Typed Discriminator Ships Without the Polymorphic Type It Discriminates

**Pattern.** A typed discriminator (enum, sealed-marker class,
`*Export`, `*Kind`, `*Type`) is shipped in production Java, but the
polymorphic type the discriminator was meant to label does NOT exist
on the classpath yet. Rule enforcers that depend on the discriminated
type become VACUOUSLY TRUE and silently arm for future drift — a
reader assumes the contract is gated when it isn't. PR #72 rc53
surfaces the family with `EvolutionExport` enum at
`agent-service/src/main/java/com/huawei/ascend/service/runtime/evolution/EvolutionExport.java`
whose javadoc declares it the discriminator for a sealed `RunEvent`
hierarchy that does NOT yet exist; Rule R-M.e "Every emitted RunEvent
declares EvolutionExport" is vacuously true because zero `RunEvent`
types exist.

**Surfaces.**
- `agent-*/src/main/java/**/*Export.java`
- `agent-*/src/main/java/**/*Kind.java`
- `agent-*/src/main/java/**/*Discriminator.java`
- `agent-*/src/main/java/**/evolution/*.java`
- `docs/governance/enforcers.yaml` (rules referencing armed-but-vacuous discriminators)

**Prevention.**
- ADR-0145 (rc55 — Sealed RunEvent Hierarchy specification; defines
  the sealed variants required by S1-S5 scenarios; specifies
  EvolutionExport binding; promotes Rule R-M.e from vacuously true
  to actively gated).
- `docs/contracts/run-event.v1.yaml` (rc55 — design_only at W1;
  promoted to runtime_enforced when Java sealed type lands).
- Candidate gate-rule for Wave 5+: scan all
  `agent-*/src/main/java/**/*Export.java` + `*Kind.java` enums; use
  `codegraph_callers` to count non-self call-sites; FAIL if a
  discriminator has 0-1 non-self callers AND a Rule kernel claims an
  enforcer over its consumers.

**Open residual.** ADR-0145 + `docs/contracts/run-event.v1.yaml` are
the design-side closures. The actual Java sealed RunEvent type lands
in a follow-up impl-mode wave; until then EvolutionExport remains
design_only-armed.

---

### F-spi-package-bloat-with-carriers — SPI Package Contains More Structural Carriers Than Extension Interfaces

**Pattern.** An `*.spi.*` Java package contains more structural
carriers (records / sealed / enums / value objects) than extension
interfaces. The package's published-SPI surface count becomes
inflated and the 4-way parity ledger (Rule R-D.e/.f/.g + Rule
G-1.1.b) becomes confusing — carriers mistakenly show up as SPI
rows, or are deliberately excluded from one surface but counted in
another. Family adjacent to but distinct from F-l1-architecture-
grounding-gap: that family is about the L1 doc lacking SPI
enumeration; this is about the `.spi` package itself conflating
"publish point" with "wire shape". Rule R-D.d explicitly mandates
`*.spi.*` packages contain ONLY extension interfaces (carriers
belong in the parent package), but no enforcer fires the rule at
gate time. PR #72 rc53 surfaces the family at
`agent-service/src/main/java/com/huawei/ascend/service/runtime/memory/spi/`
which contains 13 types (1 root interface `GraphMemoryRepository` +
12 carriers).

**Surfaces.**
- `agent-*/src/main/java/**/spi/*.java`
- `agent-*/module-metadata.yaml#spi_packages`
- `docs/contracts/contract-catalog.md`
- `docs/dfx/agent-*.yaml`

**Prevention.**
- Rule R-D.d (rc1 — `*.spi.*` package convention; mandates extension
  interfaces only in .spi sub-packages).
- rc55 W5 SPI Appendix discipline: every .spi package audit
  explicitly lists `interfaces` (extension points) vs `carriers`
  (records/enums); carriers promoted out of .spi per Rule R-D.d.
- Candidate gate-rule for Wave 5+: for every `*.spi.*` package,
  parse public classes; count interfaces vs records vs sealed types
  vs enums; FAIL if (records + sealed + enums) > interfaces.

**Open residual.** rc55 W5 audits the agent-service memory.spi
package and either splits into focused sub-packages (memory.spi.read,
memory.spi.write) OR promotes carriers out of .spi per Rule R-D.d.
The bulk Java refactor (file moves) is deferred to a follow-up
impl-mode wave; rc55 W5 documents the intended split with an ADR
placeholder + `(impl rcNN+1)` marker.

---

## §3 — META-Lessons Codified Into Rules

The recurring-family pattern itself is a defect class. The following
meta-lessons were progressively codified across rc waves; rc17 adds Rule
G-9 + this document as the structural backstop.

| Wave | Meta-Lesson | Codification |
|---|---|---|
| rc8 | "Category-wide sweep finds 14× more defects than reviewer's one cited surface" (rc8 found 14 hidden behind 8 cited findings) | rc8 release-note methodology paragraph; rc10 adopts as standing discipline |
| rc10 | "Reviewer scope can be narrower than defect scope" — Rule 94 scoped to 3 surfaces; family I-ε spans 7 | rc10 release-note explicit paragraph (P0-2); rc11 doubles down (J-β found 30+); **rc16 Rule 110 META** finally gates the discipline |
| rc11 | "Categorize → Sweep → Batch-fix → Prevention" is now a hard sequencing rule | rc11 release-note Methodology section; followed by every wave rc12 onward |
| rc12 | "Authority-surface parity is a separate gate from kernel-card coherence" | Rule 101 (rc12); Rule G-8 (rc14 consolidates the variants) |
| rc12 | "Lex sort on dated filenames is a class bug, not one-off" — encode 'latest' as typed sortable field | `gate/lib/latest_release.sh::latest_release_path` (rc12); Rule 102 gates the pattern |
| rc14 | "Cross-authority parity is its own defect family" — 117 single-surface rules can all pass while surfaces contradict each other (L-δ META) | Rule G-8 (rc14 mega-rule, 4 sub-checks); widened rc15 Rule G-8.e + Rule G-3.e |
| rc15 | "Sub-clause widening > sibling rule for cross-surface families" | Rule G-8 establishes the pattern (rc15); ADR-0091 records the decision |
| rc16 | "Recurrence pattern is itself gateable" | Rule 110 META — every prevention rule MUST declare `scope_surfaces:` + ≥2 self-test fixtures across distinct surfaces |
| **rc17** | **"Recurring families deserve a first-class derived view"** — reviewer should be able to ask "has this class been seen before?" without re-reading 16 release notes | **This document + `recurring-defect-families.yaml` + Rule G-9 + Gate Rule 111 freshness check** |
| **rc18** | **"Recursive irony — a META prevention rule must first prove it doesn't itself exhibit the defect class it prevents"** — rc17 Rule 111 had 6/8 of its own defect patterns | **F-recursive-prevention-irony added as permanent family; helper-extraction template (`gate/lib/check_recurring_families.sh`) is the structural fix; all future META rules follow this template + validate with 6+ negative scenarios** |
| **rc19** | **"Surface fixes do not close depth"** — rc18 closed the surface bypasses on Rule 111 but reviewers found 4 deeper structural assumptions in the helper itself (awk fragility, mtime proxy, hard-coded paths, no META-of-META gate) | **Python pyyaml + content-diff freshness via `git show {sha}^1:{yaml}` + auto-derived signal paths + Rule 112 META-of-META that dogfoods itself (per ADR-0096)** |
| **rc20** | **"A META-of-META gate doesn't close the family if its own scan misses the original META rule"** — rc19 Rule 112 scanned for `[META]`-marked headers but Rule 111 (the prototype META rule) was never marked, so Rule 112 silently exempted the very rule the family is named after | **rc20 Wave 1 adds `[META]` to Rule 111 + literal-path source marker so Rule 112 actually gates it; family reopened to `monitoring` pending 3-rc cool-down; cool-down convention added to the legend (per ADR-0097)** |

---

## §4 — Cross-references

- Authority: [ADR-0094](../adr/0094-rc17-recurring-defect-family-truth-and-rule-consolidation.yaml) — rc17 recurring-defect-family-truth + rule-consolidation.
- Machine form: [`recurring-defect-families.yaml`](recurring-defect-families.yaml).
- Freshness gate: Rule G-9 (Gate Rule 111) — enforces yaml well-formedness, mtime ≥ refresh-signal commit, and yaml↔md family-id parity.
- Refresh skill: [`/refresh-defect-archive`](../../.claude/skills/refresh-defect-archive.md) — project-scoped Claude skill that re-runs the family-derivation pipeline and bumps `last_updated`.
- Companion ADRs by wave (chronological): ADR-0079 (rc4-5), ADR-0080 (rc6), ADR-0081 (rc7), ADR-0082 (rc8), ADR-0083 (rc9), ADR-0084 (rc10), ADR-0085 (rc11), ADR-0086 (rc12), ADR-0087 (rc12), ADR-0088 (rc13), ADR-0089 (rc13), ADR-0090 (rc14), ADR-0091 (rc15), ADR-0092 (rc15), ADR-0093 (rc16), ADR-0094 (rc17).
- `docs/governance/rules/README.md` — taxonomy of D-/R-/G-/M- rule prefixes and the `.1` `.2` sub-rule convention introduced in rc17.
