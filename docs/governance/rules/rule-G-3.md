---
rule_id: G-3
title: "Kernel-Card-Implementation Coherence"
level: L0
view: scenarios
principle_ref: P-B
authority_refs: [ADR-0078, ADR-0083, ADR-0085, ADR-0094]
enforcer_refs: [E97, E98, E99, E133, E139, E140, E151]
status: active
scope_phase: commit
kernel_cap: 8
kernel: |
  **Kernel-Card-Implementation coherence is enforced across the CLAUDE.md / rule-card / CLAUDE-deferred triangle: each `#### Rule X` kernel paragraph in CLAUDE.md fits the per-card `kernel_cap` (sub-clause .a — Kernel Size Bounded); the kernel text byte-matches the card's `kernel:` scalar (sub-clause .b — Kernel-Card Match); every `#### Rule X` heading has a sibling `docs/governance/rules/rule-X.md`, every card has either a CLAUDE.md heading OR a `## Rule X.<letter>` reference in `docs/CLAUDE-deferred.md` (sub-clause .c — Every Active Rule Has a Card). Every `## Rule X.<letter>` sub-clause in CLAUDE-deferred.md MUST be acknowledged by a literal `Rule X.<letter>` in EITHER the CLAUDE.md kernel OR the rule card (sub-clause .d — Kernel-Deferred Coherence). Active kernel verbs implying shipped Run-state transition (`are SUSPENDED`, `transitions to FAILED`, `consumes the * capacity`, `is rejected, not failed`, `admits the caller`) MUST NOT appear when the matching obligation is explicitly deferred — neither in `CLAUDE.md` kernels nor in any active `agent-*/ARCHITECTURE.md` body text (sub-clause .e — Terminal-Verb vs Shipped-Decision; scope widened to module ARCHITECTURE.md in rc15 per ADR-0091). Disjunction-truth invariant (former sub-clause .f) split to Rule G-3.1 per ADR-0094.**
---

# Rule G-3 — Kernel-Card-Implementation Coherence

Operationalises across 6 sub-clauses. See `## Sub-clauses` below for the per-sub-clause assertion + enforcer mapping. Authority: [ADR-0078, ADR-0083, ADR-0085].

## Sub-clauses

### .a — (was sub-clause .a)

## Motivation

Without a per-section size cap, CLAUDE.md drifts back to monolithic motivation paragraphs as soon as a reviewer asks for "just a little more context." Rule G-3 sub-clause .a makes the cap a machine-checked invariant: each rule's body in CLAUDE.md is measured against the cap declared in its card front-matter. Daily principles get more room (12 lines) because they're read on every task; architectural rules get less room (8 lines) because their detail belongs in the on-demand card.

## Details

The gate counts lines from the `#### Rule NN` heading until the next `---` separator (exclusive of the separator). The card's `kernel_cap:` field is read from YAML front-matter. If a card does not exist, Rule G-3 sub-clause .a is SKIPPED for that section (Rule G-3 sub-clause .c catches the missing card).

The cap discipline is rule-class-specific because:

- **Daily principles (cap 12)** legitimately need a short bullet list inline (Rule D-3.a's pre-commit dimensions, Rule D-4's three layers). Loading the card on every task would be wasteful.
- **Architectural / ironclad (cap 8)** are referenced by file path or feature touch; their detail is on-demand and belongs in the card body, not the kernel.

## Cross-references

- Enforcer E97 — `gate/check_architecture_sync.sh#claude_md_kernel_size_bounded`.
- Companion: Rule G-3 sub-clause .b (kernel ↔ card byte-identity), Rule G-3 sub-clause .c (every rule has a card), Rule G-4 sub-clause .a (always-loaded byte budget).
- Authority: token-optimization wave PR1 — D:/.claude/plans/tokens-token-buzzing-sprout.md.

### .b — (was sub-clause .b)

## Motivation

When source-of-truth splits across two files, drift is inevitable without a mechanical check. Rule G-3 sub-clause .b makes the kernel in CLAUDE.md (what the agent reads at session start) and the card body (where motivation, tables, and sub-clauses live) provably consistent: the binding paragraph in the card front-matter is byte-identical (after whitespace normalisation) with the paragraph in CLAUDE.md.

