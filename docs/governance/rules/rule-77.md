---
rule_id: 77
title: "SPI Packages Dot-Spi Convention"
level: L1
view: development
principle_ref: P-D
authority_refs: [ADR-0066, ADR-0067]
enforcer_refs: [E110]
status: active
kernel_cap: 8
kernel: |
  **Every `spi_packages` entry MUST end in `.spi` OR contain `.spi.` (sub-packages). Operationalises Rule 32's `*.spi.*` literal convention — domain packages without a `.spi` token MUST NOT be declared as SPI.**
---

# Rule 77 — SPI Packages Dot-Spi Convention

## Motivation

The 2026-05-18 SPI integrity audit found that `agent-runtime-core/module-metadata.yaml` declared `ascend.springai.service.runtime.runs` as an SPI package — but the directory contained domain value types (`Run`, `RunMode`, `RunStatus`, `RunStateMachine`) plus one contract (`RunRepository`). Only the latter is truly SPI-grade; the rest are domain types.

Rule 32 specifies the `*.spi.*` literal convention. Without machine enforcement, that convention drifts: developers conflate "interesting package" with "SPI package", and the SPI surface bloats to include non-contracts.

Rule 77 forces every declared SPI package to actually contain `.spi` in its name.

## Algorithm

For each `spi_packages:` entry, fail unless the package ends in `.spi` OR contains `.spi.` (sub-packages of a `.spi.*` namespace).

## Examples

- `ascend.springai.engine.spi` — passes (ends in `.spi`).
- `ascend.springai.service.runtime.runs.spi` — passes (ends in `.spi`).
- `ascend.springai.engine.spi.kernel` — passes (contains `.spi.`).
- `ascend.springai.service.runtime.runs` — fails (no `.spi` token).
- `ascend.springai.engine.SPI` (uppercase) — fails (Java packages are lowercase by convention).

## Activation

Activated 2026-05-18 by the SPI metadata integrity wave per `D:\.claude\plans\spi-smooth-llama.md`. Drove the `runs` → `runs/spi/` sub-package move documented in the same wave.
