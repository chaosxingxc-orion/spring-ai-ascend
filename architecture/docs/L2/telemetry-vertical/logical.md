---
level: L2
view: logical
status: scaffold
authority: "ADR-0061 (Telemetry Vertical Layer) §1–§2 + ADR-0062 (Trace/Run/Session identity) + ADR-0073 (Engine Hooks) + ADR-0157 (EngineeringFrame Ontology, EF-HOOK-SURFACE)"
relates_to: [ADR-0061, ADR-0062, ADR-0073, ADR-0157]
extends: [ADR-0061]
---

# Telemetry Vertical — L2 Logical View (component model)

This view binds the Telemetry Vertical declared at L0 §0.5.3 to component
altitude (C4 Component / arc42 L2). It names the entities, the carrier, and the
reference emission hooks that realise the vertical, and anchors them onto the
`EF-HOOK-SURFACE` EngineeringFrame so the structural axis
(`Module → EngineeringFrame → FunctionPoint`) is explicit. It is an interpretation
layer: it invents no IDs and no relationships beyond those authored upstream.

## 1. Structural placement

```text
agent-middleware (module, plane compute_control)
  └── EF-HOOK-SURFACE (EngineeringFrame; saa.owner agent-middleware; source ADR-0157)
        ├── HookDispatcher           # runtime dispatcher (package root, not .spi)
        ├── HookPoint                # enum, mirrors engine-hooks.v1.yaml
        ├── RuntimeMiddleware        # listener interface
        ├── HookContext              # per-fire carrier
        └── HookOutcome              # sealed: Proceed | Fail | ShortCircuit
```

