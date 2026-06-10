---
principle_id: P-A
title: "Business / Platform Decoupling + Developer Self-Service"
level: L0
view: development
authority: "Layer 0 governing principle (CLAUDE.md)"
enforced_by_rules: [R-A]   # formerly Rule 29 (numeric pre-ADR-0086)
kernel: |
  P-A — Business / Platform Decoupling + Developer Self-Service.
  Business code and Platform code are decoupled.
  Customization-by-source-patch into platform internals is forbidden.
  All architecture and solution design MUST be developer-friendly:
  configuration-driven extension, debug-friendly telemetry,
  and self-service closure (a developer can build, run, and test an agent
  end-to-end against the platform without platform-team intervention).
  Enforced by Rule R-A.
scope_phase: design
---

## Motivation

This principle exists because **platform internals patched in-place by business code** is the fastest way to destroy a platform's evolvability — every business team forks the kernel, the kernel cannot ship a security fix without breaking forks, and the platform team becomes a perpetual bottleneck. The counter-discipline is two-sided: **platform code must NOT contain business-specific customizations** (enforced via SPI + `@ConfigurationProperties` only), and the platform must ship a **runnable quickstart** so that **self-service closure** is a tested property, not a marketing claim. Without self-service closure, business developers escalate to the platform team for every problem, and the team turns into a help-desk instead of an architecture owner.

## Operationalising rules

- Rule R-A — Business/Platform Decoupling Enforcement ([`docs/governance/rules/rule-R-A.md`](../rules/rule-R-A.md))

## Cross-references

- ADR-0064 (origin of Rule R-A and the developer self-service mandate)
- Deferred sub-clause 29.c — quickstart smoke-run in CI (W1 trigger); legacy deferred-rule registry retired 2026-05-28, see [`retired-rules-audit.md`](../retired-rules-audit.md)
- Related: P-C (Independent Modules) — without module independence, decoupling is rhetorical
