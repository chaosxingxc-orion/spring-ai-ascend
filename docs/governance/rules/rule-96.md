---
rule_id: 96
title: "Kernel-Deferred Clause Coherence"
level: L0
view: process
principle_ref: P-D
authority_refs: [ADR-0083, "rc8 post-corrective review P1-1"]
enforcer_refs: [E133, E134]
status: active
kernel_cap: 8
kernel: |
  **For every `## Rule N.<letter>` sub-clause heading in `docs/CLAUDE-deferred.md`, the matching `#### Rule N` kernel block in `CLAUDE.md` (between the heading and the next `---`) MUST contain the literal string `Rule N.<letter>` to acknowledge the deferred runtime obligation. Closes rc8 post-corrective review P1-1: Rule 42 and Rule 46 active kernels stated current-tense `MUST` for behavior CLAUDE-deferred.md correctly assigns to W2 sub-clauses; downstream readers couldn't reconcile the two authoritative sources. Rule 96 enforces the bidirectional link.**
---

# Rule 96 — Kernel-Deferred Clause Coherence

## Motivation

The rc8 post-corrective review (P1-1) found that two active rule kernels in `CLAUDE.md` stated current-tense `MUST` for behavior that `docs/CLAUDE-deferred.md` correctly deferred to W2:

- Rule 42 kernel said `The runtime SandboxExecutor MUST refuse a logical permission grant whose scope exceeds the declared physical limits.` — but `CLAUDE-deferred.md` 42.b deferred runtime refusal.
- Rule 46 kernel said `Callbacks consume the s2c.client.callback skill capacity declared in docs/governance/skill-capacity.yaml.` — but `CLAUDE-deferred.md` 46.b deferred runtime capacity admission to W2.

Two authoritative sources disagreeing creates a logical conflict for implementers: one says it's current `MUST`, the other says it's deferred. Rule 9 (self-audit ship gate) and Rule 28 (Code-as-Contract) cannot both be satisfied when active prose overclaims runtime enforcement.

The structural fix is the bidirectional link: the deferred sub-clause exists (CLAUDE-deferred.md 42.b, 46.b, etc.), AND the active kernel must explicitly reference it by name (`Rule 42.b`, `Rule 46.b`). That way readers see both halves of the truth at the kernel-reading step.

## Algorithm

The gate parses `docs/CLAUDE-deferred.md` for sub-clause headings of the form `## Rule N.<letter>` (e.g. `## Rule 42.b — SandboxExecutor Subsumption Runtime Check`). For each, the gate extracts the matching `#### Rule N` kernel block in `CLAUDE.md` (from the heading to the next `---`). The literal substring `Rule N.<letter>` (e.g. `Rule 42.b`) MUST appear in the kernel block.

If Rule N itself is deferred (not present as `#### Rule N` in CLAUDE.md), the check is skipped — the rule isn't active so it has no active kernel obligation to acknowledge sub-clauses.

## Why literal-string match, not semantic equivalence

Semantic equivalence checks ("does the kernel describe the same deferred behavior?") would need natural-language understanding and would be fragile. The literal `Rule N.<letter>` reference is a cheap, audit-friendly invariant: if the kernel cites the sub-clause ID, the bidirectional link exists; if it doesn't, the link is broken regardless of how the kernel describes the behavior.

## Enforcement

Enforced by E133 (Gate Rule 96 — `kernel_deferred_clause_coherence`). Positive self-test: a Rule N kernel containing `Rule N.b` passes. Negative self-test: a Rule N.b deferred sub-clause with no `Rule N.b` reference in the active kernel → FAIL.

## Activation

Activated 2026-05-19 by the v2.0.0-rc9 wave (rc8 post-corrective review response). Enforcer E133 + E134 (positive + negative self-test fixtures).

## Cross-references

- ADR-0083 — rc9 corpus-truth + CI-acceptance authority record.
- Rule 9 — self-audit ship gate (Rule 96 is a structural precondition for Rule 9 to be satisfied without false-positive findings against deferred obligations).
- Rule 28 — Code-as-Contract (active `MUST` requires enforcer; Rule 96 surfaces the case where the `MUST` should be deferred-pointing prose instead).
- `docs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` finding P1-1 — origin.
