---
rule_id: D-9
title: "No Version / Log Metadata in Code"
level: L0
view: development
authority: "Layer 1 daily principle (CLAUDE.md)"
scope_surfaces:
  - "**/*.java"
  - "**/*.py"
  - "**/*.sh"
  - "**/*.bash"
  - "**/*.kt"
  - "**/*.ts"
  - "**/*.tsx"
  - "**/application*.yml"
  - "**/application*.yaml"
  - "Dockerfile"
  - ".github/workflows/*.yml"
kernel: |
  **Production code (Java, Python, shell scripts, YAML config) and inline comments MUST NOT carry version metadata: no `rc<N> Wave <M>` tags, no `per ADR-NNNN` pointers, no commit-SHA references, no `Finding F<N>` mentions, no "closes/addresses ticket #<N>" annotations, no changelog-style entries. Such metadata lives in commit messages, PR descriptions, ADRs (`docs/adr/`), release notes (`docs/logs/releases/`), rule cards (`docs/governance/rules/*.md`), `rule-history.md`, and the recurring-defect-families ledger. Implementation comments explain WHY the code exists when non-obvious, never WHICH wave introduced it. Structural rule citations (`# Rule 113 — slug`, `enforcer E160`) are STRUCTURAL identifiers, not metadata, and remain allowed everywhere. The following surfaces are EXEMPT (they exist precisely to carry version/wave metadata): `docs/adr/`, `docs/logs/`, `docs/governance/rules/*.md`, `docs/governance/principles/*.md`, `docs/governance/rule-history.md`, `docs/governance/recurring-defect-families.{yaml,md}`, `docs/governance/architecture-status.yaml` (allowed_claim + baseline_metrics comments), `docs/governance/enforcers.yaml`, `docs/governance/principle-coverage.yaml`, `docs/governance/architecture-graph.yaml`, `CHANGELOG.md`, the kernel paragraphs in `CLAUDE.md` itself, `gate/lib/` (helpers), and `gate/test_architecture_sync_gate.sh` (test fixtures construct synthetic version-tagged inputs).**
scope_phase: always_on
---

# Rule D-9 — No Version / Log Metadata in Code

## Motivation

Implementation code accumulates "rc18 Wave 1 fix:", "per ADR-0095",
"closes Finding F4", "Wave-5-corrective" comments as governance waves
ratchet through the corpus. Each annotation is true at write time and
becomes archaeological noise within 2-3 waves. The metadata duplicates
information already captured in commit messages, PR descriptions, ADRs,
release notes, and the rule-history ledger — and those surfaces are
authoritative; an inline comment is a stale copy.

Reading code 6 months later, the question is "what is this doing and
why does it need to exist", not "which wave introduced this line".
`git blame` answers the wave question already.

## What is forbidden

In production code (Java, Python, shell, YAML config, Dockerfile, CI
workflows):

- `# rc18 Wave 1: ...`
- `// per ADR-0095 ...`
- `# Closes Finding F4 (rc19 review)`
- `# Fixed in rc20 Wave 3`
- `// addresses #42`
- `# commit-sha b1cd29a`
- Changelog-style entries near function bodies (`# Added 2026-05-21: ...`)

## What is permitted

- Comments explaining a non-obvious WHY (`# strip BOM before hashing: BOM is not part of content`).
- Comments documenting an invariant or constraint (`# tenantId MUST be set before persist; enforced by validate()`).
- Cross-references to the canonical authority surface for the constraint (`# see Rule G-9 card`).
- Linking to a runbook for operational context (`# see docs/runbooks/multi-wave-release.md`).

## Exempt surfaces

These exist precisely to carry version/log metadata; the rule does not
apply:

- `docs/adr/**`
- `docs/logs/**`
- `docs/governance/rules/*.md` (rule cards)
- `docs/governance/rule-history.md`
- `docs/governance/recurring-defect-families.{yaml,md}`
- `docs/governance/architecture-status.yaml` (`allowed_claim` prose +
  `baseline_metrics` inline comments — they document wave-by-wave count
  evolution by design)
- `CHANGELOG.md` (if present)
- `CLAUDE.md` kernel paragraphs (they cite ADR numbers for navigation)

## Why D-9 lives at Layer 1

This is a daily-discipline rule (like D-2 simplicity, D-3 pre-commit).
It governs the AUTHOR's writing-time choices, not architecture. The
gate enforcement is grep-based and runs cheaply on every commit.

## Failure modes

- "I'll add the wave tag for traceability." → No. Traceability lives in
  `git log` + commit message. Inline tag duplicates and drifts.
- "But the ADR is the source of truth for this constraint." → Cite the
  ADR in the commit message and the rule card. Not inline.
- "The reviewer asked for it." → Push back; reviewer also reads
  `git log`. If the reviewer wants written history, that belongs in the
  release note, not the implementation.

## Related

- Rule D-2 (Simplicity & Surgical Changes) — D-9 is its prose corollary.
- Rule G-7 (Linux-First Dev Environment) — separate orthogonal rule about
  how code is INVOKED.
- `docs/governance/rule-history.md` — canonical surface for "which wave
  added this rule" questions.
