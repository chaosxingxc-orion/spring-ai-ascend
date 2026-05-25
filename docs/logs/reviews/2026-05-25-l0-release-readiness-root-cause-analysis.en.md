---
title: "L0 Release Readiness Root-Cause Analysis After 30+ RC Waves"
date: 2026-05-25
status: architecture-team-feedback
scope:
  - docs/logs/reviews/
  - docs/reviews/
  - docs/logs/releases/
  - docs/governance/recurring-defect-families.yaml
  - docs/governance/architecture-status.yaml
reviewer_role: Java microservice and agent-architecture reviewer
---

# L0 Release Readiness Root-Cause Analysis After 30+ RC Waves

## Executive conclusion

The architecture team is not failing because the L0 architecture lacks one more
major component. The service split, engine/SPI direction, memory/knowledge
boundary, dynamic-planning staging, skill-capacity model, S2C placement, and
agent-service expansion are broadly proportionate for L0.

The release is still unable to become a formal final release note because the
project's **release-readiness system is not transactional**. A release wave can
fix the cited defect, pass the current gate, and still leave one of these
behind:

1. a sibling scenario in the same defect family,
2. a stale authority surface that still states the pre-fix behavior,
3. a generated or derived corpus that was not refreshed,
4. a gate that proves local syntax/count parity but not semantic parity,
5. a baseline computed from the wrong tree or with an unclear formula,
6. a future-state ADR sentence copied into a current-state contract,
7. a meta-prevention rule that itself silently fails or covers the wrong scope.

That is why the same classes keep resurfacing across RC waves. The blocker is no
longer "does L0 have enough architecture?" The blocker is: **can the repository
prove that all authoritative statements, gates, generated views, status rows,
release evidence, and Java contracts describe the same shipped state at the same
commit?** Today the answer is still not consistently yes.

## Evidence base

The local corpus reviewed for this analysis contains:

| Corpus | Count |
|---|---:|
| `docs/logs/reviews/` files | 89 |
| `docs/reviews/` files | 6 |
| `docs/logs/releases/` files | 26 |
| Registered recurring defect families | 13 |

The registered family count is not an interpretation; it is the canonical
baseline in `docs/governance/architecture-status.yaml`, and the machine-readable
ledger lists 13 families in `docs/governance/recurring-defect-families.yaml`.

Important evidence:

- The first consistency review already stated the pattern: the gate passed, but
  it only checked syntactic corpus rules and missed semantic drift between
  architecture text, Maven dependencies, HTTP filters, runtime ownership, and
  tests (`docs/logs/reviews/2026-05-12-architecture-code-consistency-feedback.en.md:9-11`).
- The governance-era review named the underlying cognitive failure: treating
  flat ADR/rule enumerations as architecture design, which causes AI agents to
  satisfy isolated constraints while forgetting other constraints
  (`docs/logs/reviews/2026-05-14-architecture-governance-in-vibe-coding-era.en.md:12-15`).
- rc8 showed the release-readiness failure in miniature: all verification was
  green, yet the release evidence claimed 74 active gate rules while the live
  gate executed 102 sections (`docs/logs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md:100-152`).
- rc11 showed that the problem had become an authority-system defect: several
  gates passed while their own authority claims were no longer true
  (`docs/logs/reviews/2026-05-19-l0-rc11-contract-authority-constraint-systematic-review.en.md:33-58`).
- rc16 showed a meta-layer contradiction: the release, ADR, and enforcer text
  described rule-card front matter while the executable gate enforced comments
  inside the monolithic gate script (`docs/logs/reviews/2026-05-20-l0-rc16-post-closure-architecture-review.en.md:36-103`).
- rc27 recorded the worst failure mode: rc22-rc26 CI was green while the gate was
  effectively a no-op because shell functions were not inherited by the timeout
  subshell (`docs/logs/reviews/2026-05-22-rc27-corrective-wave-response.en.md:53-55`).
- rc35 second pass recorded the most important process lesson: a wave is not
  complete when the cited bugs are fixed; it is complete when the sibling
  scenario sweep returns empty (`docs/governance/recurring-defect-families.yaml:42`).
- rc36 recorded permanent silent passes in drift-prevention rules after the rule
  namespace migration (`docs/governance/recurring-defect-families.yaml:41`).
- rc38 recorded that even after gate and verify were green, a four-reviewer
  Java deep-read still found latent correctness and deploy-packaging defects,
  and registered a new recurring family
  (`docs/governance/recurring-defect-families.yaml:41`).

## How many issue classes exist?

There are 13 concrete recurring defect families in the official ledger. For
release-readiness analysis, those families collapse into 9 macro failure loops.
The difference matters:

- **Concrete family** means a named recurring defect with its own surfaces and
  prevention rules, such as numeric drift or non-atomic run-status writes.
