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
- **count**: 30

## Usage

This file is a Tier-2 progressive-disclosure index over the runtime / SPI / protocol contract schemas under `docs/contracts/`. Each line names one schema file, its one-line purpose, and its current `status:` (or `runtime_enforced:` derivative).

Load this index to locate contract schemas by name, purpose, or status. The full schema body lives behind the linked file. Catalog cross-reference: `docs/contracts/contract-catalog.md`. Each contract's authority ADR is named in its leading comment block per Rule M-2 sub-clause .b.

## Index

- [a2a-envelope.v1.yaml](docs/contracts/a2a-envelope.v1.yaml) — contract-layer adoption of the A2A (Agent-to-Agent) protocol envelope shape + Task state vocabulary. Contract-ONLY adoption per ADR-0100 Rejection 3 — NO SDK runtime dep added in agent-service — design_only
- [access-intent.v1.yaml](docs/contracts/access-intent.v1.yaml) — AccessIntent inter-module data contract, version 1 — design_only
- [agent-definition.v1.yaml](docs/contracts/agent-definition.v1.yaml) — AgentDefinition schema, version 1 — design_only
- [agent-event.v1.yaml](docs/contracts/agent-event.v1.yaml) — AgentEvent stream emitted by ExecutorAdapter.execute, version 1 — design_only
- [agent-invoke-request.v1.yaml](docs/contracts/agent-invoke-request.v1.yaml) — contract between agent-service Reactive Orchestrator and the Execution Engine. Service is the Read-Modify-Write closure boundary; Engine is the Pure-Function compute boundary — schema_shipped
- [audit-trail.v1.yaml](docs/contracts/audit-trail.v1.yaml) — declare a regulator-grade, append-only audit-trail schema that captures every Run-level, tool-call-level, and model-invocation-level action a tenant's agent performs on this platform. The schema is designed for direct regulatory submissi... — design_only
- [backpressure-request.v1.yaml](docs/contracts/backpressure-request.v1.yaml) — explicit backpressure request channel on the agent-bus control track (Rule R-E). Converts local-push backpressure signals from the Reactive Orchestrator (per ADR-0100 §2.2) into distributed- pull signals across the federation bus — design_only
- [checkpoint-record.v1.yaml](docs/contracts/checkpoint-record.v1.yaml) — CheckpointRecord — STM-05 recoverable boundary marker, v1 — design_only
- [config-snapshot-ref.v1.yaml](docs/contracts/config-snapshot-ref.v1.yaml) — ConfigSnapshotRef — immutable Run-time configuration binding reference, v1 — design_only
- [control-event.v1.yaml](docs/contracts/control-event.v1.yaml) — ControlEvent IEQ-02 control-channel envelope, version 1 — design_only
- [correlation-record.v1.yaml](docs/contracts/correlation-record.v1.yaml) — CorrelationRecord — cross-Run / remote-Agent handle, v1 — design_only
- [cost-governance.v1.yaml](docs/contracts/cost-governance.v1.yaml) — declare the wire schema + enforcement semantics for per-tenant per-agent token budgets, the enforcement-mode taxonomy, and the spend reporting record. Without this contract, Persona-A cannot answer "what did business center X cost us thi... — design_only
- [engine-envelope.v1.yaml](docs/contracts/engine-envelope.v1.yaml) — Engine Envelope schema, version 1 — design_only
- [engine-hooks.v1.yaml](docs/contracts/engine-hooks.v1.yaml) — Engine Lifecycle Hooks schema, version 1 — design_only
- [engine-port.v1.yaml](docs/contracts/engine-port.v1.yaml) — EnginePort wire contract — the transport-agnostic Service-to-Engine boundary, v1 — design_only
- [error-class.v1.yaml](docs/contracts/error-class.v1.yaml) — ErrorClass — platform-wide error taxonomy enum, v1 — design_only
- [execution-request.v1.yaml](docs/contracts/execution-request.v1.yaml) — ExecutionRequest carrier consumed by ExecutorAdapter.execute, version 1 — design_only
- [governed-messages.v1.yaml](docs/contracts/governed-messages.v1.yaml) — GovernedMessages — M6 TTI-02 output (replaces v1-draft BuiltPrompt), v1 — design_only
- [iam-bridge.v1.yaml](docs/contracts/iam-bridge.v1.yaml) — declare how the platform CONSUMES an enterprise IDP's OIDC token at the HTTP edge AND PROPAGATES the user's identity through agent execution to downstream business-system calls. The contract closes the Persona-F pain point: "Agent identi... — design_only
- [intercept-request.v1.yaml](docs/contracts/intercept-request.v1.yaml) — InterceptRequest — TTI-01 unified intercept entry envelope, v1 — design_only
- [interrupt-registration.v1.yaml](docs/contracts/interrupt-registration.v1.yaml) — InterruptRegistration — HITL interrupt site descriptor, v1 — design_only
- [openapi-v1.yaml](docs/contracts/openapi-v1.yaml) — spring-ai-ascend API v1 — unknown
- [plan-projection.v1.yaml](docs/contracts/plan-projection.v1.yaml) — Plan Projection schema, version 1 — design_only
- [plan.v1.yaml](docs/contracts/plan.v1.yaml) — Plan / PlanStep schema, version 1 — design_only
- [planning-request.v1.yaml](docs/contracts/planning-request.v1.yaml) — PlanningRequest / PlanningResult schema, version 1 — design_only
- [run-event.v1.yaml](docs/contracts/run-event.v1.yaml) — closes F-discriminator-without-discriminated-type for the EvolutionExport enum at agent-service/src/main/java/com/huawei/ascend/service/runtime/evolution/EvolutionExport.java whose package-info Javadoc declares it as discriminator for a... — design_only
- [s2c-callback.v1.yaml](docs/contracts/s2c-callback.v1.yaml) — Server-to-Client (S2C) Capability Callback schema, version 1 — runtime_enforced
- [session-snapshot.v1.yaml](docs/contracts/session-snapshot.v1.yaml) — SessionSnapshot — STM-04 read projection, v1 — design_only
- [tool-result.v1.yaml](docs/contracts/tool-result.v1.yaml) — ToolResult — normalised tool-invocation result, v1 — design_only
- [work-item.v1.yaml](docs/contracts/work-item.v1.yaml) — WorkItem IEQ-03 data-channel envelope, version 1 — design_only
