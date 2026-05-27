---
level: L1
view: development
module: agent-service
status: proposed
authority: "Absorbed from docs/logs/reviews/2026-05-26-agent-service-module-capability-feature-list.{cn,en}.md §6.1. Anchors back to the canonical Access Layer (Layer 1) in ../logical.md §1 + the ingress sub-section of ../physical.md §1."
---

# Access Layer — Feature Inventory (AS-L1-F01..F08)

> Module: Access Layer (Layer 1 per ADR-0138).
> Sovereign for: protocol convergence, tenant/auth/idempotency binding, client capability publication, cursor/cancel/resume/callback ingress.
> Does NOT own: Run aggregate state, Task control state, Session context state, engine dispatch, model/tool translation.

| Feature ID | Category | Covered clusters | Capability | Inputs / Outputs | Collaborators | Exception coverage | OSS reference |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AS-L1-F01 | Protocol ingress convergence | AS-SC01, AS-SC02, AS-SC05 | Receive HTTP / future gRPC / future A2A / future MQ ingress and converge external create / query / cancel / resume / callback requests into tenant-bound service requests. | Inputs: protocol payload, headers, identity. Outputs: normalized request, error envelope. | Session & Task Manager, Task-Centric Control Layer. | Unsupported protocol, schema invalid, protocol-state mismatch. | A2A Java Task API, Conductor REST/gRPC, LangGraph run/thread API. |
| AS-L1-F02 | SSE / streaming access | AS-SC03, AS-SC09 | Support SSE or equivalent streaming boundaries and emit token / step / RunEvent projections to clients without treating the connection as the Run lifecycle source of truth. | Inputs: runId, cursor, stream options. Outputs: stream event, event offset, disconnect signal. | Internal Event Queue, Session & Task Manager, Translation & Tool-Intercept. | SSE disconnect, slow consumer, event replay gap, stream backpressure. | A2A streaming, OpenAI Agents stream events, LangGraph streaming. |
| AS-L1-F03 | Non-SSE polling / query access | AS-SC02, AS-SC07, AS-SC09 | Provide cursor polling, Run / Task query, and terminal result retrieval for clients that cannot keep a streaming connection. | Inputs: taskId / runId / cursor, tenant. Outputs: status snapshot, terminal payload, retry-after hint. | Session & Task Manager, Task-Centric Control Layer. | Stale cursor, cross-tenant query, terminal result expired. | Conductor task query, Temporal query, A2A task get. |
| AS-L1-F04 | Direct-access boundary governance | AS-SC04, AS-SC21 | Allow clients to connect directly to Agent Service Access Layer or Mode B local deployment ingress; reject or isolate direct access to engine adapters, RunRepository, middleware, or agent-bus. | Inputs: client route, deployment mode, auth context. Outputs: accepted ingress or boundary error. | Physical deployment plane, Session & Task Manager, Engine Dispatch & Execution. | Direct-to-engine, missing tenant binding, idempotency bypass. | AgentScope Runtime endpoint, Spring AI A2A thin controller. |
| AS-L1-F05 | Tenant / auth / trace / idempotency binding | AS-SC01-AS-SC06, AS-SC23 | Bind JWT tenant claim cross-check, TenantContextFilter, IdempotencyHeaderFilter, TraceExtractFilter, and request hash before any state write. | Inputs: JWT, headers, body hash. Outputs: tenant-bound request context, idempotency decision, trace id. | Session & Task Manager. | Cross-tenant, idempotency conflict, body drift, missing trace. | Spring Security filter chain, Conductor idempotent task update. |
| AS-L1-F06 | Client capability and callback transport | AS-SC13, AS-SC14, AS-SC21 | Receive and publish client support for SSE, polling, webhook, local file, browser action, human approval, and client-hosted skills. | Inputs: client metadata, capability advertisement. Outputs: client profile, callback route, capability response. | Task-Centric Control Layer, Translation & Tool-Intercept. | Stale capability, callback unavailable, permission mismatch. | A2A AgentCard / push notification, OpenAI Agents tool approval. |
| AS-L1-F07 | Cancel / resume / callback ingress | AS-SC12-AS-SC14 | Provide ingress for cancel, resume, and client callback responses, converting all control requests into tenant-bound control service calls. | Inputs: runId, callbackId, resume payload, cancel actor. Outputs: resume accepted / rejected, cancel result. | Session & Task Manager, Task-Centric Control Layer, Internal Event Queue. | Resume re-auth failure, callbackId mismatch, same-terminal cancel, illegal transition. | Temporal signal, Conductor human task update, A2A input_required resume. |
| AS-L1-F08 | Agent / peer capability publication | AS-SC15, AS-SC18, AS-SC20 | Publish Agent / peer-visible capabilities, AgentCard, supported protocols, and delegation capabilities without weakening internal EngineRegistry strict matching. | Inputs: agent metadata, engine capability summary, adapter profile. Outputs: capability response, peer-facing metadata. | Engine Dispatch & Execution, Translation & Tool-Intercept. | Stale capability, peer unsupported feature, capability mismatch. | A2A AgentCard, AgentScope metadata, OpenAI Agents handoff metadata. |

## Cross-references

- **Canonical Layer 1 definition**: [`../logical.md`](../logical.md) §1 (5-layer diagram) + §10 (Configuration ownership matrix: Access Layer owns client identity + agent identity).
- **Scenario anchors**: [`../scenarios.md`](../scenarios.md) §0.1 (AS-SC* expanded inventory) and §1-§5 (S1-S5 canonical).
- **Process sequences**: [`../process.md`](../process.md) P1 (intake), P3 (cancel winner), P5 (S2C callback), P6 (cancel loser).
- **Physical deployment**: [`../physical.md`](../physical.md) §1 (Edge Access plane mapping).
- **SPI 4-way parity**: [`../spi-appendix.md`](../spi-appendix.md) — Access Layer produces no SPI; it consumes `RunRepository`, `TaskStateStore`, `ContextProjector`.

## Originating source

This file absorbs §6.1 of [`docs/logs/reviews/2026-05-26-agent-service-module-capability-feature-list.en.md`](../../../logs/reviews/2026-05-26-agent-service-module-capability-feature-list.en.md) (rc55 capability-feature scenario follow-up). The source review remains the discussion record; this file is the canonical Access Layer feature decomposition.