- **Macro failure loop** means the systemic release-readiness mechanism that
  allows multiple concrete families to recur.

### Concrete recurring families

| # | Family | Occurrences | Status |
|---:|---|---|---|
| 1 | Numeric Drift Across Authority Surfaces | rc5, rc6, rc7, rc8, rc9, rc10, rc12, rc14, rc15, pr57, rc36, rc37 | partial |
| 2 | Deleted-Module-Name Leakage After Refactor | rc9, rc10, rc11, rc12, rc13, rc16 | structurally addressed |
| 3 | Authority-Surface Path Drift After Code Refactor | rc4, rc6, rc7, rc11, rc12, rc13, rc14, rc15, rc36 | partial |
| 4 | Prevention Rule Kernel vs Implementation Drift | rc6, rc7, rc11, rc15, rc35-second-pass, rc36 | partial |
| 5 | Cross-Authority Surface Disagreement | rc14, rc15, rc16, rc33, rc34, rc34-follow-up, rc34-merge-train, rc35-correctness-batch, rc35-second-pass, rc35-second-pass-ci-corrective, rc36 | structurally addressed |
| 6 | CLAUDE-deferred.md Orphan | rc12, rc15, rc16, rc36 | partial |
| 7 | Shadow Corpus Prose Staleness | rc7, rc8, rc9, rc10, rc14, rc15 | partial |
| 8 | Active Kernel Terminal Verb vs Deferred Decision | rc11, rc12, rc15 | closed |
| 9 | META Prevention Rule Exhibits the Defect Class It Prevents | rc17, rc19, rc20 | monitoring |
| 10 | CLAUDE.md Kernel Loaded but Rules Don't Fire at Work Time | rc21 | closed |
| 11 | L1 Architecture Document Lacks Code-Mapping or SPI Enumeration | rc17, rc18, rc19, rc20, rc21, rc22, rc27, rc28, rc29, rc30 | monitoring |
| 12 | Bulk Regex Scrub Leaves Orphan Punctuation in Code Comments | rc27, rc28, rc31, rc32 | partial |
| 13 | Non-Atomic Run Status Write Loses a Parallel Terminal Transition | rc35-correctness-batch, rc35-second-pass, rc36, rc38 | partial |

### Macro failure loops

| # | Macro failure loop | Concrete families it explains | Release-blocking mechanism |
|---:|---|---|---|
| 1 | Non-transactional authority refresh | 1, 3, 5, 6, 7, 8, 11 | One surface is fixed while CLAUDE, ADRs, status rows, contract catalog, OpenAPI, README, gate README, rule cards, release notes, Java docs, or generated graph remain stale. |
| 2 | Gate localism and vacuous success | 4, 5, 9, 10, 11 | A gate proves the surface it scans, but not the semantic claim being made, or it scans zero useful targets and still passes. |
| 3 | Baseline and evidence formula ambiguity | 1, 5 | Counts are copied or reflowed manually; release notes cite evidence that is not reproducible from the canonical command or tree. |
| 4 | Refactor without full reverse-index closure | 2, 3, 7, 11, 12 | Module/package/path moves update code first, while prose, generated views, samples, Docker, scripts, and Javadocs update later or not at all. |
| 5 | Current-state versus future-state blur | 5, 6, 8, 11 | ADRs correctly describe a future widening, but public contracts or status rows read as if the future state already shipped. |
| 6 | Sibling-scenario incompleteness | 4, 5, 13 | The exact cited bug is fixed, but other methods, states, modules, or branches with the same failure shape are not searched. |
| 7 | Meta-governance recursion | 4, 7, 9, 10 | Rules and ledgers added to prevent drift become additional authority surfaces that can themselves drift. |
| 8 | AI-context and work-time rule loading failure | 9, 10, 11 | The corpus is too large and too flat; agents satisfy a local rule but miss the scenario contract needed for the task. |
| 9 | Low-level implementation invariants not elevated to enforceable contracts | 4, 13 | Prose says "atomic", "not terminal", "tenant-isolated", or "replay-safe", but the Java SPI/gate/test contract does not force every implementation path to use the safe primitive. |

## Do issue types recur across multiple rounds?

Yes. Recurrence is the dominant pattern, not an exception.

The highest-frequency recurring classes are:

1. **Numeric drift**: at least 12 registered occurrences.
2. **Cross-authority disagreement**: at least 11 registered occurrences.
3. **L1 grounding gaps**: at least 10 registered occurrences.
4. **Authority-surface path drift**: at least 9 registered occurrences.
5. **Deleted-module leakage**: at least 6 registered occurrences.
6. **Kernel-vs-implementation drift**: at least 6 registered occurrences.
7. **Non-atomic run-status write**: 4 occurrences, including the latest rc38 wave.

These are not all independent. The same systemic failure repeats at different
scales:

