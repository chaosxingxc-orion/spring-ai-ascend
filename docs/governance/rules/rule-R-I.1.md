---
rule_id: R-I.1
title: "Edge↔Compute Ingress Routing"
level: L0
view: physical
principle_ref: P-I
authority_refs: [ADR-0049, ADR-0089, ADR-0094]
enforcer_refs: [E143]
status: design_only
scope_phase: design
kernel_cap: 6
scope_surfaces:
  - agent-client/src/main/java/**/*.java (Java import scan)
  - docs/contracts/ingress-envelope.v1.yaml
  - "**/module-metadata.yaml (deployment_plane == edge)"
kernel: |
  **Modules whose `deployment_plane` is `edge` MUST NOT import any production class under `ascend.springai.{service,engine,middleware}..` AND MUST NOT invoke compute_control HTTP routes directly; edge→compute_control traffic flows exclusively through `ascend.springai.bus.spi.ingress.IngressGateway` whose wire schema is `docs/contracts/ingress-envelope.v1.yaml`; W1 enforcement is ArchUnit (`EdgeToComputeDirectLinkArchTest`) + gate rule `edge_no_direct_compute_link`; contract status `design_only` at W1, promoted to `runtime_enforced` when the agent-client SDK lands per ADR-0049 / W3+.**
---

# Rule R-I.1 — Edge↔Compute Ingress Routing

Split out from Rule R-I.b in the rc17 wave (per ADR-0094) to separate the
**deployment-plane manifest** (Rule R-I, shipped, W1) from the
**ingress-routing protocol** (this rule, `design_only`, W3+ promotion).
Originally added in rc13 as Rule R-I.b per ADR-0089.

## Motivation

L0 motivation (LucioIT W1 §7.1): workloads with different characteristics
(latency-sensitive HTTP vs. throughput-sensitive ML training vs.
untrusted sandbox code) MUST NOT share infrastructure. Interference
between them produces the avalanche failure mode that costs production
AI platforms most uptime.

This rule operationalises the plane-boundary invariant for the edge ↔
compute_control surface specifically. Today (rc17) agent-client is
skeleton (0 Java files). Locking the positive topology contract NOW —
before any SDK code lands — prevents the future SDK from picking the
most-convenient HTTP path (direct to agent-service /v1/runs) by default.
The constraint composes with ADR-0088's relocation of S2cCallbackTransport
to agent-bus: after both ADRs, agent-bus owns the entirety of cross-plane
traffic in both directions.

## What the rule requires

**Enforcers**: E143 (ArchUnit `EdgeToComputeDirectLinkArchTest`) + gate
Rule 105 (`edge_no_direct_compute_link`).

For every module whose `deployment_plane` is `edge`:

1. No production source under `<module>/src/main/java/**/*.java` may
   `import` any class under `ascend.springai.service..`,
   `ascend.springai.engine..`, or `ascend.springai.middleware..`.
2. No production source may construct a `RestTemplate` or `WebClient`
   targeting a host other than the bus ingress endpoint.
3. The positive contract — what traffic IS allowed — is the
   `ascend.springai.bus.spi.ingress.IngressGateway` SPI, whose wire
   schema is `docs/contracts/ingress-envelope.v1.yaml`.

Contract status today is `design_only` (no runtime binding); W3+ SDK
landing promotes to `runtime_enforced` per ADR-0089's
`deferred_runtime_binding`.

## Why split from R-I

Rule R-I (kernel sub-clause .a, shipped W1) governs the **manifest**:
every reactor module's `module-metadata.yaml` declares
`deployment_plane: edge | compute_control | bus_state | sandbox | evolution | none`.
That invariant is fully active in W1 — every module has the field.

Rule R-I.1 (this rule, `design_only`/W3+) governs the **traffic shape**:
how edge modules talk to compute_control. The ArchUnit + gate
enforcement is active in W1 (E143 + Rule 105 prevent regression), but
the SPI it protects (`IngressGateway`) is still skeleton — there is no
agent-client production code today. Promotion to `runtime_enforced`
happens when the SDK ships.

Keeping the two invariants in one rule (the rc13 R-I.b structure) made
it hard to reason about "what's actually active in W1?" — the kernel
mixed a shipped manifest field with a deferred traffic protocol. Rule
R-I.1 isolates the deferred half so reviewers can assess shipped vs
deferred status cleanly.

## Deferred sub-clauses

R-I.1.c — IngressGateway Runtime Implementation (W3+ per ADR-0089)
R-I.1.d — Edge HTTP-Route Direct-Call Prohibition (W3+ per ADR-0089)
R-I.1.e — Bus Backpressure Mapping for Ingress (W2 per ADR-0050)

All three deferred sub-clauses are catalogued under their original Rule
R-I.c / .d / .e identifiers in `docs/CLAUDE-deferred.md`. The rc17 split
does not move them — only the active kernel obligation moved from R-I.b
to R-I.1.

## Activation history

- 2026-05-20 (rc13 per ADR-0089) — original Rule R-I.b activation.
- 2026-05-21 (rc17 per ADR-0094) — extracted from Rule R-I into a
  standalone rule. The manifest-vs-protocol invariants are structurally
  distinct; splitting clarifies W1 shipped surface vs W3+ deferred
  surface. Gate Rule 105 number is unchanged; ArchUnit class name is
  unchanged.

## Cross-references

- ADR-0049 — agent-client SDK roadmap (target W3+).
- ADR-0089 — Edge-Plane Ingress Gateway Mandate (origin of this rule's content).
- ADR-0094 — rc17 rule-consolidation authority (this split).
- Rule R-I — Five-Plane Manifest (sibling structural invariant; R-I.1
  covers the cross-plane traffic shape).
- Rule R-E — Three-Track Channel Isolation (the data channel carries the
  bus-side forward of an ingress envelope).
- Rule R-F — Cursor Flow Mandate (IngressResponse.cursor is the Task
  Cursor for RUN_CREATE).
- Rule R-L — Sandbox Permission Subsumption (the `sandbox` plane's
  physical enforcement boundary).
- Cross-plane symmetry: ADR-0088 relocates `S2cCallbackTransport` to
  `agent-bus.spi.s2c` — the S2C direction's analogue of this rule's C2S
  ingress invariant.
