---
date: 2026-06-11
status: awaiting owner decisions
scope: W3 kickoff — candidate inventory, local-feasibility split, open questions
predecessors:
  - 2026-06-11-llm-gateway-w2-proposal.md (approved; PR-1..PR-5 landed locally)
  - 2026-06-11-agent-service-serviceization-proposal.md (approved; Stages 0–2 + Stage-3 local subset landed)
---

# W3 kickoff assessment

## 1. Where W2 stands (local branch `feat/w1-run-kernel`)

Landed and verified on this branch (full reactor green, e2e example 29/29,
gate exit 0): Run kernel + DFA, JWT cross-check at both ingress edges,
idempotent `message/send`, LLM egress gateway (PR-1..PR-4) with the
`model.alias` SDK form and the example config flip (PR-5), serviceization
Stages 0–2 plus the Stage-3 local subset (session affinity, card masking,
self-registration client), MCP tool execution (stdio + HTTP/SSE), LangGraph
remote adapter, OTel GENERATION span bridge, quickstart/README refresh.

Still blocked (unchanged): Postgres tiers + gateway PR-6 Flyway (no Docker on
this dev host), Vault-sourced secrets, travel examples (openjiuwen 0.1.12 not
published), budget *enforcement* (owner scoped W2 to recording-only),
agent-bus Align-vs-Retire (owner decision at the W2 planning gate).

## 2. W3 candidate inventory (from L0 §0.5.3, §1, §1.1, status ledger)

| # | Item | Authority | Local feasibility | Notes |
|---|---|---|---|---|
| 1 | `springai-ascend-client` SDK (Java first) | §1.1 Audience B "W3 primary"; ledger `client_sdk_observability_contract` (design_accepted, W3 deferred) | **Fully local** | Server-side prerequisites (traceparent, MDC, traceresponse) already shipped. The retired `agent-client` module is NOT revived; this would be a new reactor module. The ledger names the contract: business spans local, W3C traceparent outbound, traceresponse correlation, OTLP/HTTP batching, PII redaction in posture=prod. |
| 2 | Score entity + cost dashboards | §0.5.3 W3 row | **Blocked-leaning** | Score persistence assumes `trace_store` Postgres (W2 dual-write item, Docker-blocked). An in-memory Score would be honest only as an SPI skeleton. |
| 3 | MCP tool protocol | §1 tech-stack table (W3) | Already landed early | `McpToolExecutor` (stdio + HTTP/SSE) shipped in agent-sdk; W4 holds the MCP *replay* tools. No W3 work left here. |
| 4 | Apache Tika document parsing | §1 tech-stack table (W3) | **Local, needs design** | No ingestion SPI exists anywhere; this is a fresh design question (where does document ingestion live — agent-sdk tool? runtime capability?). Not started by any ADR on disk. |
| 5 | GraalVM polyglot sandbox | §1 "W3-optional" (ADR-0018) | Local, optional | Explicitly optional; propose deferring unless a concrete agent needs it. |
| 6 | Audience C appliance surface | §1.1 "W3+ deferred" | Out of scope | Stays deferred by L0's own wording. |

## 3. Recommendation

**Lead W3 with item 1 (client SDK, Java first).** It is the declared Audience B
GA surface, it is fully local, its server-side halves are already on this
branch, and it is the only W3 item with a written contract in the ledger.
Items 2 (Score) and the Postgres halves join the same unblock train as the
W2 persistence backlog the moment Docker is available. Item 4 (Tika) needs a
design proposal of its own before any code; recommend sequencing it after the
client SDK's first cut. Item 5 stays deferred.

Proposed first slice for the SDK (one local batch, reviewable alone):
module `springai-ascend-client` (kind: sdk) with (a) an A2A client facade
over the platform's JSON-RPC + SSE surface (typed message/send +
message/stream, agent-card fetch), (b) outbound `traceparent` propagation +
`traceresponse` correlation per the shipped server behavior, (c) minted-token
+ JWT auth helpers matching both ingress edges, (d) Layer-1 tests against
stub servers plus an example e2e against the real runtime. OTLP batching and
PII redaction land as the second slice (they need an OTel SDK dependency
decision mirroring the gateway bridge's optional-dep pattern).

## 4. Open questions for the owner

1. **Un-defer the client SDK now?** The ledger marks it W3-deferred; starting
   it is a wave-boundary call the owner makes, not the gate.
2. **Module name/coordinates**: `springai-ascend-client` (ledger's name) as a
   new reactor module, or under an existing module? The retired `agent-client`
   name is not reused per L0 §2.
3. **Kotlin surface**: ledger says "Java/Kotlin first" — acceptable to ship
   Java-only in the first slice and treat Kotlin idioms as a follow-up?
4. **Tika ingestion**: in scope for v1.0 (2026-06-30) at all, or W3+?
