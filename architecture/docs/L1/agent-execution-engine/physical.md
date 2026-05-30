---
level: L1
view: physical
status: active
authority: "ADR-0101 (deployment loci) + ADR-0158 (transport-agnostic EnginePort boundary)"
---

# `agent-execution-engine` — Physical View

## Deployment plane

`deployment_plane: compute_control` (matches
`module-metadata.yaml#deployment_plane`). The engine runs in-process with
the runtime kernel; it holds no durable state and exposes no network
endpoint of its own — it is reached through the EnginePort boundary
(ADR-0158).

## Topology

`deployment_loci: [platform_centric, business_centric]` — the engine is
location-agnostic.

- **Mode-A (platform-centric).** The engine runs on the platform.
- **Mode-B (business-centric).** The engine joins `agent-service` on the
  business side for zero-latency local execution loops. The SPI and the
  envelope are unchanged across loci (ADR-0101).

## Resource model

Resource usage is dominated by the adapter's own compute (LLM calls for
the agent-loop kind, graph traversal for the workflow kind). The engine
contributes no persistence, no pool, and no independent scheduler; capacity
and resilience for compute are governed via the resilience contract owned
by `agent-service`.
