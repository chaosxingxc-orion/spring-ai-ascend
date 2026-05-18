---
level: L1
view: logical
module: agent-client
status: skeleton
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0049 (Client SDK / Edge Access plane); Layer-0 principle P-I (Five-Plane Distributed Topology)"
---

# agent-client — L1 architecture (skeleton)

> Owner: AgentClient team | Wave: W3+ | Maturity: skeleton (no code yet)
> Created: 2026-05-17 (six-module materialization PR)

## Status

**This module is a deliberately empty skeleton.** It exists so the
AgentClient team has a stable workspace for the SDK implementation
landing in W3+ per ADR-0049. No production code, no SPI, no tests
beyond the placeholder `package-info.java`.

## 0.4 Layered 4+1 view map (W1 — ADR-0068)

Only the **logical** view is meaningful at this stage. Other views
populate as the SDK takes shape.

| Section | View | Notes |
|---|---|---|
| §1 Role | logical | Edge Access plane SDK |
| §2 Boundary | logical | non-blocking submit → Task Cursor → SSE/Webhook |

## 1. Role

`agent-client` will be the **client-side SDK** that downstream
applications embed to submit Runs and consume their outputs without
holding HTTP connections open. It implements the client half of the
Cursor Flow contract (Rule 36 / P-F): submission returns a Task Cursor
immediately; clients consume process state via SSE and intermediate-
result checkpoints via Webhook.

## 2. Boundary

- **In scope (target):** authenticated submission of `RunRequest`,
  Task Cursor handling, SSE subscriber, Webhook receiver, replay /
  idempotency helpers, posture-aware backoff.
- **Out of scope:** server-side orchestration (post-ADR-0078 / ADR-0079
  lives in `agent-service` plus `agent-runtime-core` for kernel SPI types;
  pre-Phase-C this was split between `agent-platform` and `agent-runtime`),
  heterogeneous engine selection (lives in `agent-execution-engine`), bus
  channels (live in `agent-bus`).
- **Forbidden imports:** none of the server-plane modules
  (pre-Phase-C: `agent-runtime`, `agent-platform`; post-ADR-0078:
  `agent-service`, `agent-runtime-core`; plus `agent-middleware`,
  `agent-execution-engine`, `agent-bus`, `agent-evolve`). Enforced by
  `module-metadata.yaml#forbidden_dependencies` + the planned ArchUnit
  test once code lands. The pre-Phase-C names remain in the forbidden list
  as sentinels — a future PR attempting to depend on them by name (mis-
  reference) is rejected.

## 3. SPI surface

None yet. The SDK is a consumer of platform contracts
(`docs/contracts/openapi-v1.yaml`), not a producer of new SPI.

## Reading order for new contributors

1. `module-metadata.yaml` — module identity + dependency promises.
2. `docs/dfx/agent-client.yaml` — Design-for-X declarations.
3. `docs/contracts/openapi-v1.yaml` + the Task Cursor schema — the
   contract this SDK consumes.
4. ADR-0049 — Edge Access plane authority.
