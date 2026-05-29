---
rule_id: G-24
title: "Old Orchestration-SPI Package Ban"
level: L1
view: development
principle_ref: P-C
authority_refs: [ADR-0158]
enforcer_refs: [E189]
status: active
scope_phase: design
kernel_cap: 8
governance_infra: true
scope_surfaces:
  - docs/governance/templates
  - architecture/docs
  - docs/dfx
  - docs/quickstart.md
  - docs/cross-cutting/oss-bill-of-materials.md
  - docs/governance/enforcers.yaml
  - docs/governance/architecture-status.yaml
  - docs/contracts/contract-catalog.md
  - architecture/features
  - gate/lib/check_old_orchestration_spi_package.py
kernel: |
  Active authority surfaces MUST NOT name `engine.orchestration.spi` / `engine/orchestration/spi` as the CURRENT home; ADR-0158 re-homed the neutral orchestration/engine SPI to `com.huawei.ascend.bus.spi.engine`. Past-tense re-home / dissolution prose carrying a historical marker is legitimate and skipped. Scope: templates `.j2`, rendered `architecture/docs`, `docs/dfx`, quickstart, oss-bom, `enforcers.yaml`, `architecture-status.yaml`, `contract-catalog.md`, `architecture/features`. Excluded: `docs/logs`, `docs/adr`, `rule-history.md`, `docs/archive`, `scripts`, and generated artefacts.
---

# Rule G-24 — Old Orchestration-SPI Package Ban

## What

ADR-0158 moved the neutral orchestration/engine SPI out of
`com.huawei.ascend.engine.orchestration.spi` and into
`com.huawei.ascend.bus.spi.engine`. This rule bans the old package name from
appearing as the current home on any active authority surface.

## Why

Closes the 2026-05-29 review finding F6.3. A stale package name on a current
authority surface is an instruction that points an AI agent at a package that no
longer exists. History (the re-home / dissolution narrative) is legitimate and
must remain; only present-tense current-home claims are banned.

## How it works

`gate/lib/check_old_orchestration_spi_package.py` scans the in-scope surfaces
for the old package in dotted or path form, then skips any line carrying a
historical marker (`re-homed`, `relocated`, `transient`, `dissolved`,
`co-located`, `ADR-0088`, `ADR-0158`, `previously`, `former`, ...). The
ADR-0158 statement that declares the NEW home carries such a marker and is
therefore allowed. Findings are reported as line numbers only.

## Render-order note

The rendered `architecture/docs/**.md` are re-rendered from the fixed `.j2`
sources in a later wave. Until that render runs, the rendered Markdown still
names the old package as current, so the live-tree pass of this rule is
validated post-render. The `.j2` sources and the non-templated inline surfaces
are already clean.

## Test fixtures

  - INVALID: a synthetic active template line naming the old package as current
             fails.
  - VALID  : a clean synthetic corpus passes.

## Cross-references

  - ADR-0158 — transport-agnostic EnginePort boundary