- rc8: executable gate count and published baseline disagree.
- rc11: rule namespace authority disagrees across CLAUDE, ADR, status, rule
  cards, enforcers, graph nodes, and gate headers.
- rc16: meta prevention rule authority disagrees between rule-card front matter
  and gate-script comments.
- rc33/rc34: status rows reference ADRs before those ADR files exist, then the
  ADR arrival creates graph/baseline drift.
- rc35/rc36/rc38: cancel-vs-complete atomicity fixes land on one path, but
  sibling write paths remain non-atomic.

## Why 30+ RC waves still did not produce a final release note

### 1. The team kept fixing findings, not closure algorithms

Many waves fixed the reviewer-cited line or file. The repeated lesson is that a
finding is only a symptom. The closure unit must be the defect family and its
sibling surfaces.

Example: the cancel-vs-complete race was addressed in multiple places, but rc38
still found `SyncOrchestrator` using a private non-atomic helper. That means the
true missing artifact was not just an ADR or one CAS method; it was a repository
contract and static guard stating: "no status-changing `RunRepository.save(...)`
may bypass the atomic `updateIfNotTerminal` path."

### 2. The release note is being written before the release state is frozen

The release process appears to be:

1. fix known issues,
2. run available gates,
3. update release note and baseline claims,
4. receive review,
5. patch more surfaces.

That workflow is vulnerable because baseline claims, generated graph counts,
ADR counts, test counts, and family counts are moving while the release note is
being authored. The final release note must be generated or validated after the
tree is frozen, not estimated during the wave.

### 3. "Single source of truth" exists, but derived surfaces are still manually authored

`architecture-status.yaml` is canonical for many claims, but the same facts are
still restated in README, gate README, release notes, rule cards, ADRs, contract
catalog headers, generated graph metadata, and Java Javadocs.

A single source of truth does not prevent drift if downstream authority surfaces
are hand-maintained and not regenerated or byte-compared against the source.

### 4. Gates prove coverage, not release truth

Several RCs passed gates while the release was still not trustworthy:

- rc8: gate passed while release evidence disagreed with executed gate sections.
- rc16: gate passed while generated `gate/rules/` shadow files carried a
  different semantic rule.
- rc27: CI was green while the timeout subshell made the gate effectively no-op.
- rc36: drift-prevention rules were silent-passing after namespace migration.
- rc38: gate and verify were green before Java deep-read found five issues.

The gap is that a gate can answer "did this checker pass?" without answering
"does this release note accurately describe the shipped state of every
authority surface at this commit?"

### 5. ADRs are necessary but not sufficient

Some recurrence is due to missing or late ADRs, especially early agent-runtime
areas such as serializable payloads, capability/skill registry, resource matrix,
sandboxing, S2C, and W2/W4 durability paths. But after rc14 the dominant problem
is not "no ADR"; it is "ADR exists, but another authority surface disagrees or
copies the future part as current state."

Examples:

- ADR-0108/0116 define current cancel as W0 404 and future 403+audit as W1
  widening, while stale human/status surfaces still imply 403 is current.
- ADR-0118 registers the atomicity recurrence, but the safe invariant still has
  to become a Java-level and gate-level prohibition against sibling blind saves.

### 6. The corpus has an authority-boundary contradiction around logs

`docs/logs/` is described as an audit/review archive, but the latest release note
inside it is also an active gate input and the working surface for architecture
team responses. That is operationally reasonable, but it must be explicit:
logs are non-normative as design authority, yet normative as release workflow
evidence until their decisions are copied into CLAUDE, ADRs, contracts, status,
or module docs.

Without this distinction, agents can either ignore a live release constraint or
treat historical RC prose as current architecture.

### 7. The architecture governance system has become a product of its own

By rc17-rc20, the project added recurring-family ledgers, meta rules, helper
extraction, freshness gates, and rule-card synchronization. This was necessary,
but it also created a second architecture: the governance machinery.

The failure mode changed from "architecture docs are incomplete" to
"architecture-governance artifacts are themselves inconsistent." This is visible
in F-recursive-prevention-irony: the rule designed to prevent a class of defect
exhibited the defect class itself.

### 8. Agent-driven architecture needs scenario contracts, not only rule lists

The 2026-05-14 governance review was accurate: AI agents operating over a flat
ADR/rule corpus naturally optimize locally. They may satisfy C/S separation but
forget payload expiration, satisfy a path rule but miss semantic status-code
drift, or update a release count but not the generated graph.

The rc21 phase-contract work is the right direction. But release readiness still
needs a specific scenario contract: "formal release note publication." That
scenario should load only the release-critical constraints and require a fixed
evidence bundle.

## Root-cause ranking

