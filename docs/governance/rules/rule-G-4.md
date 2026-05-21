---
rule_id: G-4
title: "Always-Loaded Context Budget"
level: L0
view: scenarios
principle_ref: P-B
authority_refs: []
enforcer_refs: [E100, E101]
status: active
scope_phase: always_on
kernel_cap: 8
kernel: |
  **The always-loaded session-context budget is enforced two ways: every file listed in `gate/always-loaded-budget.txt` MUST be at or below its declared byte ceiling, validated by `gate/measure_always_loaded_tokens.sh` (sub-clause .a; a ceiling of `0` means kept on disk but excluded from the budget). `docs/CLAUDE-deferred.md` MUST NOT be auto-injected into session context — no `@docs/CLAUDE-deferred.md` include directive in `CLAUDE.md`, no `ALWAYS` / `ALWAYS-LOAD` marker on its row in `docs/governance/SESSION-START-CONTEXT.md` (sub-clause .b; plain prose pointers fine, only auto-load mechanisms forbidden).**
---

# Rule G-4 — Always-Loaded Context Budget

Operationalises the AI-agent context-window constraint for the spring-ai-ascend corpus: the always-loaded set MUST fit in a small byte budget so that other context (codebase reads, tool I/O) has room.

## Sub-clauses

### .a — Always-Loaded Byte Budget (was Rule 70)

**Enforcer**: E100.

Every file listed in `gate/always-loaded-budget.txt` MUST be at or below its declared byte ceiling. `gate/measure_always_loaded_tokens.sh` walks the budget file and exits non-zero on any overage. A ceiling of `0` means the file is kept on disk but excluded from the always-loaded budget (used after a file has been demoted to on-demand).

### .b — Deferred Doc Not In Always-Loaded Set (was Rule 71)

**Enforcer**: E101.

`docs/CLAUDE-deferred.md` MUST NOT be auto-injected into the session context: no `@docs/CLAUDE-deferred.md` include directive in `CLAUDE.md`, and no `ALWAYS` / `ALWAYS-LOAD` marker on its row in `docs/governance/SESSION-START-CONTEXT.md`. Plain prose pointers ("see `docs/CLAUDE-deferred.md`") are fine — only the auto-load mechanisms are forbidden.
