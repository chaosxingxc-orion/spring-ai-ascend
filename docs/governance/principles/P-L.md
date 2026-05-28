---
principle_id: P-L
title: "Sandbox Permission Subsumption"
level: L0
view: physical
authority: "Layer 0 governing principle (CLAUDE.md); LucioIT W1 L0 §6-§7"
enforced_by_rules: [R-L]   # formerly Rule 42 (numeric pre-ADR-0086)
product_claim: "PC-003"
kernel: |
  P-L — Sandbox Permission Subsumption.
  Logical authorizations issued by the bus MUST 1:1 map to physical sandbox
  restrictions (outbound IP whitelist, CPU cap, filesystem access).
  A logical grant cannot exceed what the physical sandbox enforces;
  otherwise the runtime authority is fictional.
  Enforced by Rule R-L.
scope_phase: design
---

## Motivation

This principle exists because **a logical authorization issued by the bus to a downstream node is a paper grant if the physical sandbox cannot enforce it** — the sandbox refuses the operation at runtime, but the failure mode is unpredictable from the logical model (different error path, different timing, no telemetry parity). Subsumption makes the logical-vs-physical mapping 1:1: every logical permission is bounded by what the sandbox can verify and limit (outbound IP whitelist, CPU cap in millicores, memory cap in megabytes, wall-clock cap in seconds, filesystem read/write scope). The runtime `SandboxExecutor` refuses any logical grant whose scope exceeds the declared physical limits, so authorization decisions in the bus correspond exactly to what the sandbox will actually permit.

## Operationalising rules

- Rule R-L — Sandbox Permission Subsumption ([`docs/governance/rules/rule-R-L.md`](../rules/rule-R-L.md))

## Cross-references

- ADR-0069 (origin of Rules 35–42 and the LucioIT W1 §7.4 sandbox subsumption doctrine)
- Policy source of truth: [`docs/governance/sandbox-policies.yaml`](../sandbox-policies.yaml) — `default_policy:` block with six required keys (outbound_network, filesystem_read, filesystem_write, cpu_cap_millicores, memory_cap_megabytes, wall_clock_cap_seconds)
- Deferred sub-clause 42.b — runtime enforcement of over-wide grant refusal (W2 trigger); legacy deferred-rule registry retired 2026-05-28, see [`retired-rules-audit.md`](../retired-rules-audit.md)
- Related: P-I (Five-Plane Topology) — Sandbox Execution plane is where physical limits live
