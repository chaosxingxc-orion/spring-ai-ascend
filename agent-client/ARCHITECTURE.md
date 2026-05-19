---
level: L1
view: logical
module: agent-client
status: skeleton
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0049 (Client SDK / Edge Access plane); ADR-0089 (Edge-Plane Ingress Gateway Mandate); Layer-0 principle P-I (Five-Plane Distributed Topology); CLAUDE.md Rule R-I sub-clause .b"
---

# agent-client — L1 architecture (skeleton)

> Owner: AgentClient team | Wave: W3+ | Maturity: skeleton (no code yet)
> Created: 2026-05-17 (six-module materialization PR); cross-plane routing locked 2026-05-20 (rc13 / ADR-0089)

## Status

**This module is a deliberately empty skeleton.** It exists so the
AgentClient team has a stable workspace for the SDK implementation
landing in W3+ per ADR-0049. No production code, no SPI, no tests
beyond the placeholder `package-info.java` AND the rc13 ArchUnit
sentinel test `EdgeToComputeDirectLinkArchTest` (vacuous-but-armed —
fires the moment SDK code lands and attempts a forbidden direct import).

## 0.4 Layered 4+1 view map (W1 — ADR-0068)

Only the **logical** view is meaningful at this stage. Other views
populate as the SDK takes shape.

| Section | View | Notes |
|---|---|---|
| §1 Role | logical | Edge Access plane SDK |
| §2 Boundary | logical | non-blocking submit → IngressGateway → Task Cursor → SSE/Webhook |
| §3 SPI surface | logical | consumes `bus.spi.ingress.IngressGateway`; no SPI produced |
| §6 W3+ test plan placeholder | logical | adapter tests; ingress contract conformance; backpressure handling |

## 1. Role

`agent-client` will be the **client-side SDK** that downstream
applications embed to submit Runs and consume their outputs without
holding HTTP connections open. It implements the client half of the
Cursor Flow contract (Rule R-F): submission returns a Task Cursor
immediately; clients consume process state via SSE and intermediate-
result checkpoints via Webhook.

## 2. Boundary

- **In scope (target):** authenticated submission of `RunRequest` via
  the `bus.spi.ingress.IngressGateway` SPI, Task Cursor handling, SSE
  subscriber, Webhook receiver, replay / idempotency helpers,
  posture-aware backoff.
- **Out of scope:** server-side orchestration (lives in `agent-service`;
  pre-Phase-C this was split between `agent-platform` and `agent-runtime`,
  consolidated per ADR-0078; the transient `agent-runtime-core` module
  was dissolved per ADR-0088 with runs / idempotency relocated back to
  `agent-service`), heterogeneous engine selection (lives in
  `agent-execution-engine`, owns `engine.orchestration.spi` per ADR-0088),
  bus channels (live in `agent-bus`).
- **No-direct-link clause (rc13 — ADR-0089 / Rule R-I sub-clause .b):**
  The SDK MUST NOT import any class under
  `ascend.springai.{service,engine,middleware}..`. Cross-plane traffic
  flows exclusively through `ascend.springai.bus.spi.ingress.IngressGateway`,
  whose wire schema is `docs/contracts/ingress-envelope.v1.yaml`. Enforced
  by ArchUnit `EdgeToComputeDirectLinkArchTest` (E143) + gate Rule 105
  (`edge_no_direct_compute_link`) + module-metadata
  `forbidden_dependencies`. Promotion of the ingress contract from
  `design_only` to `runtime_enforced` is triggered by the first SDK release.
- **Forbidden imports:** all compute_control plane modules
  (`agent-service`, `agent-execution-engine`, `agent-middleware`) +
  `agent-evolve`. `agent-bus` is intentionally NOT forbidden — the SDK
  legitimately *consumes* the `bus.spi.ingress.IngressGateway` interface
  as its sole cross-plane entry point.

## 3. SPI surface

None produced. The SDK is a consumer of platform contracts:

- `docs/contracts/ingress-envelope.v1.yaml` — wire shape for client → bus → server traffic.
- `docs/contracts/openapi-v1.yaml` — HTTP route catalogue (which the bus implementation
  forwards to internally; the SDK does NOT speak directly to these routes).
- The Task Cursor schema (Rule R-F).

## 6. Test plan placeholder (W3+ landing wave)

When the W3+ SDK PR lands it MUST include:

- Unit tests for `IngressEnvelope` / `IngressResponse` consumer-side
  construction + serialisation.
- Adapter tests verifying every `IngressGateway` call shape exercises a
  valid `IngressRequestType` discriminator.
- ArchUnit `EdgeToComputeDirectLinkArchTest` continues to pass (sentinel
  added in rc13; W3+ PR re-runs it green on the populated tree).
- Backpressure handling test — `IngressResponse.deferred(...)` is
  consumed as a retry-with-backoff hint, not a terminal failure (see
  deferred Rule R-I sub-clause .e in `docs/CLAUDE-deferred.md`).
- Contract-conformance test against `ingress-envelope.v1.yaml` snapshot.

## Reading order for new contributors

1. `module-metadata.yaml` — module identity + dependency promises.
2. `docs/dfx/agent-client.yaml` — Design-for-X declarations.
3. `docs/contracts/ingress-envelope.v1.yaml` — the cross-plane wire shape this SDK consumes.
4. `agent-bus/ARCHITECTURE.md` §3a — the IngressGateway SPI surface the SDK calls.
5. ADR-0049 (Edge Access plane), ADR-0089 (Edge-Plane Ingress Gateway Mandate).
