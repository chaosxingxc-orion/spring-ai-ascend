---
principle_id: P-C
title: "Code-as-Everything, Rapid Evolution, Independent Modules"
level: L0
view: development
authority: "Layer 0 governing principle (CLAUDE.md)"
enforced_by_rules: [R-C.a, R-C.b]   # formerly Rules 28 + 31 (numeric pre-ADR-0086)
kernel: |
  P-C — Code-as-Everything, Rapid Evolution, Independent Modules.
  Every architectural constraint is code.
  Modules evolve independently — each builds, tests, and upgrades on its own,
  with high cohesion and low coupling.
  Production-environment upgrades are lightweight
  (BoM + starter pattern + semver compatibility).
  Enforced by Rule R-C.a + Rule R-C.1.
---

## Motivation

This principle exists because **architecture text without enforcer code rots within one quarter** — reviewers cannot remember every prose constraint, LLM agents cannot traverse a flat prose pile, and drift accumulates silently between releases. The counter-discipline is two-sided: (1) **every architectural constraint is code** (Rule R-C.a — Code-as-Contract: every active normative constraint must reach an ArchUnit test, gate-script rule, integration test, schema constraint, or compile-time check), and (2) **modules evolve independently** (Rule R-C.1 — each module owns a `module-metadata.yaml`, builds in isolation via `mvn -pl <module> -am test`, and ships under BoM + starter semver). Together they make the platform upgradeable in production by changing one BoM version rather than re-vendoring source.

## Operationalising rules

- Rule R-C.a — Code-as-Contract ([`docs/governance/rules/rule-R-C.md`](../rules/rule-R-C.md))
- Rule R-C.1 — Independent Module Evolution ([`docs/governance/rules/rule-R-C.md`](../rules/rule-R-C.md))

## Cross-references

- ADR-0059 (origin of Rule R-C.a and the enforcer-coverage cross-index)
- ADR-0066 (origin of Rule R-C.1 and the module-metadata schema)
- Deferred sub-clause 31.b — runtime semver compatibility enforcement (W2 trigger) — see [`docs/CLAUDE-deferred.md`](../../CLAUDE-deferred.md)
- Related: P-A (Business/Platform Decoupling) — depends on module independence to be meaningful
- Related: P-D (SPI + DFX + TCK Co-Design) — extends "constraint is code" into the contract surface
