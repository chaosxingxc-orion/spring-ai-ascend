---
rule_id: 93
title: "DFX Stem Matches Module"
level: L0
view: development
principle_ref: P-D
authority_refs: [ADR-0083, "rc8 post-corrective review P0-3"]
enforcer_refs: [E127, E128]
status: active
kernel_cap: 8
kernel: |
  **Every `docs/dfx/*.yaml` file (excluding `docs/archive/`) MUST have a basename stem matching a `<module>` entry in root `pom.xml`. Closes rc8 post-corrective review P0-3: `docs/dfx/agent-platform.yaml` remained on disk after ADR-0078 deleted the agent-platform module; ADR-0082 had mandated removal but the gate did not enforce orphan-detection.**
---

# Rule 93 — DFX Stem Matches Module

## Motivation

The rc8 post-corrective review (P0-3) found that `docs/dfx/agent-platform.yaml` was still on disk and declared `module: agent-platform` — but the agent-platform module was deleted by ADR-0078 (Phase-C consolidation). ADR-0082 had explicitly closed the deleted-module DFX family by mandating removal, yet the gate had no orphan-detection check.

A DFX file is not casual prose. Rule 78 uses DFX as part of SPI/package truth, and the file carries platform, security, observability, and artifact-coordinate claims for a module that no longer exists. Rule 93 closes the family by asserting that every active DFX file has a corresponding live module.

## Algorithm

The gate extracts `<module>` entries from root `pom.xml`:
```
grep -oE '<module>[^<]+</module>' pom.xml | sed -E 's|</?module>||g' | sort -u
```
and iterates `docs/dfx/*.yaml`. For each, the file basename minus `.yaml` is compared against the module set. Stems not matching any module are flagged as orphans with the suggested remediation (delete or archive).

## Why archive is allowed

Files under `docs/archive/` are explicitly outside the active corpus (this is the same convention Rules 84/86/87/94 use for the historical-marker exemption). A DFX file genuinely worth preserving for historical context can be moved to `docs/archive/<date>-dfx-<stem>-archived/<stem>.yaml`; it then escapes Rule 93 because it's no longer in `docs/dfx/`.

## Enforcement

Enforced by E127 (Gate Rule 93 — `dfx_stem_matches_module`). Positive self-test: all DFX stems match modules → PASS. Negative self-test: a synthetic DFX file with a non-existent module stem → FAIL.

## Activation

Activated 2026-05-19 by the v2.0.0-rc9 wave (rc8 post-corrective review response). Enforcer E127 + E128 (positive + negative self-test fixtures).

## Cross-references

- ADR-0078 — the Phase-C consolidation that deleted agent-platform and agent-runtime.
- ADR-0082 — the rc8 wave authority that mandated orphan DFX removal.
- ADR-0083 — rc9 corpus-truth + CI-acceptance authority record.
- Rule 78 — DFX-SPI-package parity (consumes the same dfx file set).
- `docs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` finding P0-3 — origin.
