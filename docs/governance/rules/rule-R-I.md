---
rule_id: R-I
title: "Five-Plane Manifest"
level: L0
view: physical
principle_ref: P-I
authority_refs: [ADR-0069, ADR-0094]
enforcer_refs: [E68]
status: active
product_claim: "PC-002"
scope_phase: design
kernel_cap: 8
kernel: |
  **Every `<module>/module-metadata.yaml` MUST declare `deployment_plane:` whose value is one of `edge | compute_control | bus_state | sandbox | evolution | none`. The plane assignment MUST match the L0 §7.1 topology — Edge Access (Agent Client SDK), Compute & Control (Runtime + Execution Engine), Bus & State Hub (Bus + Middleware persistence), Sandbox Execution (untrusted code), Evolution (Python ML). BoMs and build-time-only modules use `none`. Edge↔Compute ingress routing invariants split to Rule R-I.1 per ADR-0094.**
deferred_sub_clauses:
  - id: ".c"
    title: "IngressGateway Runtime Implementation [Deferred to W3+]"
    re_introduction_trigger: "first agent-client SDK release shipping production Java code under `agent-client/src/main/java/` (W3+ per ADR-0049)."
    deferred_body: |
      **Rule (draft)**: The bus-side implementation of `com.huawei.ascend.bus.spi.ingress.IngressGateway`
      MUST be wired to the agent-service `/v1/runs` HTTP routes via a bounded queue on the data
      channel (`bus-channels.yaml#channels[id=data]`). Implementation MUST honour the Task Cursor
      shape from Rule R-F: long-running requests (`request_type=RUN_CREATE`) return
      `IngressResponse.accepted(requestId, cursor)`; non-cursor request types return inline.
      The contract status in `docs/contracts/ingress-envelope.v1.yaml` MUST be promoted from
      `design_only` to `runtime_enforced` in the same wave.

      Composes with: Rule R-I sub-clause .a (Five-Plane Manifest); Rule R-E (Three-Track Channel
      Isolation — selects the data channel); Rule R-F (Cursor Flow Mandate); ADR-0089; ADR-0050.
    relates_to: ["ADR-0089", "ADR-0050", "ADR-0049", "Rule R-I sub-clause .a", "Rule R-E", "Rule R-F"]
  - id: ".d"
    title: "Edge HTTP-Route Direct-Call Prohibition [Deferred to W3+]"
    re_introduction_trigger: "same as R-I.c (first agent-client SDK release)."
    deferred_body: |
      **Rule (draft)**: Once the W3+ SDK ships, an integration test MUST assert that an
      agent-client request to a compute_control HTTP route (e.g., direct call to
      `agent-service /v1/runs`) fails by network-level rejection (e.g., service mesh policy,
      authn config) — not just by ArchUnit/static analysis. Until then, the W1 ArchUnit + gate
      guards (E143 + Rule 105) cover the import surface; HTTP-level enforcement is the W3+
      promotion gate.

      Composes with: Rule R-I sub-clause .b (W1 invariant this strengthens); ADR-0089;
      deployment-time mesh / load-balancer config.
    relates_to: ["ADR-0089", "Rule R-I sub-clause .b"]
  - id: ".e"
    title: "Bus Backpressure Mapping for Ingress [Deferred to W2]"
    re_introduction_trigger: "first non-stub `BackpressureSignal` SPI implementation lands in agent-bus (W2 per ADR-0050)."
    deferred_body: |
      **Rule (draft)**: The IngressGateway MUST surface ingress-channel backpressure as
      `IngressResponse.deferred(requestId)` (status DEFERRED) when the bus reports a non-zero
      admit-rate hold. Clients MUST treat DEFERRED as a retry-with-backoff hint, not a
      terminal failure. The retry policy itself is in scope for the SDK companion deferred
      sub-clause (out of scope for the bus implementation).

      Composes with: Rule R-K (Skill Capacity Matrix — the in-process analogue);
      ADR-0089; ADR-0050.
    relates_to: ["ADR-0089", "ADR-0050", "Rule R-K"]
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

## Deferred sub-clauses

The edge↔compute ingress runtime obligations (split to card R-I.1 per ADR-0094) remain deferred:
- Rule R-I sub-clause .c — IngressGateway runtime implementation (W3+, CLAUDE-deferred.md).
- Rule R-I sub-clause .d — edge HTTP-route direct-call prohibition (W3+, CLAUDE-deferred.md).
- Rule R-I sub-clause .e — bus backpressure mapping for ingress (W2, CLAUDE-deferred.md).

Rule G-3 sub-clause .d (`kernel_deferred_clause_coherence`) asserts the bidirectional link between this active rule and each deferred sub-clause.

## Cross-references

- Enforced by Gate Rule 49 (`deployment_plane_in_module_metadata`) for sub-clause .a.
- For the edge↔compute ingress routing invariant (was sub-clause .b pre-rc17), see [`rule-R-I.1.md`](rule-R-I.1.md) per ADR-0094.
- Architecture reference: ADR-0069 (Layer-0 ironclad rules), ADR-0089 (Edge-Plane Ingress Gateway Mandate).
- Companion rule: Rule R-C.1 ([`rule-R-C.1.md`](rule-R-C.1.md)) — Independent Module Evolution (was Rule R-C.b pre-rc17 per ADR-0094; module-metadata.yaml ownership).
- Companion rule: Rule R-E ([`rule-R-E.md`](rule-R-E.md)) — Three-Track Channel Isolation (the data channel carries the bus-side forward of an ingress envelope).
- Companion rule: Rule R-F ([`rule-R-F.md`](rule-R-F.md)) — Cursor Flow Mandate (IngressResponse.cursor is the Task Cursor for RUN_CREATE).
- Companion rule: Rule R-L ([`rule-R-L.md`](rule-R-L.md)) — Sandbox Permission Subsumption (the `sandbox` plane's physical enforcement boundary).
- Cross-plane symmetry: ADR-0088 relocates `S2cCallbackTransport` to `agent-bus.spi.s2c` — the S2C direction's analogue of this rule's C2S ingress invariant.
