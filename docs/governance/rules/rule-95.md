---
rule_id: 95
title: "SPI Catalog Exhaustiveness"
level: L0
view: logical
principle_ref: P-D
authority_refs: [ADR-0083, "rc8 post-corrective review P1-2"]
enforcer_refs: [E131, E132]
status: active
kernel_cap: 8
kernel: |
  **Every `public interface ...` declaration in a Java source file under any `*/spi/*` path (excluding `target/`) MUST appear in `docs/contracts/contract-catalog.md` either as an Active SPI table row OR be explicitly marked `(internal)`. Closes rc8 post-corrective review P1-2: `SkillCapacityRegistry` was a public extension point under a declared `.spi` package but absent from the catalog's "Active SPI interfaces (N total)" table; Rule 85 enforced "catalog rows must be backed by metadata" (one direction) but not "every public SPI must be cataloged" (the other direction).**
---

# Rule 95 — SPI Catalog Exhaustiveness

## Motivation

The rc8 post-corrective review (P1-2) found that `SkillCapacityRegistry` — a public interface under `ascend.springai.service.runtime.resilience.spi`, called out by ADR-0080 as the "registry SPI" and exposed by `ResilienceAutoConfiguration` as an `@ConditionalOnMissingBean` overrideable bean — was absent from `docs/contracts/contract-catalog.md` §2 "Active SPI interfaces (11 total)" table.

Rule 85 (rc6) enforced the other direction: every catalog row whose status doesn't say `(internal)` must have its `Module` and `Package` columns resolve to real `module-metadata.yaml#spi_packages` entries. That's "declared rows must be valid" — important, but one-directional. The opposite direction — "all public surfaces must be declared" — was never enforced.

Rule 95 closes the second edge. The catalog now has to know about every public SPI interface OR explicitly mark it internal.

## Algorithm

The gate scans all Java files under any path containing `/spi/`:
```
find . -type f -name '*.java' -path '*/spi/*' -not -path './target/*' -not -path './*/target/*'
```
For each, extract the first `public [sealed|non-sealed] interface <Name>` declaration. The interface name must appear inside a markdown backtick (`` `Iface` ``) somewhere in `docs/contracts/contract-catalog.md`. If absent → violation.

## Why backtick-match instead of structured table parsing

The catalog's Active SPI table has a stable shape, but the rule needs to also accept `(internal)` annotations that may live in prose adjacent to the table rather than in the table itself. A simple backtick-name match is robust to both forms; the prose decision (active SPI vs internal) is up to the architects.

## Enforcement

Enforced by E131 (Gate Rule 95 — `spi_catalog_exhaustiveness`). Positive self-test: every SPI interface present in catalog. Negative self-test: a synthetic public interface under `spi/` not in catalog → FAIL.

## Activation

Activated 2026-05-19 by the v2.0.0-rc9 wave (rc8 post-corrective review response). Enforcer E131 + E132 (positive + negative self-test fixtures).

## Cross-references

- ADR-0080 — calls out SkillCapacityRegistry as registry SPI.
- ADR-0081 — describes ResilienceContract.resolve consuming SkillCapacityRegistry.
- ADR-0083 — rc9 corpus-truth + CI-acceptance authority record.
- Rule 85 — sibling direction: catalog row → metadata.
- Rule 66 — SPI package exhaustiveness against module-metadata.
- `docs/reviews/2026-05-18-l0-rc8-post-corrective-architecture-review.en.md` finding P1-2 — origin.
