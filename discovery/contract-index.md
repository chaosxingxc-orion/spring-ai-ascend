---
index_id: DISCOVERY-CONTRACT-INDEX
governance_infra: true
generated_at: 2026-05-28
generator: "spring-ai-ascend Phase A Wave 3"
purpose: "Tier-2 progressive disclosure index — auto-loaded with summary lines; full bodies loaded on demand by phase-contract skills."
---

# Contract Discovery Index

- **schema_version**: 1
- **last_updated**: 2026-05-28
- **count**: 25

## Usage

This file is a Tier-2 progressive-disclosure index over the runtime / SPI / protocol contract schemas under `docs/contracts/`. Each line names one schema file, its one-line purpose, its current `status:` (or `runtime_enforced:` derivative), and a `product_claim` tag — either an actual `PC-NNN` id, `governance_infra` (when the contract governs framework discipline rather than a product capability), or `placeholder` (Wave-4 backfill target).

Load this index to locate contract schemas by name, purpose, or status. The full schema body lives behind the linked file. Catalog cross-reference: `docs/contracts/contract-catalog.md`. Each contract's authority ADR is named in its leading comment block per Rule M-2 sub-clause .b.

## Index

- [a2a-envelope.v1.yaml](docs/contracts/a2a-envelope.v1.yaml) — contract-layer adoption of the A2A (Agent-to-Agent) protocol envelope shape + Task state vocabulary. Contract-ONLY adoption per ADR-0100 Rejection 3 — NO SDK runtime dep added in agent-service — design_only — product_claim:placeholder
- [agent-definition.v1.yaml](docs/contracts/agent-definition.v1.yaml) — AgentDefinition schema, version 1 — design_only — product_claim:placeholder
- [agent-invoke-request.v1.yaml](docs/contracts/agent-invoke-request.v1.yaml) — contract between agent-service Reactive Orchestrator and the Execution Engine. Service is the Read-Modify-Write closure boundary; Engine is the Pure-Function compute boundary — schema_shipped — product_claim:placeholder
- [audit-trail.v1.yaml](docs/contracts/audit-trail.v1.yaml) — declare a regulator-grade, append-only audit-trail schema that captures every Run-level, tool-call-level, and model-invocation-level action a tenant's agent performs on this platform. The schema is designed for direct regulatory submissi... — design_only — product_claim:PC-003
- [backpressure-request.v1.yaml](docs/contracts/backpressure-request.v1.yaml) — explicit backpressure request channel on the agent-bus control track (Rule R-E). Converts local-push backpressure signals from the Reactive Orchestrator (per ADR-0100 §2.2) into distributed- pull signals across the federation bus — design_only — product_claim:placeholder
- [chat-advisor.v1.yaml](docs/contracts/chat-advisor.v1.yaml) — ChatAdvisor / AdvisedRequest / AdvisedResponse schema, version 1 — design_only — product_claim:placeholder
- [engine-envelope.v1.yaml](docs/contracts/engine-envelope.v1.yaml) — Engine Envelope schema, version 1 — runtime_enforced — product_claim:placeholder
- [engine-hooks.v1.yaml](docs/contracts/engine-hooks.v1.yaml) — Engine Lifecycle Hooks schema, version 1 — runtime_enforced — product_claim:placeholder
- [federation-envelope.v1.yaml](docs/contracts/federation-envelope.v1.yaml) — cross-network wire shape for Mode B Business-Centric federation. Wraps the existing ingress-envelope.v1.yaml shape with federation routing metadata — design_only — product_claim:placeholder
- [iam-bridge.v1.yaml](docs/contracts/iam-bridge.v1.yaml) — declare how the platform CONSUMES an enterprise IDP's OIDC token at the HTTP edge AND PROPAGATES the user's identity through agent execution to downstream business-system calls. The contract closes the Persona-F pain point: "Agent identi... — design_only — product_claim:PC-003
- [ingress-envelope.v1.yaml](docs/contracts/ingress-envelope.v1.yaml) — Ingress Envelope schema, version 1 — design_only — product_claim:placeholder
- [memory-store.v1.yaml](docs/contracts/memory-store.v1.yaml) — MemoryStore schema, version 1 — design_only — product_claim:placeholder
- [model-invocation.v1.yaml](docs/contracts/model-invocation.v1.yaml) — ModelInvocation / ModelResponse schema, version 1 — design_only — product_claim:placeholder
- [model-streaming.v1.yaml](docs/contracts/model-streaming.v1.yaml) — ModelResponseChunk / streaming-aware ModelGateway schema, version 1 — design_only — product_claim:placeholder
- [openapi-v1.yaml](docs/contracts/openapi-v1.yaml) — spring-ai-ascend API v1 — unknown — product_claim:placeholder
- [plan-projection.v1.yaml](docs/contracts/plan-projection.v1.yaml) — Plan Projection schema, version 1 — design_only — product_claim:placeholder
- [plan.v1.yaml](docs/contracts/plan.v1.yaml) — Plan / PlanStep schema, version 1 — design_only — product_claim:placeholder
- [planning-request.v1.yaml](docs/contracts/planning-request.v1.yaml) — PlanningRequest / PlanningResult schema, version 1 — design_only — product_claim:placeholder
- [prompt-template.v1.yaml](docs/contracts/prompt-template.v1.yaml) — PromptTemplate / PromptTemplateSource schema, version 1 — design_only — product_claim:placeholder
- [reflection-envelope.v1.yaml](docs/contracts/reflection-envelope.v1.yaml) — S2C envelope shape for online-evolution updates flowing from the cloud Slow Track (LLM-as-Judge) to the active agent's session/memory state. Carried over the existing agent-bus S2C transport (per ADR-0074) — design_only — product_claim:placeholder
- [run-event.v1.yaml](docs/contracts/run-event.v1.yaml) — closes F-discriminator-without-discriminated-type for the EvolutionExport enum at agent-service/src/main/java/com/huawei/ascend/service/runtime/evolution/EvolutionExport.java whose package-info Javadoc declares it as discriminator for a... — design_only — product_claim:placeholder
- [s2c-callback.v1.yaml](docs/contracts/s2c-callback.v1.yaml) — Server-to-Client (S2C) Capability Callback schema, version 1 — runtime_enforced — product_claim:placeholder
- [skill-definition.v1.yaml](docs/contracts/skill-definition.v1.yaml) — SkillDefinition schema, version 1 — design_only — product_claim:placeholder
- [structured-output.v1.yaml](docs/contracts/structured-output.v1.yaml) — StructuredOutputConverter<T> schema, version 1 — design_only — product_claim:placeholder
- [vector-store.v1.yaml](docs/contracts/vector-store.v1.yaml) — VectorStore / Retriever / EmbeddingModel schema, version 1 — design_only — product_claim:placeholder
