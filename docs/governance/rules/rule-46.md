---
rule_id: 46
title: "S2C Callback Envelope + Lifecycle Bound"
level: L1
view: process
principle_ref: P-M
authority_refs: [ADR-0074]
enforcer_refs: [E81, E82, E83, E89, E90, E92]
status: active
kernel_cap: 8
kernel: |
  **Server-to-Client capability invocation MUST go through `S2cCallbackEnvelope` + `S2cCallbackTransport` SPI (both under `ascend.springai.service.runtime.s2c.spi` after the v2.0.0-rc3 package move per cross-constraint audit α-4 / β-2). The waiting Run MUST suspend via `SuspendSignal.forClientCallback(...)` — a checked-suspension variant introduced in v2.0.0-rc3 per cross-constraint audit α-2 / β-5 to preserve ADR-0019's compile-time-visible-suspension doctrine; the prior parallel unchecked `S2cCallbackSignal` was deleted. The orchestrator MUST mark the parent Run SUSPENDED with `SuspendReason.AwaitClientCallback`. An `s2c.client.callback` skill capacity row MUST be declared in `docs/governance/skill-capacity.yaml`; runtime admission against that row (`ResilienceContract.resolve(tenant, "s2c.client.callback")`) is deferred to Rule 46.b (W2). Client responses MUST be validated against `docs/contracts/s2c-callback.v1.yaml` (callback_id match, outcome enum membership) BEFORE resume; invalid response transitions Run to FAILED with reason `s2c_response_invalid`. Non-blocking lifecycle for the W2.x synchronous bridge is deferred to Rule 46.c (W2 async orchestrator).**
---

## Motivation

Authority: ADR-0074 / P-M. Part of the W2.x Engine Contract Structural Wave. Server-to-client capability invocation is an explicit asynchronous protocol bound to the suspend/resume loop — the platform's "server-sovereign" boundary requires that S2C callbacks travel through a typed envelope and that the waiting Run participates in the same SUSPENDED/RUNNING state machine as every other long-horizon wait. The v2.0.0-rc3 cross-constraint audit unified the original S2cCallbackSignal into SuspendSignal as a checked-suspension variant so that ADR-0019's compile-time-visible-suspension doctrine remains a single source of truth.

## Details

### Envelope-propagation matrix

The Phase 3a cross-rule co-design audit matrix in [`docs/reviews/2026-05-16-engine-contract-structural-response.en.md`](../../reviews/2026-05-16-engine-contract-structural-response.en.md) §5 is the canonical reference for how this rule interlocks with Rules 20/35/38/41/42 — six mandatory request fields (callback_id, server_run_id, capability_ref, request_payload, trace_id, idempotency_key) propagate at every layer to prevent the Class-3 envelope-propagation gap (fourteenth-cycle SpawnEnvelope 11-dim precedent).

## Cross-references

- Enforced by Gate Rule 58 (`s2c_callback_yaml_present_and_wellformed`, enforcer E81), integration test E82 (`S2cCallbackRoundTripIT`), ArchUnit E83 (`S2cCallbackRespectsRule38Test` — no Thread.sleep in s2c..).
- Additional enforcers E89, E90, E92 cover post-release closure work (v2.0.0-rc1 → rc3) including SuspendSignal sealed-checked-variant unification and the s2c.spi package move.
- Runtime ResilienceContract integration for `s2c.client.callback` skill capacity deferred to Rule 46.b (W2) per ADR-0074 §Consequences and `docs/CLAUDE-deferred.md` 46.b.
- Deferred sub-clauses (canonical names — aligned with `docs/CLAUDE-deferred.md` 2026-05-19 rc9 wave): 46.b (`ResilienceContract s2c.client.callback` runtime admission wiring — W2), 46.c (non-blocking lifecycle for the W2.x synchronous bridge — W2 async orchestrator). Invalid-response handling itself is **already shipped at L1.x** through the kernel's `BEFORE-resume` validation clause and is enforced by `S2cCallbackEnvelopeValidationTest` (enforcer E89) — it is not a deferred sub-clause.
- Schema source: `docs/contracts/s2c-callback.v1.yaml`.
- Inter-rule cross-citations: Rule 20 (`SuspendReason.AwaitClientCallback` as a legal RUNNING → SUSPENDED transition), Rule 35 (S2C traffic rides the `data` channel by default with control intents on `control`), Rule 38 (no Thread.sleep — checked-suspension variant), Rule 41 (`s2c.client.callback` skill capacity), Rule 42 (logical-to-physical authority discipline at the callback boundary).
