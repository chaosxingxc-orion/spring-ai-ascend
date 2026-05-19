---
rule_id: D-6
title: "Posture-Aware Defaults"
level: L1
view: process
principle_ref: P-A
authority_refs: []
enforcer_refs: []
status: active
kernel_cap: 12
kernel: |
  **Every config knob, fallback path, and persistence backend declares its default behaviour under three postures: `dev` / `research` / `prod`.**
---

## Motivation

A config knob with a single hardcoded default hides its production behaviour behind a value chosen for developer convenience. Postures make the contract explicit: `dev` is permissive (warnings only, in-memory backends allowed), `research`/`prod` are fail-closed (required config present, durable persistence, fallbacks emit metrics). The posture is read once at startup from `APP_POSTURE` and never hardcoded at call sites — every site that branches on posture imports a single source of truth.

## Details

- `dev`: permissive — warnings only, in-memory backends allowed.
- `research` / `prod`: fail-closed — required config present, durable persistence, fallbacks emit metrics.

Posture set by `APP_POSTURE` env var (default `dev`). Read once at startup; never hard-code at call sites. Per-module posture coverage matrix: [`docs/governance/posture-coverage.md`](../posture-coverage.md). Tests MUST cover `dev` and `research` paths for any new contract.

## Cross-references

- Rule D-4 (Three-Layer Testing, With Honest Assertions) — tests covering `dev` and `research` paths is a Rule D-6 obligation enforced by Rule D-4's coverage discipline.
- [`docs/governance/posture-coverage.md`](../posture-coverage.md) — per-module posture coverage matrix.