The Telemetry Vertical is **not** a module and **not** a frame of its own. It is a
cross-cutting concern that emits through `EF-HOOK-SURFACE`: every LLM call, tool
invocation, state transition, and middleware adapter emission goes through the
Hook SPI (L0 §4 #16) or `TraceContext` (L0 §4 #53–#59); no layer emits telemetry
directly. The reference emission hooks are the FunctionPoints anchored on the
frame.

## 2. Entities (ADR-0061 §1)

Three OTel-native, Langfuse-compatible entities own the vertical's data model. The
field-level shapes are L2 detail; the canonical definition is ADR-0061 §1 and the
span-attribute table in `docs/telemetry/policy.md §4`.

| Entity | Langfuse analogue | Identity fields | Role |
|---|---|---|---|
| `Trace` | Trace | `trace_id` (16-byte hex), `tenant_id`, `session_id`, `started_at`, `posture` | root container; MAY span 1..N Runs (N:M with `session_id` per ADR-0062) |
| `Span` | Observation.SPAN | `span_id`, `trace_id`, `parent_span_id` (nullable for root), `status` (`OK | ERROR | UNSET`), `tenant_id` | generic nestable work unit |
| `LlmCall` | Observation.GENERATION | inherits `Span` + `gen_ai.system`, `gen_ai.request.model`, `gen_ai.usage.{input,output}_tokens`, `langfuse.cost_usd`, `langfuse.latency_ms` | specialised Span for an LLM invocation |

`Score` (Langfuse eval/feedback) is reserved but deferred to W3 (ADR-0061 §1); no
L1.x / W2 storage. Prompt / completion content is never inlined in
posture=research/prod — only `payload_ref://<id>` references (L0 §4 #58).

## 3. Carrier SPI (ADR-0061 §2)

| Type | Package | Role |
|---|---|---|
| `TraceContext` | `agent-bus` orchestration SPI (companion to `RunContext`) | pure-Java carrier: `traceId()`, `spanId()`, `sessionId()`, `newChildSpan(name)` |
| `NoopTraceContext` | default impl | propagates IDs without emission (L1.x posture before the OTel SDK lands at W2) |
| `RunContext` accessors | runtime carrier | `traceId()` / `spanId()` / `sessionId()` / `traceContext()` — mandatory L1.x accessors (L0 §4 #54, ADR-0062) |

`TraceContext` is the canonical in-runtime carrier; ThreadLocal reads remain
forbidden (Rule R-C.e). SPI purity is preserved — the accessors return plain
`String`s, not entity types (L0 §4 #7).

## 4. Reference emission hooks (FunctionPoints on EF-HOOK-SURFACE)

These are the W2 consumer implementations of `RuntimeMiddleware`, attached at
canonical `HookPoint` events. They are design-only today (the W2 Telemetry
Vertical populates them; see L1 `agent-middleware/ARCHITECTURE.md`). Each is a
FunctionPoint anchored on `EF-HOOK-SURFACE`.

| Hook (FunctionPoint) | Bound HookPoint(s) | Responsibility | Emits |
|---|---|---|---|
| `TokenCounterHook` | `BEFORE_LLM_INVOCATION` | token-budget accounting | counter increment |
| `PiiRedactionHook` | `BEFORE_LLM_INVOCATION`, `AFTER_MEMORY_WRITE` | strip raw PII before emission; mandatory in posture=research/prod (L0 §4 #58) | redacted span attributes |
| `CostAttributionHook` | `AFTER_LLM_INVOCATION` | per-tenant per-model cost | `langfuse.cost_usd` on `LlmCall` |
| `LlmSpanEmitterHook` | `AFTER_LLM_INVOCATION` | build the `GENERATION` span | `LlmCall` span (`gen_ai.*` + `langfuse.*`) |
| `ToolSpanEmitterHook` | `AFTER_TOOL_INVOCATION` | build the tool-call span | `Span` (`tool.name`, `mcp.tool.name`) |

`HookDispatcher.fire(point, context)` is the sole emission path; adapters never
emit telemetry directly (L0 §4 #56, ADR-0061 §7). `HookChain` ordering and the
fire sequence are detailed in [`process.md`](process.md).

## 5. What is L2 here vs structural at L0/L1

| Concern | Altitude | Home |
|---|---|---|
| "telemetry is a named cross-cutting vertical emitted via the Hook SPI" | L0 structural | L0 §0.5.3 + §4 #16 / #53–#59 |
| "`agent-middleware` owns the hook surface" | L1 structural | L1 `agent-middleware` + `EF-HOOK-SURFACE` |
| entity field schemas, `gen_ai.*` / `langfuse.*` attribute keys | **L2 detail** | this file + `docs/telemetry/policy.md §4` |
| emission sequence, MDC slice, dual-write / OTLP sink | **L2 detail** | [`process.md`](process.md) |
| span / log JSON field shapes | **L2 detail** | `docs/telemetry/policy.md §7` |

## Cross-references

- **L0 vertical**: [`../../L0/ARCHITECTURE.md`](../../L0/ARCHITECTURE.md) §0.5.3 + §4 #16, #53–#59.
- **L1 frame owner**: [`../../L1/agent-middleware/ARCHITECTURE.md`](../../L1/agent-middleware/ARCHITECTURE.md) §2 Hook surface.
- **EngineeringFrame**: `EF-HOOK-SURFACE` in [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl).
- **Contract surface**: `docs/contracts/engine-hooks.v1.yaml` (canonical `HookPoint` set).
- **Policy SSOT**: [`docs/telemetry/policy.md`](../../../../docs/telemetry/policy.md).
- **Process view**: [`process.md`](process.md).

## Authority

- ADR-0061 — Telemetry Vertical Layer (entities §1, carrier §2, hook co-shipping §7).
- ADR-0062 — Trace ↔ Run ↔ Session N:M identity.
- ADR-0073 — Engine Hooks + Runtime Middleware SPI.
- ADR-0157 — EngineeringFrame Ontology (`EF-HOOK-SURFACE`).
- ADR-0068 — Layered 4+1 + Architecture Graph (L2 altitude).
