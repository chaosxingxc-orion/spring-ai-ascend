---
level: L2
view: process
status: scaffold
authority: "ADR-0061 (Telemetry Vertical Layer) §3–§5 + ADR-0017 (trace_store dual-write / MCP replay) + ADR-0062 (Trace/Run/Session identity) + ADR-0063 (traceresponse correlation)"
relates_to: [ADR-0061, ADR-0017, ADR-0062, ADR-0063]
extends: [ADR-0061]
---

# Telemetry Vertical — L2 Process View (wire / MDC / sink)

This view carries the runtime emission detail that the layer-purity verdict
(Rule G-27) drains out of the L0 §0.5.3 prose: the on-wire format, the W3C
propagation handshake, the Logback MDC slice and its lifetime, and the hybrid
`trace_store` + OTLP dual-write sink sequence. It is the L2 (C4 Component / arc42
process) companion to [`logical.md`](logical.md). The operational SSOT remains
[`docs/telemetry/policy.md`](../../../../docs/telemetry/policy.md); this file binds
that policy to the emission sequence at component altitude.

All shapes below are L2 detail and are correctly sited here — the L2-detail-sink
helper (gate Rule 145 / E195) scans only L0 / L1 prose, never this directory.

## 1. Propagation handshake (HTTP edge — ADR-0061 §4)

`TraceExtractFilter` parses the W3C version-00 `traceparent` header on every
inbound request, ahead of the JWT / Tenant / Idempotency filters:

- **present and well-formed** → adopt the inbound `trace_id`, originate a fresh
  server `span_id` as a child of the inbound `parent_span_id`;
- **absent or malformed** → originate a fresh `trace_id` (16-byte random hex) +
  `span_id` (8-byte random hex); on malformed input, increment
  `springai_ascend_traceparent_invalid_total{posture}`.

