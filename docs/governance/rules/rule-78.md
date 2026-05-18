---
rule_id: 78
title: "DFX SPI Packages Match Module Metadata"
level: L1
view: development
principle_ref: P-D
authority_refs: [ADR-0066, ADR-0067]
enforcer_refs: [E111]
status: active
kernel_cap: 8
kernel: |
  **For every module with `kind ∈ {platform, domain}`, `docs/dfx/<module>.yaml` MUST declare a top-level `spi_packages:` block whose entries are an order-insensitive set match with the non-placeholder entries of `module-metadata.yaml#spi_packages`. Mis-nested (under `observability:` or other sub-keys) or omitted dfx blocks fail.**
---

# Rule 78 — DFX SPI Packages Match Module Metadata

## Motivation

The 2026-05-18 SPI integrity audit found three drift patterns between `module-metadata.yaml` and the matching `docs/dfx/*.yaml`:

1. `docs/dfx/agent-runtime-core.yaml` had no `spi_packages` declaration at any level, even though metadata claimed three SPI packages.
2. `docs/dfx/agent-service.yaml` declared `spi_packages` nested under `observability:` rather than as a top-level peer.
3. `docs/dfx/agent-execution-engine.yaml` declared a single SPI package while metadata claimed two (one of which was empty — see Rule 75).

DFX is the design-time contract document; module-metadata is the build-time declaration. They MUST agree on which packages the module publishes.

## Algorithm

For each `*/module-metadata.yaml` whose kind is `platform` or `domain`:
1. Build the "real SPI" set from the metadata's `spi_packages:` MINUS placeholder entries (those with `# placeholder; ... ADR-NNNN ...` comment).
2. If the real-SPI set is empty, skip (the module is placeholder-only).
3. Build the same real-SPI set from `docs/dfx/<module>.yaml`'s top-level `spi_packages:`.
4. Fail if dfx is missing the top-level `spi_packages:` block OR the sets differ.

Order-insensitive comparison via `sort -u` on both sides.

## Why top-level only

DFX yamls have a 5-dimension structure (releasability/resilience/availability/vulnerability/observability). Nested `spi_packages:` under `observability:` (a real pre-rule pattern) is a structural error: it hides the SPI declaration from anyone scanning the file for module-level contracts.

## Activation

Activated 2026-05-18 by the SPI metadata integrity wave per `D:\.claude\plans\spi-smooth-llama.md`. Triggered fixes to all 4 affected DFX files (agent-runtime-core, agent-execution-engine, agent-service, agent-middleware).
