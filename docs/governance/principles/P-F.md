---
principle_id: P-F
title: "Cursor Flow & Asynchronous Client Boundary"
level: L0
view: logical
authority: "Layer 0 governing principle (CLAUDE.md); LucioIT W1 L0 §6-§7"
enforced_by_rules: [R-F]   # formerly Rule 36 (numeric pre-ADR-0086)
product_claim: "PC-003"
kernel: |
  P-F — Cursor Flow & Asynchronous Client Boundary.
  The Client → Runtime boundary is non-blocking by ironclad rule.
  Long-horizon task submissions return a Task Cursor immediately;
  clients consume process state via SSE and intermediate-result checkpoints
  via Webhook.
  No long-poll, no synchronous blocking.
  Enforced by Rule R-F.
scope_phase: design
---

## Motivation

This principle exists because **synchronous long-poll dies under enterprise load**: clients holding 10s+ HTTP connections exhaust threadpools on both sides, the load balancer cannot recycle connections, and a single slow Agent stalls the whole client fleet. The Task Cursor pattern returns control to the client within milliseconds with a polling/streaming handle, lets the server pick the right delivery channel per state (SSE for live process state, Webhook for intermediate-result checkpoints), and eliminates client busy-waiting. This is also what makes the Five-Plane topology (P-I) realizable — Edge Access stays lightweight because it never holds an open connection during Compute work.

## Operationalising rules

- Rule R-F — Cursor Flow Mandate ([`docs/governance/rules/rule-R-F.md`](../rules/rule-R-F.md))

## Cross-references

- ADR-0069 (origin of Rules 35–42 and the LucioIT W1 §6.1 cursor flow doctrine)
- Contract source of truth: [`docs/contracts/openapi-v1.yaml`](../../contracts/openapi-v1.yaml) — declares at least one 202-returning endpoint with cursor schema
- Rule R-F.b activated in W1.x Phase 8 (`RunCursorFlowIT.createReturns202WithCursorWithin200ms`, enforcer E72, gate Rule 53) per ADR-0070
- Related: P-E (Bus Channel Isolation) — cursor cancellation flows through `control` channel
- Related: P-G (Non-Blocking I/O) — non-blocking client boundary is meaningless if internal I/O blocks