| Rank | Root cause | Weight | Why it matters |
|---:|---|---:|---|
| 1 | Non-transactional authority refresh | Very high | Most recurrences are stale or divergent authority surfaces after a correct local fix. |
| 2 | Gate semantic scope is narrower than release claims | Very high | Green gates repeatedly coexisted with false release evidence. |
| 3 | Sibling-scenario closure is not mandatory | High | Fixes address cited examples, not all structurally equivalent paths. |
| 4 | Baseline formula and evidence generation are not fully derived | High | Counts are still hand-copied, reflowed, or computed against the wrong tree. |
| 5 | Current/future state model is too implicit | High | ADR future widenings leak into current public contracts. |
| 6 | Governance artifacts are overgrown relative to automation | Medium-high | More rules increase surfaces unless generated, deduplicated, and self-tested. |
| 7 | ADR gaps | Medium | Important early, but no longer the dominant failure mode. |
| 8 | Java microservice contract gaps | Medium | Atomicity and weakly typed payloads remain real, but narrower than authority-system drift. |

## What the architecture team should change before the next final release claim

### R1. Introduce a "release freeze transaction"

Before a formal release note is published, require a frozen commit and run a
single command that produces a release evidence bundle. The release note should
cite only values generated from that bundle.

Minimum bundle:

- latest commit SHA,
- latest ADR count and highest ADR id,
- active engineering rule count,
- active gate section count,
- gate self-test count,
- enforcer row count,
- recurring-family count and open/partial/monitoring rows,
- architecture graph node/edge counts,
- Maven test count from the canonical wrapper command,
- OpenAPI pinned-vs-live comparison,
- generated contract-catalog freshness marker,
- list of authority surfaces touched by the wave.

### R2. Make release note numeric fields generated, not authored

The release note should not hand-type baseline counts. Use a checked script that
emits a markdown table from the same extractors used by gates. If the author
edits those numbers manually, verification should fail.

### R3. Add a current-versus-forward contract template

Every `allowed_claim`, public contract paragraph, and release-note claim that
mentions a staged behavior must separate:

- `current_shipped_behavior`,
- `current_verified_by`,
- `forward_behavior`,
- `trigger_for_promotion`,
- `must_not_claim_before`.

This directly addresses cancel 404/403 drift, idempotency replay drift, value
yield drift, and design-only SPI promotion drift.

### R4. Require sibling-scenario closure for every recurring family hit

When a finding is classified under a recurring family, the response must include
a sibling sweep:

- same method family,
- same state-transition family,
- same module family,
- same generated/derived surface family,
- same platform/environment family,
- same future/current language family.

If no sweep is performed, the release note should say "corrective wave, not
final release candidate."

### R5. Convert critical semantic invariants into code/gate prohibitions

Some invariants cannot remain prose:

- no status-changing `RunRepository.save(...)` bypassing
  `updateIfNotTerminal`,
- no route contract that says W1 403 behavior is W0 shipped,
- no response-replay language while the active idempotency row says replay is
  W2 deferred,
- no `.spi` package anchors under old package roots,
- no gate rule that resolves zero live targets and passes.

### R6. Retire or fully regenerate shadow corpora

For every generated or derived surface, choose one:

- remove it from the active authority surface, or
- regenerate it in the release-freeze transaction and compare digests.

This applies to `gate/rules/`, architecture graph files, contract tables,
PlantUML/source views, pinned OpenAPI files, and any generated rule cards.

### R7. Clarify the authority role of logs

Add one explicit rule:

`docs/logs/**` is non-normative as architecture design authority, but the latest
release/review pair is normative as release workflow evidence until its accepted
outcomes are copied into authoritative design surfaces.

This prevents both false reliance on historical RC prose and false ignoring of
current release evidence.

### R8. Add a "formal release note" phase contract

The phase contract should say that a formal release note cannot be published
until:

1. all recurring families are `closed` or have an explicit accepted residual,
2. all `partial` and `monitoring` rows are named as residual risk,
3. all generated surfaces are refreshed or excluded,
4. the release evidence bundle is produced after freeze,
5. the current/future template is satisfied for every staged claim,
6. a sibling-scenario sweep has been performed for every family touched in the
   wave.

## Final answer to the root-cause question

The reason 30+ RC waves have not produced a trustworthy formal release note is
not that the architecture team lacks diligence. The evidence shows the opposite:
the team has created many ADRs, gates, self-tests, release notes, response logs,
and family ledgers.

The root cause is that **the corrective process is still document-and-gate
oriented, not release-transaction oriented**. It can close a local defect and
even add a prevention rule, but it does not always:

- freeze the tree first,
- derive release evidence from that frozen tree,
- update all authority surfaces atomically,
- prove current-vs-future semantic truth,
- sweep sibling surfaces in the same defect class,
- prove the prevention rule itself is non-vacuous,
- regenerate or retire shadow corpora.

Until those steps become one mandatory release transaction, every RC wave can
legitimately fix real problems and still create enough authority drift to block
the next formal release note.

