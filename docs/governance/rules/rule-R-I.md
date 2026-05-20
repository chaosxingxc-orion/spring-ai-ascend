---
rule_id: R-I
title: "Five-Plane Manifest"
level: L0
view: physical
principle_ref: P-I
authority_refs: [ADR-0069, ADR-0094]
enforcer_refs: [E68]
status: active
kernel_cap: 8
kernel: |
  **Every `<module>/module-metadata.yaml` MUST declare `deployment_plane:` whose value is one of `edge | compute_control | bus_state | sandbox | evolution | none`. The plane assignment MUST match the L0 §7.1 topology — Edge Access (Agent Client SDK), Compute & Control (Runtime + Execution Engine), Bus & State Hub (Bus + Middleware persistence), Sandbox Execution (untrusted code), Evolution (Python ML). BoMs and build-time-only modules use `none`. Edge↔Compute ingress routing invariants split to Rule R-I.1 per ADR-0094.**
---

## Motivation

The L0 motivation (LucioIT W1 §7.1): workloads with different characteristics
(latency-sensitive HTTP vs. throughput-sensitive ML training vs. untrusted
sandbox code) MUST NOT share infrastructure. Interference between them produces
the avalanche failure mode that costs production AI platforms most uptime.

Sub-clause .b operationalises the plane-boundary invariant for the edge ↔
compute_control surface specifically. Today (rc13 — 2026-05-20) agent-client
is skeleton (0 java files). Locking the positive topology contract NOW —
before any SDK code lands — prevents the future SDK from picking the most-
convenient HTTP path (direct to agent-service /v1/runs) by default. The
constraint composes with ADR-0088's relocation of S2cCallbackTransport to
agent-bus: after both ADRs, agent-bus owns the entirety of cross-plane traffic
in both directions.

## Sub-clauses

### .a — Five-Plane Manifest (the original Rule R-I body)

**Enforcer**: E68 (`deployment_plane_in_module_metadata`).

Every reactor module's `module-metadata.yaml` declares
`deployment_plane: edge | compute_control | bus_state | sandbox | evolution | none`.
Gate-script schema check fails closed if missing.

### .b — Edge↔Compute Ingress Routing (added rc13, ADR-0089)

**Enforcers**: E143 (ArchUnit `EdgeToComputeDirectLinkArchTest`) + gate
Rule 105 (`edge_no_direct_compute_link`).

For every module whose `deployment_plane` is `edge`:
1. No production source under `<module>/src/main/java/**/*.java` may
   `import` any class under `ascend.springai.service..`,
   `ascend.springai.engine..`, or `ascend.springai.middleware..`.
2. No production source may construct a `RestTemplate` or `WebClient`
   targeting a host other than the bus ingress endpoint.
3. The positive contract — what traffic IS allowed — is the
   `ascend.springai.bus.spi.ingress.IngressGateway` SPI, whose wire schema
   is `docs/contracts/ingress-envelope.v1.yaml`.

Contract status today is `design_only` (no runtime binding); W3+ SDK landing
promotes to `runtime_enforced` per ADR-0089's `deferred_runtime_binding`.

Deferred sub-clauses .c–.z (W2+ runtime-binding follow-ups) are catalogued
in `docs/CLAUDE-deferred.md`.

## Cross-references

- Enforced by Gate Rule 49 (`deployment_plane_in_module_metadata`) for .a.
- Enforced by Gate Rule 105 (`edge_no_direct_compute_link`) for .b.
- Architecture reference: ADR-0069 (Layer-0 ironclad rules), ADR-0089 (Edge-Plane Ingress Gateway Mandate).
- Companion rule: Rule R-C.b ([`rule-R-C.md`](rule-R-C.md)) — Independent Module Evolution (module-metadata.yaml ownership).
- Companion rule: Rule R-E ([`rule-R-E.md`](rule-R-E.md)) — Three-Track Channel Isolation (the data channel carries the bus-side forward of an ingress envelope).
- Companion rule: Rule R-F ([`rule-R-F.md`](rule-R-F.md)) — Cursor Flow Mandate (IngressResponse.cursor is the Task Cursor for RUN_CREATE).
- Companion rule: Rule R-L ([`rule-R-L.md`](rule-R-L.md)) — Sandbox Permission Subsumption (the `sandbox` plane's physical enforcement boundary).
- Cross-plane symmetry: ADR-0088 relocates `S2cCallbackTransport` to `agent-bus.spi.s2c` — the S2C direction's analogue of this rule's C2S ingress invariant.
