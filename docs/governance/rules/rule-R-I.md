---
rule_id: R-I
title: "Five-Plane Manifest"
level: L0
view: physical
principle_ref: P-I
authority_refs: [ADR-0069]
enforcer_refs: [E68]
status: active
kernel_cap: 8
kernel: |
  **Every `<module>/module-metadata.yaml` MUST declare `deployment_plane:` whose value is one of `edge | compute_control | bus_state | sandbox | evolution | none`. The plane assignment MUST match the L0 §7.1 topology — Edge Access (Agent Client SDK), Compute & Control (Runtime + Execution Engine), Bus & State Hub (Bus + Middleware persistence), Sandbox Execution (untrusted code), Evolution (Python ML). BoMs and build-time-only modules use `none`.**
---

## Motivation

The L0 motivation (LucioIT W1 §7.1): workloads with different characteristics (latency-sensitive HTTP vs. throughput-sensitive ML training vs. untrusted sandbox code) MUST NOT share infrastructure. Interference between them produces the avalanche failure mode that costs production AI platforms most uptime.

## Cross-references

- Enforced by Gate Rule 49 (`deployment_plane_in_module_metadata`) — schema check on every module-metadata.yaml.
- Architecture reference: ADR-0069 / LucioIT W1 §7.1.
- Companion rule: Rule R-C.b ([`rule-R-C.md`](rule-R-C.md)) — Independent Module Evolution (module-metadata.yaml ownership).
- Companion rule: Rule R-L ([`rule-R-L.md`](rule-R-L.md)) — Sandbox Permission Subsumption (the `sandbox` plane's physical enforcement boundary).
