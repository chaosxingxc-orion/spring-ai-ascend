---
rule_id: 75
title: "SPI Packages Populated"
level: L1
view: development
principle_ref: P-D
authority_refs: [ADR-0066, ADR-0067, ADR-0079]
enforcer_refs: [E108]
status: active
kernel_cap: 8
kernel: |
  **Every `<module>/module-metadata.yaml#spi_packages` entry MUST resolve to a real directory under `<module>/src/main/java/...` containing at least one `.java` file beyond `package-info.java`. Entries whose inline comment includes BOTH `placeholder` AND an `ADR-NNNN` reference are exempt (deferred SPI work waived by an ADR).**
---

# Rule 75 — SPI Packages Populated

## Motivation

The 2026-05-18 SPI integrity audit found that `agent-execution-engine/module-metadata.yaml` declared `ascend.springai.engine.spi` as an SPI package, but the physical directory contained only `package-info.java` — every real engine SPI class had landed in the parallel `service.runtime.orchestration.spi` namespace. The declaration was aspirational; reality differed. No gate rule caught this drift.

Rule 75 operationalises the implicit invariant of Rule 32 ("Every module MUST expose at least one SPI package containing ≥ 1 public interface"): declared SPI must be backed by code.

## Algorithm

For each `*/module-metadata.yaml`:
1. Parse the top-level `spi_packages:` list.
2. For each entry, skip if its inline comment includes BOTH `placeholder` AND `ADR-NNNN` (deferred SPI work, explicitly waived).
3. Otherwise, resolve `pkg.name.parts` → `<module>/src/main/java/pkg/name/parts/`.
4. Fail if the directory is missing.
5. Fail if the directory contains only `package-info.java` (no real SPI classes).

## Placeholder convention

A module's metadata may legitimately declare future SPI before the implementation lands. Mark each such line with `# placeholder; ... ADR-NNNN ...` so the rule treats it as deferred. Example:

```yaml
spi_packages:
  - ascend.springai.bus.spi      # placeholder; populated in W2 per ADR-0050
```

## Activation

Activated 2026-05-18 by the SPI metadata integrity wave per `D:\.claude\plans\spi-smooth-llama.md`. Surfaced during the wave: bus/client/evolve modules already had ADR-referenced placeholders; only agent-execution-engine had an unmarked empty SPI declaration (fixed by Track A of the same plan).
