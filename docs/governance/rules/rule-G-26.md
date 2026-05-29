---
rule_id: G-26
title: "Local Plan-Path Ban"
level: L1
view: scenarios
principle_ref: P-C
authority_refs: [ADR-0156]
enforcer_refs: [E191]
status: active
scope_phase: design
kernel_cap: 8
governance_infra: true
scope_surfaces:
  - product
  - docs/adr
  - docs/governance
  - architecture
  - CLAUDE.md
  - AGENTS.md
  - gate/local-plan-path-exemptions.txt
kernel: |
  Active authority MUST NOT reference local agent-host plan paths of the form `D:\.claude\plans` or `D:/.claude/plans` (BOTH separators). Scope: `product/`, `docs/adr/`, `docs/governance/` (excluding `rule-history.md`), `architecture/`, `CLAUDE.md`, `AGENTS.md`. The only permitted surfaces are listed in `gate/local-plan-path-exemptions.txt` (Linux-first workflow docs + rule history + gate helpers/fixtures that construct synthetic inputs).
---

# Rule G-26 — Local Plan-Path Ban

## What

Bans references to a local agent-host plan directory
(`D:\.claude\plans` / `D:/.claude/plans`) from active authority surfaces. The
pattern matches both Windows and POSIX path separators so the ban cannot be
sidestepped by switching slashes.

## Why

Closes the 2026-05-29 review finding P1-4. A committed reference to a
machine-local plan path is dead on any other host and leaks the authoring
agent's private workspace layout into shared authority. Plans live outside the
repo; the corpus must not point at them.

## How it works

The gate greps the in-scope trees for the dual-separator pattern, then drops any
hit whose repo-relative path matches an entry (exact file or directory prefix)
in `gate/local-plan-path-exemptions.txt`. Remaining hits fail closed.

## Exemptions

`gate/local-plan-path-exemptions.txt` lists the surfaces whose job is to
document the local / Linux-first workflow or to record history:
`rule-G-7.md`, `dev-environment.md`, `rule-history.md`, `gate/lib/`, and
`gate/test_architecture_sync_gate.sh`.

## Test fixtures

  - INVALID: a synthetic product file with the backslash form fails.
  - INVALID: a synthetic ADR with the forward-slash form fails (proves both
             separators are caught).
  - VALID  : an exempted reference passes.

## Cross-references

  - Rule G-7 — Linux-First Dev Environment (legitimately documents the local path)