## Details

Normalisation steps applied to both sides before comparison:

1. Strip CR characters (`tr -d '\r'`)
2. Collapse runs of spaces and tabs to a single space (`tr -s ' \t' ' '`)
3. Join lines (`tr '\n' ' '`)
4. Collapse multi-space runs again (`tr -s ' '`)
5. Strip leading/trailing whitespace

The card's `kernel:` field supports both YAML literal block style (`|`) and single-line scalar. Either works; the awk extractor handles both.

For Rule R-C.a and Rule R-M sub-clause .d (the longest rules), the kernel preserves the bolded imperative plus any embedded numbered list (Rule R-C.a's five enforcement surfaces). The card body holds the Constraint State Taxonomy table (Rule R-C.a) and the envelope-propagation matrix reference (Rule R-M sub-clause .d) — these do NOT participate in the kernel byte-comparison.

## Cross-references

- Enforcer E98 — `gate/check_architecture_sync.sh#claude_md_kernel_matches_card`.
- Companion: Rule G-3 sub-clause .a (size cap), Rule G-3 sub-clause .c (every rule has a card).
- Authority: token-optimization wave PR1.

### .c — (was sub-clause .c)

## Motivation

The kernel-and-card split is a contract: if a rule appears in CLAUDE.md, its expanded body MUST be reachable on disk; if a card exists, it MUST be either active (cited from CLAUDE.md) or deferred (cited from CLAUDE-deferred.md). Rule G-3 sub-clause .c makes both halves of the contract machine-checked. Without it, kernel shrinks could silently lose detail (rule in CLAUDE.md, no card) or stale cards could accumulate (card on disk, rule deleted).

## Details

The gate computes two sets:

1. **Active rule numbers** — extracted from `^#### Rule NN` headings in CLAUDE.md.
2. **Card numbers** — extracted from filenames `docs/governance/rules/rule-NN.md` (zero-padding stripped).

It fails on:

- **Missing cards**: a rule heading in CLAUDE.md with no matching card file.
- **Orphan cards**: a card file whose rule number is neither in CLAUDE.md nor mentioned as `Rule NN` (or `Rule NN.x` for sub-clauses) in `docs/CLAUDE-deferred.md`.

During the initial PR1 landing the `docs/governance/rules/` directory may not yet exist — the rule is vacuously true in that case so other rules can land first.

## Cross-references

- Enforcer E99 — `gate/check_architecture_sync.sh#every_active_rule_has_card`.
- Companion: Rule G-3 sub-clause .a (size cap), Rule G-3 sub-clause .b (byte-identity), Rule G-4 sub-clause .b (deferred-doc demote).
- Authority: token-optimization wave PR1.

### .d — (was sub-clause .d)

`) OR the matching `docs/governance/rules/rule-NN.md` card MUST contain the literal string `Rule N.<letter>` to acknowledge the deferred runtime obligation.**
---

# Rule G-3 sub-clause .d — Kernel-Deferred Clause Coherence

## Motivation

The rc8 post-corrective review (P1-1) found that two active rule kernels in `CLAUDE.md` stated current-tense `MUST` for behavior that `docs/CLAUDE-deferred.md` correctly deferred to W2:

- Rule R-L kernel said `The runtime SandboxExecutor MUST refuse a logical permission grant whose scope exceeds the declared physical limits.` — but `CLAUDE-deferred.md` 42.b deferred runtime refusal.
- Rule R-M sub-clause .d kernel said `Callbacks consume the s2c.client.callback skill capacity declared in docs/governance/skill-capacity.yaml.` — but `CLAUDE-deferred.md` 46.b deferred runtime capacity admission to W2.

Two authoritative sources disagreeing creates a logical conflict for implementers: one says it's current `MUST`, the other says it's deferred. Rule D-5 (self-audit ship gate) and Rule R-C.a (Code-as-Contract) cannot both be satisfied when active prose overclaims runtime enforcement.

The structural fix is the bidirectional link: the deferred sub-clause exists (CLAUDE-deferred.md 42.b, 46.b, etc.), AND the active kernel must explicitly reference it by name (`Rule R-L.b`, `Rule R-M sub-clause .d.b`). That way readers see both halves of the truth at the kernel-reading step.

## Algorithm

The gate parses `docs/CLAUDE-deferred.md` for sub-clause headings of the form `## Rule N.<letter>` (e.g. `## Rule R-L.b — SandboxExecutor Subsumption Runtime Check`). For each, the gate checks two surfaces:

1. The matching `#### Rule N` kernel block in `CLAUDE.md` (from the heading to the next `---`) — `_r96_kernel_has`.
2. The matching `docs/governance/rules/rule-NN.md` card — `_r96_card_has`.

Coherence is satisfied if **either** surface contains the literal substring `Rule N.<letter>` (e.g. `Rule R-L.b`). Only if BOTH are absent does Rule G-3 sub-clause .d fail.

If Rule N itself is deferred (not present as `#### Rule N` in CLAUDE.md), the check is skipped — the rule isn't active so it has no active kernel/card obligation to acknowledge sub-clauses.

## Why "either kernel or card" instead of "kernel only"

The rc10 post-corrective review (P1-3) noted that the original Rule G-3 sub-clause .d kernel said "the matching `CLAUDE.md` kernel block MUST contain" while the implementation accepted EITHER the CLAUDE kernel OR the rule card. The kernel-narrow-impl-broad drift was a Code-as-Contract violation in the rule whose whole job is preventing such drift. rc11 aligned the kernel + card + enforcer wording to the implemented "either surface" policy.

The justification for the broader policy: rule cards have no `kernel_cap`, so a rule with a long deferred discussion can cite the sub-clause in the card without bloating the always-loaded kernel. The structural invariant (the bidirectional link exists between deferred sub-clauses and active rules) is preserved regardless of which surface holds the literal reference.

## Why literal-string match, not semantic equivalence

Semantic equivalence checks ("does the kernel describe the same deferred behavior?") would need natural-language understanding and would be fragile. The literal `Rule N.<letter>` reference is a cheap, audit-friendly invariant: if either surface cites the sub-clause ID, the bidirectional link exists; if neither does, the link is broken regardless of how the kernel/card describes the behavior.

## Enforcement

Enforced by E133 (Gate Rule G-3 sub-clause .d — `kernel_deferred_clause_coherence`). The enforcer's truth-table:

| Kernel cites `Rule N.b`? | Card cites `Rule N.b`? | Outcome |
|---|---|---|
| Yes | Yes | PASS |
| Yes | No  | PASS (kernel-only path) |
| No  | Yes | PASS (card-only path) |
| No  | No  | FAIL — broken bidirectional link |

Positive self-tests:
- `test_rule_96_kernel_pos`: a Rule N kernel containing `Rule N.b` → PASS.
- `test_rule_96_card_only_pos`: a Rule N.b deferred sub-clause cited only in the rule card → PASS (rc11 addition).

Negative self-test:
- `test_rule_96_neg`: a Rule N.b deferred sub-clause with no reference in either the kernel or the card → FAIL.

## Activation

Activated 2026-05-19 by the v2.0.0-rc9 wave (rc8 post-corrective review response P1-1).

Kernel + card + enforcer wording aligned to the "either surface" policy by the v2.0.0-rc11 wave (rc10 post-corrective review response P1-3 + ADR-0085).

## Cross-references

- ADR-0083
- Rule D-5 — self-audit ship gate (Rule G-3 sub-clause .d is a structural precondition for Rule D-5 to be satisfied without false-positive findings against deferred obligations).
- Rule R-C.a — Code-as-Contract (active `MUST` requires enforcer; Rule G-3 sub-clause .d surfaces the case where the `MUST` should be deferred-pointing prose instead).
- `docs/logs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` finding P1-1 — origin.

### .e — (was sub-clause .e)

# Rule G-3 sub-clause .e — Kernel Terminal-Verb vs Shipped-Decision Check

## Motivation

The rc8 post-corrective review (P1-1) found that Rule R-L + Rule R-M sub-clause .d active kernels stated current-tense `MUST` for behaviour that `docs/CLAUDE-deferred.md` correctly assigned to W2 sub-clauses. The rc9 wave introduced Rule G-3 sub-clause .d to enforce the bidirectional link — each deferred sub-clause must be cited in the active kernel OR the rule card.

The rc10 post-corrective review (P1-1) then surfaced a SECOND form of the same drift: Rule R-K kernel said *"over-cap callers are SUSPENDED, not rejected"* while the shipped Java surface (`DefaultSkillResilienceContract.resolve`) returns a **decision envelope** (`SkillResolution.reject(SuspendReason.RateLimited)`), not a `Run.SUSPENDED` state transition. The deferred sub-clause Rule R-K.c (introduced in rc11) covers the actual scheduler admission step.

Rule G-3 sub-clause .d alone cannot catch this defect because Rule G-3 sub-clause .d checks for the *literal* `Rule N.<letter>` reference — it doesn't read the kernel's verb. The kernel could (and did) include `(W2 scheduler admission per Rule R-K.c)` while still saying *"are SUSPENDED"* in the main clause. The semantic claim is wrong even though the structural reference is present.

## What Rule G-3 sub-clause .e catches

Rule G-3 sub-clause .e is the SEMANTIC layer Rule G-3 sub-clause .d doesn't cover. The invariant: if a deferred sub-clause Rule N.<letter> exists in `CLAUDE-deferred.md`, the matching active kernel block for Rule N MUST NOT use end-state verbs that imply the deferred behaviour has shipped.

The end-state verb token list (rc11):

- `are SUSPENDED` / `is SUSPENDED` / `callers are SUSPENDED` — implies a `RunStatus.SUSPENDED` transition.
- `transitions to FAILED` / `transitions to SUSPENDED` — explicit state-machine claim.
- `consumes the * capacity` — implies actual capacity-counter mutation, not just decision-envelope return.
- `is rejected, not failed` — implies a Run-status outcome distinction.
- `admits the caller` — implies an admission decision that mutates scheduler state.

Decision-envelope-friendly verbs that PASS (the kernel is honestly describing what's shipped):

- `MUST return SkillResolution.reject(...)` — return value, not transition.
- `MUST consult the matrix` — schema-level enforcement.
- `MUST cite a yaml schema` — corpus-level enforcement.
- `MUST suspend via SuspendSignal` — describes a primitive call, but only paired with a deferred sub-clause naming the transition step.

## Algorithm

1. Parse `docs/CLAUDE-deferred.md` for `## Rule N.<letter>` headings → build the set of rule numbers with deferred sub-clauses.
2. For each `#### Rule N` heading in `CLAUDE.md`, extract the body (between heading and next `---`).
3. If Rule N has a deferred sub-clause AND the body contains any end-state verb token → FAIL with the rule id + matched verb.
4. Otherwise pass.

## Why not Rule G-3 sub-clause .d widening

Rule G-3 sub-clause .d enforces the *structural* invariant (literal `Rule N.<letter>` reference present). Rule G-3 sub-clause .e enforces the *semantic* invariant (kernel verb honestly describes shipped behaviour). Both are needed: the structural reference can be present while the semantic claim is wrong (rc10 P1-1 demonstrated this).

Widening Rule G-3 sub-clause .d to also check kernel verbs would conflate two cleanly separable invariants and make the rule harder to audit. Separate rules keep each one debuggable.

## Enforcement

Enforced by E139 (Gate Rule G-3 sub-clause .e — `kernel_terminal_verb_vs_shipped_decision_check`). Positive self-test: a Rule N kernel saying "MUST return SkillResolution.reject(...)" with matching Rule N.c deferred sub-clause → PASS. Negative self-test: a Rule N kernel saying "callers are SUSPENDED" with matching Rule N.c deferred sub-clause → FAIL.

## Activation

Activated 2026-05-19 by the v2.0.0-rc11 wave (rc10 post-corrective review response). Enforcer E139 + E140 (positive + negative self-test fixtures).

## Cross-references

- ADR-0085
- Rule G-3 sub-clause .d — `kernel_deferred_clause_coherence` (sibling: structural reference invariant; Rule G-3 sub-clause .e covers the semantic-verb invariant).
- Rule R-K — first known instance of the drift Rule G-3 sub-clause .e catches; rc11 narrowed the kernel + added Rule R-K.c sub-clause.
- `docs/logs/reviews/2026-05-19-l0-rc10-post-corrective-architecture-review.en.md` finding P1-1 — origin.

### .f — (was sub-clause .f)

# Rule G-3 sub-clause .f — Kernel-Implementation Disjunction Truth

## Motivation

The rc10 post-corrective review (P1-3) noted that Rule G-3 sub-clause .d's kernel was narrow (`MUST contain` on the CLAUDE.md kernel block) while its implementation was broad (accepted EITHER the kernel OR the rule card). This is the worst class of Code-as-Contract drift: a rule whose JOB is preventing kernel/deferred drift contains kernel/impl drift of its own.

The rc11 wave (per ADR-0085) chose to keep the broader "either surface" implementation (cards have no kernel_cap, so a long deferred discussion can live there without bloating CLAUDE.md) and align the kernel + card wording to match. Rule G-3 sub-clause .f prevents recurrence by enforcing the bidirectional declaration: for every rule whose impl uses `||`-style disjunction on a structurally-important predicate, the kernel AND the card MUST both carry the EITHER/OR wording.

## Why allow-list scope, not corpus-wide

A fully-general "scan every `_rNN_*` block for `&&` vs `||` and cross-check against the kernel's `AND`/`OR` connective" rule is fragile:

- Bash predicate grammar varies (`[[ ... && ... ]]`, `[[ ... ]] || [[ ... ]]`, multi-stage checks via temp variables).
- Some rules use multi-stage checks where the surface AND-vs-OR doesn't map cleanly to a single connective.
- Many `&&`/`||` joins are structural (`[[ $? -eq 0 ]] || _fail=1`), not semantically load-bearing.

The allow-list captures only rules where the disjunction is *structurally load-bearing* — meaning the difference between AND-implementation and OR-implementation would change which corpus inputs pass.

Initial allow-list (rc11):

- Rule G-3 sub-clause .d — `kernel_deferred_clause_coherence` (CLAUDE.md kernel block OR rule card).

Future additions surfaced by J-γ family sweeps:

- Rule M-2 sub-clause .a (yaml schema OR grandfather entry) — needs verification.
- Rule G-3 sub-clause .c (active heading OR deferred reference) — needs verification.
- Rule R-D sub-clause .g (catalog row OR `(internal)` mark) — needs verification.

Each addition requires kernel + card to explicitly declare EITHER/OR wording before the rule id lands in the allow-list. A new addition without kernel/card alignment will fail Rule G-3 sub-clause .f on its first run.

## Algorithm

1. Read `gate/rule-100-disjunction-allowlist.txt` (one rule id per line, `#` comments).
2. For each rule N in the allow-list:
   - Extract the `#### Rule N` block from `CLAUDE.md`.
   - Read the matching `docs/governance/rules/rule-NN.md` card.
   - Test BOTH for explicit disjunction tokens: `EITHER` (uppercase), `OR` (uppercase word), `either surface`, `either ... or`, `either kernel`, `either the`.
3. If either surface lacks the disjunction wording → FAIL with the rule id + (kernel=Y/N, card=Y/N) tally.

## Enforcement

Enforced by E141 (Gate Rule G-3 sub-clause .f — `kernel_implementation_disjunction_truth`). Positive self-test: a Rule N kernel + card both saying "EITHER the kernel OR the rule card" → PASS. Negative self-test: a Rule N kernel saying "the kernel block MUST contain" with `Rule N` added to the allow-list → FAIL (kernel=0, card=?).

## Activation

Activated 2026-05-19 by the v2.0.0-rc11 wave (rc10 post-corrective review response). Enforcer E141 + E142 (positive + negative self-test fixtures).

## Cross-references

- ADR-0085
- Rule G-3 sub-clause .d — first allow-list entry; the rule whose kernel-AND-impl-OR drift this prevention rule catches.
- Rule G-3 sub-clause .e — sibling: semantic-verb invariant for deferred sub-clauses (Rule G-3 sub-clause .e covers `MUST` verbs that overclaim shipped behaviour; Rule G-3 sub-clause .f covers `AND/OR` connectives that mismatch the implementation).
- `gate/rule-100-disjunction-allowlist.txt` — the canonical allow-list.
- `docs/logs/reviews/2026-05-19-l0-rc10-post-corrective-architecture-review.en.md` finding P1-3 — origin.