On every outbound response (success or error), the filter emits
`traceresponse: 00-<trace_id>-<server_span_id>-01` so the W3 client SDK can
correlate (ADR-0063 §1). At L1.x the `X-Tenant-Id` header stays mandatory (L0 §4
#37); OTel Baggage (`tenant.id`, `session.id`, `user.id`) is W2 wire.

## 2. MDC slice and its lifetime

The Logback MDC carries a minimised set of correlation carriers, written by
distinct filters and cleared at request-scope exit:

| MDC key | Written by | Stage | Cleared |
|---|---|---|---|
| `tenant_id` | `TenantContextFilter` | after tenant resolution | request-scope exit |
| `trace_id` | `TraceExtractFilter` | HTTP edge, pre-JWT | request-scope exit (`finally`) |
| `span_id` | `TraceExtractFilter` | HTTP edge, pre-JWT | request-scope exit (`finally`) |
| `parent_span_id` | `TraceExtractFilter` | HTTP edge, pre-JWT | request-scope exit (`finally`) |
| `run_id` | `RunIdMdcFilter` (inline, once the Run is materialised) | after Run create | request-scope exit |

`session_id` is intentionally **not** in MDC at L1.x (minimise MDC carriers); it
is carried on the persisted Run row and propagated via OTel Baggage from W2. The
JSON log field shape (Logback JSON encoder) is the policy SSOT
(`docs/telemetry/policy.md §7`); the load-bearing MDC slice for debugging is
`tenant_id` + `trace_id` + `span_id` + `run_id`.

## 3. Emission sequence (hook-fired spans)

`HookDispatcher.fire(point, context)` is the sole emission path. The reference
emitter hooks (see [`logical.md`](logical.md) §4) build spans inside the dispatch
chain; adapters never emit telemetry directly (L0 §4 #56, ADR-0061 §7).

```text
engine reaches an LLM-call boundary
  │
  ├─ fire BEFORE_LLM_INVOCATION
  │     ├─ PiiRedactionHook   (research/prod: strip raw prompt → payload_ref://<id>)
  │     └─ TokenCounterHook   (budget accounting)
  │
  ├─ [ engine invokes the model ]
  │
  └─ fire AFTER_LLM_INVOCATION
        ├─ LlmSpanEmitterHook  (build GENERATION span: gen_ai.* + langfuse.*)
        └─ CostAttributionHook (langfuse.cost_usd on the LlmCall)
               │
               └─ Span handed to the hybrid sink (§4)
```

Tool-call boundaries follow the same shape via `AFTER_TOOL_INVOCATION` and
`ToolSpanEmitterHook`. Span attribute requirements per emission site are the
policy SSOT (`docs/telemetry/policy.md §4`): every span carries `tenant.id` (raw,
not bucketed — L0 §4 #57); raw prompt / completion / tool-input / tool-output
content is prohibited (L0 §4 #58).

## 4. Hybrid sink (wire format + dual-write — ADR-0061 §3, ADR-0017)

Every `Span` is written to two destinations through an outbox-style writer so the
MCP replay surface (W4) needs no external sink dependency:

1. **OTLP/HTTP exporter** — the wire format is `OTLP/HTTP` (no gRPC). Endpoint is
   `springai.telemetry.otlp.endpoint`. Compatible backends: Tempo, Jaeger,
   Langfuse, SigNoz, Honeycomb. Attribute namespaces on the wire are `gen_ai.*`
   (OTel semconv) + `langfuse.*` (platform-specific).
2. **`trace_store` Postgres** — outbox dual-write (ADR-0017 schema) so the W4 MCP
   replay tools (`get_run_trace`, `list_runs`, `get_llm_call`, `list_sessions`)
   serve traces without an external backend. This preserves the L0 §1 "no Admin
   UI" exclusion (the replay surface is MCP-only).

Sampling is posture-aware:

| Posture | OTLP exporter | `trace_store` dual-write | Sample rate |
|---|---|---|---|
| `dev` | stdout-OTLP (optional) | enabled | 100 % |
| `research` | OTLP/HTTP | enabled | 10 % head-based |
| `prod` | OTLP/HTTP fail-closed | enabled, tenant-scoped | 1 % head + tail-on-error (W4) |

Metric cardinality is reconciled separately: spans keep raw `tenant.id` (sampled
storage), metrics bucket `tenant_id` into `tenant_bucket` (per-emission
cardinality) — the two policies do not collide (L0 §4 #57,
`docs/telemetry/policy.md §2`).

## 5. Tenant isolation on the replay path (ADR-0061 §5)

The W4 MCP replay surface fails closed on tenant mismatch (`403`), application
-enforced like Langfuse `project_id`. `TraceExtractFilter` writes `tenant.id` into
OTel Baggage at the HTTP edge; the runtime reads tenant only via
`RunContext.tenantId()`. No raw PII reaches span attributes —
`PiiRedactionHook` is mandatory in posture=research/prod from W2 (L0 §4 #58).

## Cross-references

- **Logical view**: [`logical.md`](logical.md) (entities, carrier, hooks as FunctionPoints).
- **L0 vertical**: [`../../L0/ARCHITECTURE.md`](../../L0/ARCHITECTURE.md) §0.5.3 + §4 #53–#59.
- **Policy SSOT**: [`docs/telemetry/policy.md`](../../../../docs/telemetry/policy.md) §4 (span attributes), §5 (wire), §6 (propagation), §7 (log fields).
- **Replay schema**: ADR-0017 (`trace_store` + MCP replay tools).

## Authority

- ADR-0061 — Telemetry Vertical Layer (wire §3, propagation §4, tenant scoping §5).
- ADR-0017 — Dev-time trace replay (`trace_store` dual-write, MCP-only surface).
- ADR-0062 — Trace ↔ Run ↔ Session N:M identity.
- ADR-0063 — Client SDK observability contract (`traceresponse` correlation).
- ADR-0068 — Layered 4+1 + Architecture Graph (L2 altitude).
