---
rule_id: R-I
title: "Five-Plane Manifest"
level: L0
view: physical
principle_ref: P-I
authority_refs: [ADR-0069, ADR-0094]
enforcer_refs: [E68]
status: active
scope_phase: design
kernel_cap: 8
kernel: |
  **Every `<module>/module-metadata.yaml` MUST declare `deployment_plane:` whose value is one of `edge | compute_control | bus_state | sandbox | evolution | none`. The plane assignment MUST match the L0 §7.1 topology — Edge Access (Agent Client SDK), Compute & Control (Runtime + Execution Engine), Bus & State Hub (Bus + Middleware persistence), Sandbox Execution (untrusted code), Evolution (Python ML). BoMs and build-time-only modules use `none`. Edge↔Compute ingress routing invariants split to Rule R-I.1 per ADR-0094.**
---

## Motivation

The L0 motivation (LucioIT W1 §7.1): workloads with different characteristics
(latency-sensitive HTTP vs. throughput-sensitive ML training vs. untrusted
sandbox code) MUST NOT share infrastructure. Interference between them produces
the avalanche failure mode that costs production AI platforms most uptime.

This card scopes to sub-clause .a (the five-plane manifest invariant). The
edge↔compute ingress routing invariant that was sub-clause .b pre-rc17 was
split out to its own card [`rule-R-I.1.md`](rule-R-I.1.md) per ADR-0094.

## Sub-clause .a — Five-Plane Manifest

**Enforcer**: E68 (`deployment_plane_in_module_metadata`).

Every reactor module's `module-metadata.yaml` declares
`deployment_plane: edge | compute_control | bus_state | sandbox | evolution | none`.
Gate-script schema check fails closed if missing.

## Cross-references

- Enforced by Gate Rule 49 (`deployment_plane_in_module_metadata`) for sub-clause .a.
- For the edge↔compute ingress routing invariant (was sub-clause .b pre-rc17), see [`rule-R-I.1.md`](rule-R-I.1.md) per ADR-0094.
- Architecture reference: ADR-0069 (Layer-0 ironclad rules), ADR-0089 (Edge-Plane Ingress Gateway Mandate).
- Companion rule: Rule R-C.1 ([`rule-R-C.1.md`](rule-R-C.1.md)) — Independent Module Evolution (was Rule R-C.b pre-rc17 per ADR-0094; module-metadata.yaml ownership).
- Companion rule: Rule R-E ([`rule-R-E.md`](rule-R-E.md)) — Three-Track Channel Isolation (the data channel carries the bus-side forward of an ingress envelope).
- Companion rule: Rule R-F ([`rule-R-F.md`](rule-R-F.md)) — Cursor Flow Mandate (IngressResponse.cursor is the Task Cursor for RUN_CREATE).
- Companion rule: Rule R-L ([`rule-R-L.md`](rule-R-L.md)) — Sandbox Permission Subsumption (the `sandbox` plane's physical enforcement boundary).
- Cross-plane symmetry: ADR-0088 relocates `S2cCallbackTransport` to `agent-bus.spi.s2c` — the S2C direction's analogue of this rule's C2S ingress invariant.
