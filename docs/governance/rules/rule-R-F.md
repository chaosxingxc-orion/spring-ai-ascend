---
rule_id: R-F
title: "Cursor Flow Mandate"
level: L1
view: logical
principle_ref: P-F
authority_refs: [ADR-0069, ADR-0070]
enforcer_refs: [E65, E72]
status: active
product_claim: "PC-003"
scope_phase: design
kernel_cap: 8
kernel: |
  **Every long-horizon Runtime API endpoint MUST return a Task Cursor immediately and MUST NOT hold the client connection while work executes. The contract surface (request → cursor → polled status / SSE / Webhook) MUST be declared in `docs/contracts/openapi-v1.yaml` for at least one runs operation; new long-running endpoints MUST follow the same shape.**
---

## Motivation

The L0 motivation (LucioIT W1 §6.1): synchronous long-poll dies under enterprise load — clients holding 10s+ HTTP connections exhaust threadpools client-side AND server-side. The Task Cursor + SSE/Webhook pattern eliminates client busy-waiting.

## Cross-references

- Enforced by Gate Rule R-M sub-clause .d (`cursor_flow_documented`) — checks `docs/contracts/openapi-v1.yaml` declares at least one 202-returning endpoint or an explicit `cursor:` schema.
- Architecture reference: ADR-0069 / LucioIT W1 §6.1.
- Integration-test enforcement activated in W1.x Phase 8 (`RunCursorFlowIT.createReturns202WithCursorWithin200ms`, enforcer E72, gate Rule 53 per ADR-0070); the original 36.b deferral closed.
- Companion rule: Rule R-H ([`rule-R-H.md`](rule-R-H.md)) — No Thread.sleep in Business Code (the suspend/wake path that cursor-flow surfaces depend on).
