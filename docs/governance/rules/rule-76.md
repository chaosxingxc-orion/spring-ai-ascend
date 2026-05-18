---
rule_id: 76
title: "No Split SPI Packages"
level: L1
view: development
principle_ref: P-D
authority_refs: [ADR-0066, ADR-0079]
enforcer_refs: [E109]
status: active
kernel_cap: 8
kernel: |
  **A given Java SPI package MUST be declared by exactly one Maven module's `module-metadata.yaml#spi_packages`. Two modules co-declaring the same package is a Maven split-package and fails Rule 76 — Maven test classpath, IDE ownership, and JPMS cannot reason about split-package SPI cleanly.**
---

# Rule 76 — No Split SPI Packages

## Motivation

The 2026-05-18 SPI integrity audit found that `ascend.springai.service.runtime.orchestration.spi` was declared simultaneously by `agent-runtime-core/module-metadata.yaml` AND `agent-execution-engine/module-metadata.yaml`. Both modules physically contributed `.java` files to the same Java package — a Maven split-package.

Split-packages compile but degrade quietly: Maven test classpaths can collide, IDE refactor-rename traverses only one module, and JPMS (Java Platform Module System) refuses to start because two modules cannot both own a package.

Rule 76 forces an explicit owner per SPI package.

## Algorithm

1. Collect `(spi_package, module)` pairs from every `*/module-metadata.yaml`.
2. Group by `spi_package`. Fail if any group has more than one module.

## Resolution patterns

When two modules legitimately need to share a Java package, prefer ONE of:
- Promote the shared package to a third (upstream) module that both depend on.
- Split the package into non-overlapping sub-packages (e.g. `foo.spi.kernel` vs `foo.spi.adapter`) so each module owns its sub-package.
- Move the smaller contributor's classes to a sibling package the owning module does not claim.

The 2026-05-18 remediation chose option 3 for `orchestration.spi` / `engine.spi`: engine-adapter classes moved out of `orchestration.spi` into the (previously empty) `engine.spi` package.

## Activation

Activated 2026-05-18 by the SPI metadata integrity wave per `D:\.claude\plans\spi-smooth-llama.md`.
