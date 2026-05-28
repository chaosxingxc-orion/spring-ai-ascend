---
rule_id: G-10
title: "Parallel-Linux-Scripts Mandate"
level: L0
view: process
principle_ref: P-B
authority_refs: ["ADR-0098"]
enforcer_refs: [E164]
status: active
governance_infra: true
scope_phase: always_on
kernel_cap: 8
scope_surfaces:
  - "gate/**/*.sh"
kernel: |
  **Every new gate check or long-running validation under `gate/**/*.sh` (added on or after ADR-0098 / rc21) MUST be invokable through a parallel-execution path on Linux/WSL — via `xargs -P`, GNU `parallel`, or background jobs with explicit `wait`. The canonical runner is `gate/check_parallel.sh`; `gate/check_architecture_sync.sh` serial execution is debug-only. Rule G-5.a already enforces serial↔parallel slug parity; G-10 extends the discipline to "new scripts are parallel-ready from day one". Exemptions: helper libraries under `gate/lib/` and one-shot bootstrap scripts (listed in `gate/serial-only-paths.txt`).**
---

## Motivation

rc18 + rc20 hardening waves showed: every reviewer-response cycle runs
the full gate ~3–5 times (initial check → fix → re-check → corrective
→ final). Serial gate on Git Bash for Windows takes 20+ minutes per
run; parallel gate on Linux/WSL takes 3 minutes. The 6–20× speedup
documented under Rule G-7 only materialises if NEW gate scripts are
parallel-compatible from authoring time. Retrofitting parallelism
after the fact is expensive — Rule G-5.a (serial↔parallel slug parity)
already caught the legacy debt; G-10 prevents the new debt.

## What is forbidden

In any newly-authored shell script under `gate/**/*.sh`:

- A `for` / `while` loop over a long list with no batching mechanism
  that the parallel wrapper can split.
- Side-effecting state that depends on serial execution order (e.g.
  writing to a shared file without `flock` or per-batch tempfiles).
- Embedded `sleep 30s` style waits that block other batches.

## What is permitted

- Single-rule scripts that operate on bounded input (one ADR, one
  module, one file) and finish in &lt;5s — these run inside one batch
  worker and the parallel wrapper handles distribution.
- Helper libraries under `gate/lib/*.sh` — they are leaves called by
  the parallel runner; they don't need to BE parallel.
- Bootstrap scripts (`gate/lib/update_benchmark_baseline.sh`,
  one-shot migration utilities) — listed in
  `gate/serial-only-paths.txt`.

## Exemption mechanism

`gate/serial-only-paths.txt` enumerates the paths that pass G-10
vacuously. Helper-only files (`gate/lib/`) and known one-shot
bootstrap utilities go in this list.

## Cross-references

- Rule G-5.a — serial↔parallel slug parity (existing).
- Rule G-7 — Linux/WSL is the verification env.
- ADR-0098 — rc21 6-phase scenario-loaded contracts, which include
  this new rule as part of the always-on contract.
- Enforcer E164 — `gate/rules/rule-116.sh` scans for non-parallel
  new scripts.
