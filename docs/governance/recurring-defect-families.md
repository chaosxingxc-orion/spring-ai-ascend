---
level: L0
view: scenarios
status: active
authority_refs: [ADR-0094]
---

# Recurring Defect Families — Human View

> **What this is.** A categorised summary of defect ROOT-CAUSE CLASSES that
> have recurred across multiple rc waves (rc4 → rc16). The canonical
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
>    `F-numeric-drift`). Catalogued here; cross-wave. Eight of them.
> 2. **Wave-local finding clusters** — Greek-letter suffix on the rc
>    review letter (e.g., "Family A" or "L-α", "M-η" in rc14/rc15
>    release notes). Ephemeral; specific to one review pass. Reset each
>    wave.
> When a release note says "Family A closed", it means the wave-local
> cluster. When this document says "F-numeric-drift partial", it means
> the permanent root-cause class. The two namespaces never overlap by
> regex (Greek letters / capital letters in release notes ≠ `F-` prefix
> here).

---

## §1 — Family Summary (12 families as of rc32)

| # | Family ID | Title | RC Occurrences | Cleanup |
|---|---|---|---:|---|
| 1 | F-numeric-drift | Numeric Drift Across Authority Surfaces | 9 | ⚠️ partial |
| 2 | F-deleted-module-name-leakage | Deleted-Module-Name Leakage After Refactor | 6 | ✅ structurally addressed (rc17) |
| 3 | F-authority-surface-path-drift | Authority-Surface Path Drift After Refactor | 8 | ⚠️ partial |
| 4 | F-kernel-vs-implementation-drift | Prevention Rule Kernel vs Implementation Drift | 4 | ⚠️ partial |
| 5 | F-cross-authority-agreement | Cross-Authority Surface Disagreement | 3 | ✅ structurally addressed (rc14-16) |
| 6 | F-deferred-clause-orphan | CLAUDE-deferred.md Orphan | 3 | ⚠️ partial |
| 7 | F-shadow-corpus-prose-staleness | Shadow Corpus Prose Staleness (gate/rules/) | 6 | ⚠️ partial |
| 8 | F-terminal-verb-overclaim | Active Kernel Terminal Verb vs Deferred Decision | 3 | ✅ closed (rc16) |
| 9 | F-recursive-prevention-irony | META Prevention Rule Exhibits the Defect Class It Prevents | 3 (rc17, rc19, rc20) | 🟡 monitoring (rc20 reopen — Rule 112 missed Rule 111 itself; closed by adding [META] marker + dogfooding fix, kept under monitoring until 3-rc cool-down) |
| 10 | F-progressive-loading-weak-enforcement | CLAUDE.md Kernel Loaded but Rules Don't Fire at Work Time | 1 (rc21) | ✅ closed — phase contracts + skills + dual-track loading per ADR-0098 |
| 11 | F-l1-architecture-grounding-gap | L1 Architecture Document Lacks Code-Mapping or SPI Enumeration | 10 (rc17-rc22+rc27-30) | 🟡 monitoring (rc32 reopen — rc29 marked closed but rc30 surfaced a regression in check_l1_dev_view_tree.sh; cool-down rc32+rc33+rc34) |
| 12 | F-bulk-scrub-orphan-syntax | Bulk Regex Scrub Leaves Orphan Punctuation in Code Comments | 4 (rc27, rc28, rc31, rc32) | ⚠️ partial (rc32 register — Rule D-9 bulk-regex scrub recurs every wave; structural fix is AST-aware tooling, deferred) |

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
re-baseline ritual at release time has not yet been codified.

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
Rule 108 (rc16 java anchor truth) → Rule 109 (rc16 namespaced ref).

**Open residual.** Every new rule MUST enumerate its authority surfaces.
Drift will reappear only if a rule introduces a new surface without
declaring it (Rule 110 META + Rule G-9 freshness gate).

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

**Prevention chronology.** Rule 99 (rc11) → Rule 108 (rc16).

**Open residual.** None. Rule 108 widens scope to all active corpus
prose; rc16 closure was the last known instance.

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

**Surfaces.**

- `agent-*/ARCHITECTURE.md` — all 6 modules.
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

**Cleanup status.** `structurally_addressed` — Rule G-1.1 ratifies
the depth/grounding discipline AND the rc22 wave brings every existing
L1 ARCHITECTURE.md into compliance. Sub-clause .c arms for W3+ when L2
docs land. Rc22.5 (package-root migration per ADR-0104) strips the
forward-compatibility `<!-- root-migration-target -->` markers and
revalidates the rule under the new namespace.

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
(rc32 + rc33 + rc34) per the rc20 / ADR-0097 convention.

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
