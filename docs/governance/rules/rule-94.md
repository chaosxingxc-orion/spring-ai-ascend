---
rule_id: 94
title: "Active Corpus Deleted-Module Name Truth"
level: L0
view: development
principle_ref: P-D
authority_refs: [ADR-0083, "rc8 post-corrective review P1-3"]
enforcer_refs: [E129, E130]
status: active
kernel_cap: 8
kernel: |
  **Every active `.md`, `.yaml`, and `*.java` file (excluding `docs/archive/`, `docs/reviews/`, `docs/releases/2026-05-1[0-7]-*.md`, fenced code blocks, and yaml comment lines) MUST NOT contain a current-tense word-boundary reference to the pre-Phase-C module names `agent-platform` or `agent-runtime` (the latter negative-filtered against `agent-runtime-core`) outside an explicit historical marker (`historical`, `pre-ADR-NNNN`, `pre-Phase-C`, `consolidated into`, `merged into`, `was rooted`, `formerly`, `superseded`, `deprecated`, `archived`, `moved`, `extracted per ADR-NNNN`, `post-ADR-NNNN`) within ±3 lines. Closes rc8 post-corrective review P1-3: ARCHITECTURE.md #59, McpReplaySurfaceArchTest Javadoc, and rule-37.md still used deleted module names; Rule 87 only covered `architecture-status.yaml#allowed_claim` — Rule 94 widens the same discipline to the broader corpus.**
---

# Rule 94 — Active Corpus Deleted-Module Name Truth

## Motivation

Rule 87 (rc7) prevented stale `agent-platform` / `agent-runtime` claims in `architecture-status.yaml#allowed_claim` text. The rc8 post-corrective review (P1-3) found that **equivalent current-tense claims still appeared** in:

- `ARCHITECTURE.md` §4 #59 — ArchUnit enforcement prose listed `agent-platform/web/replay/`, `agent-platform/web/trace/`, `agent-platform/web/session/` paths.
- `agent-service/src/test/java/.../McpReplaySurfaceArchTest.java` Javadoc — said "The rule lives in agent-platform" and "agent-runtime hosts no HTTP endpoints".
- `docs/governance/rules/rule-37.md` — said "Scope is intentionally narrow to `agent-runtime`" with existing `agent-platform` references.

The actual tests still check the current package names, so this was not a runtime failure — it was a contract-truth failure: an active L0 constraint teaches the wrong module path, and the gate didn't cover that surface.

Rule 94 widens Rule 87's scope from one yaml field to the entire active corpus (`.md`, `.yaml`, `.java`), with the same historical-marker exemption pattern.

## Algorithm

For each candidate file (not under `docs/archive/`, `docs/reviews/`, `docs/releases/2026-05-1[0-7]-*.md`, not in target/.git):
1. Track fenced-code-block state (`^````).
2. Skip yaml comment lines (`^[[:space:]]*#`).
3. For each remaining line, test `\bagent-platform\b` OR (`\bagent-runtime\b` AND NOT `\bagent-runtime-core\b`).
4. On a match, look ±3 lines for any historical marker. If found, the match is exempt.
5. Otherwise, flag as a violation with file:line.

## Why this and not just expanding Rule 87

Rule 87 specifically targets the `allowed_claim:` field of `architecture-status.yaml` — a tightly-bounded vocabulary check. Rule 94 needs different ergonomics (multi-file, multi-language, fenced-block awareness, ±3-line marker window). Keeping them as separate rules makes each one auditable.

## Enforcement

Enforced by E129 (Gate Rule 94 — `active_corpus_deleted_module_name_truth`). Positive self-test: clean fixture passes. Negative self-test: a synthetic .md with `agent-platform/web/foo` on a line without a marker → FAIL; same with `pre-Phase-C` marker within ±3 lines → PASS.

## Activation

Activated 2026-05-19 by the v2.0.0-rc9 wave (rc8 post-corrective review response). Enforcer E129 + E130 (positive + negative self-test fixtures).

## Cross-references

- ADR-0078 — the Phase-C consolidation that deleted agent-platform and agent-runtime.
- ADR-0083 — rc9 corpus-truth + CI-acceptance authority record.
- Rule 87 — sibling: same family, narrower scope (`architecture-status.yaml#allowed_claim`).
- Rule 84 — same family family at the agent-*/ARCHITECTURE.md scope.
- Rule 86 — same family at the root ARCHITECTURE.md scope.
- `docs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` finding P1-3 — origin.
