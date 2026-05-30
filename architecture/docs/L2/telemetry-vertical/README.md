---
level: L2
view: scenarios
status: scaffold
authority: "ADR-0061 (Telemetry Vertical Layer) + ADR-0062 (Trace/Run/Session identity) + ADR-0063 (Client SDK observability) + ADR-0017 (dev-time trace replay) + ADR-0073 (Engine Hooks) + ADR-0157 (EngineeringFrame Ontology)"
relates_to: [ADR-0061, ADR-0062, ADR-0063, ADR-0017, ADR-0073, ADR-0157]
---

# L2 — Telemetry Vertical (wire / MDC / sink detail home)

This directory is the **L2 detail sink** for the Telemetry Vertical. It is the
home into which the runtime implementation detail that does **not** belong at L0
or L1 — on-wire formats, MDC field shapes, span/log schemas, the dual-write sink
sequence, sampling postures — is pulled down to component (C4 Component / arc42
L2) altitude.

It exists because the adjudicated layer-purity verdict (kernel Rule G-27 /
enforcer E195, gate Rule 145) requires an L0 / L1 authority surface to stay a
**structural boundary** document. Wire formats (`OTLP/HTTP`, `gen_ai.*`,
`langfuse.*`, `traceparent`), MDC slices (`tenant_id` / `trace_id` / `span_id` /
`run_id`), and the `trace_store` dual-write sink behaviour are L2 detail. The
single-source-of-truth for telemetry *policy* remains
[`docs/telemetry/policy.md`](../../../../docs/telemetry/policy.md); this L2 home is
the **architecture-side** companion that binds that policy to the structural axis
(`Module → EngineeringFrame → FunctionPoint`) and to the L0 §0.5.3 vertical.

## Scope

Per Rule 33 / ADR-0068, an L2 document targets one feature or use case (not a
module, not the whole system) and binds specific implementation classes,
packages, sequences, and physical placements to the L0 / L1 contracts above.
This vertical's L2 home covers:

- the **component model** of the Telemetry Vertical (entities `Trace` / `Span` /
  `LlmCall`, carrier `TraceContext`, the reference emission hooks) and how it
  anchors to the `EF-HOOK-SURFACE` EngineeringFrame in `agent-middleware`;
- the **emission process** — the hook-fire → span-build → MDC-populate →
  dual-write / OTLP-export sequence;
- the **wire and log shapes** — OTLP/HTTP, the `gen_ai.*` / `langfuse.*`
  attribute namespaces, the W3C `traceparent` / `traceresponse` propagation
  contract, and the Logback MDC + JSON log field set.

It does **not** restate product claims, module ownership, or ADR decisions — it
*interprets* them at component altitude. The authority cascade is unchanged:
generated facts > DSL > Card / prose (this directory is prose).

## Structural anchor

| Axis | Anchor |
|---|---|
| Structure | `agent-middleware` → `EF-HOOK-SURFACE` (frame `saa.id`, authored in [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl), owner `agent-middleware`, source ADR-0157) |
| FunctionPoints | the reference emission hooks `TokenCounterHook`, `PiiRedactionHook`, `CostAttributionHook`, `LlmSpanEmitterHook`, `ToolSpanEmitterHook` (W2 consumer impls; design-only today) anchored on `EF-HOOK-SURFACE` |
| Contract surface | `docs/contracts/engine-hooks.v1.yaml` (the canonical `HookPoint` surface; gate Rule 57 enforces yaml ↔ enum consistency) |
| L0 vertical | [`../../L0/ARCHITECTURE.md`](../../L0/ARCHITECTURE.md) §0.5.3 Telemetry Vertical + §4 #16, #53–#59 |
| L1 module | [`../../L1/agent-middleware/ARCHITECTURE.md`](../../L1/agent-middleware/ARCHITECTURE.md) §2 Hook surface |
| Policy SSOT | [`docs/telemetry/policy.md`](../../../../docs/telemetry/policy.md) |

## Contents

| View file | Altitude | Drains from |
|---|---|---|
| [`logical.md`](logical.md) | L2 logical — component model: entities, carrier, hooks as FunctionPoints on `EF-HOOK-SURFACE` | L0 §0.5.3 entity list; ADR-0061 §1–§2 |
| [`process.md`](process.md) | L2 process — emission sequence + MDC slice lifetime + dual-write / OTLP sink path | L0 §0.5.3 wire/sink prose; ADR-0061 §3–§5 |

A single-file form (`docs/L2/telemetry-vertical.md`) is permitted by the L2 root
README when only one view is in scope; the per-view form is used here because the
component model (logical) and the emission sequence (process) are distinct
altitudes that the verdict separates.

## Staging

The Telemetry Vertical ships staged (L0 §0.5.3): L1.x is the contract surface
(`TraceContext` Noop SPI, `TraceExtractFilter`, MDC expansion, nullable `Run`
columns, no OTel SDK), W2 un-freezes the Hook SPI and the reference emission
hooks, W3 adds the client SDK, W4 adds the MCP replay surface. This L2 home is
the drain target the verdict requires; it is populated as the W2 Telemetry
Vertical lands, ahead of the consumer impls.

## Gate behaviour

- Gate Rule 37 (`architecture_artefact_front_matter`, enforcer E55): every file
  in this tree declares `level: L2` + a valid `view:`.
- Gate Rule 38 (`architecture_graph_well_formed`, enforcer E56): each non-README
  L2 file is discovered as an `artefact` node and indexed by its `level` / `view`
  in `docs/governance/architecture-graph.yaml`; the build must regenerate
  idempotently.
- Gate Rule 145 (`layer_purity`, enforcers E194 + E195, kernel Rule G-27): the
  L2-detail-sink helper scans only L0 / L1 prose, so the wire / MDC / sink detail
  that is *correctly* sited here is never flagged.

## Authority

- ADR-0061 — Telemetry Vertical Layer (entities, wire format, sink strategy, staging).
- ADR-0062 — Trace ↔ Run ↔ Session N:M identity.
- ADR-0063 — Client SDK observability contract (W3).
- ADR-0017 — Dev-time trace replay (MCP replay surface, no Admin UI).
- ADR-0073 — Engine Hooks + Runtime Middleware SPI (the emission path).
- ADR-0157 — EngineeringFrame Ontology (`EF-HOOK-SURFACE` structural anchor).
- ADR-0068 — Layered 4+1 + Architecture Graph (the L2 altitude itself).
- Rule 33 — Layered 4+1 Discipline; Rule G-27 — L2 detail sink (`CLAUDE.md`).
- Policy SSOT — `docs/telemetry/policy.md`.
