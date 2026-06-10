---
index_id: DISCOVERY-ADR-INDEX
governance_infra: true
generated_at: 2026-05-28
generator: "spring-ai-ascend Phase A Wave 3"
purpose: "Tier-2 progressive disclosure index — auto-loaded with summary lines; full bodies loaded on demand by phase-contract skills."
---

# ADR Discovery Index

schema_version: 1 | last_updated: 2026-05-28 | count: 67

Tier-2 progressive-disclosure index over the ADR corpus. Each row: id link, title, `status:`. Sources: `docs/adr/*.yaml`, `docs/adr/*.md`, `docs/adr/locked/*.md`, `architecture/decisions/*` (precedence in that order on id collision).

## Index

- [ADR-0001](docs/adr/locked/0001-java-21-spring-boot-runtime.md) — Java 21 + Spring Boot 4.0.5 as the runtime baseline — accepted
- [ADR-0002](docs/adr/locked/0002-spring-ai-llm-gateway.md) — Spring AI 2.0.0-M5 as the LLM gateway, not LangChain4j — accepted
- [ADR-0004](docs/adr/locked/0004-postgres-primary-data-store.md) — PostgreSQL 16 with RLS + pgvector, not separate vector DB — accepted
- [ADR-0005](docs/adr/locked/0005-tenant-isolation-guc-set-local.md) — Row-level security with SET LOCAL transaction-scoped GUC, not per-connection re… — accepted
- [ADR-0006](docs/adr/locked/0006-posture-model-dev-research-prod.md) — ActionGuard 5-stage chain (cycle-9 truth-cut), not 11-stage — accepted
- [ADR-0010](docs/adr/locked/0010-spring-security-oauth2.md) — Keycloak (OSS) as default IdP, but customer can BYO — accepted
- [ADR-0011](docs/adr/locked/0011-flyway-schema-migration.md) — Spring Cloud Gateway as ingress, not Kong / Traefik — accepted
- [ADR-0014](docs/adr/locked/0014-contract-spine-versioning-policy.md) — 3-posture model (dev/research/prod), not 5 or 2 — accepted
- [ADR-0015](docs/adr/locked/0015-layered-architecture-capability-model.md) — Defer multi-framework dispatch (Python sidecar, LangChain4j) to W4+ — accepted
- [ADR-0016](docs/adr/0016-a2a-federation-strategic-deferral.yaml) — A2A Federation — Strategic Deferral to Post-W4 — accepted
- [ADR-0020](docs/adr/locked/0020-runlifecycle-spi-and-runstatus-formal-dfa.md) — RunLifecycle SPI Separation and RunStatus Formal DFA — accepted
- [ADR-0024](docs/adr/0024-suspension-write-atomicity.yaml) — Suspension Write Atomicity: Checkpointer and RunRepository Transactional Contra… — accepted
- [ADR-0025](docs/adr/0025-checkpoint-ownership-boundary.yaml) — Checkpoint Ownership Boundary: Executor Resume Cursors vs Orchestrator Run Row — accepted
- [ADR-0027](docs/adr/locked/0027-idempotency-scope-w0-header-validation.md) — Idempotency Scope at W0: Header Validation Only, Dedup Deferred to W1 — accepted
- [ADR-0029](docs/adr/0029-cognition-action-separation.yaml) — Cognition-Action Separation Principle — accepted
- [ADR-0032](docs/adr/0032-scope-based-run-hierarchy-and-planner-contract-minimal.yaml) — Scope-Based Run Hierarchy and Planner Contract Minimal — accepted
- [ADR-0033](docs/adr/0033-logical-identity-equivalence-and-deployment-locus-vocabulary.yaml) — Logical Identity Equivalence and Deployment-Locus Vocabulary — accepted
- [ADR-0035](docs/adr/0035-posture-enforcement-single-construction-path.yaml) — Posture Enforcement Single-Construction-Path — accepted
- [ADR-0038](docs/adr/0038-skill-spi-resource-tier-classification.yaml) — Skill SPI Resource Tier Classification — accepted
- [ADR-0039](docs/adr/0039-payload-migration-adapter-strategy.yaml) — Payload Migration Adapter Strategy — accepted
- [ADR-0040](docs/adr/0040-w1-http-contract-reconciliation.yaml) — W1 HTTP Contract Reconciliation — accepted
- [ADR-0049](docs/adr/0049-c-s-dynamic-hydration-protocol.yaml) — C/S Dynamic Hydration Protocol and Cursor Handoff — accepted
- [ADR-0051](docs/adr/0051-memory-knowledge-ownership-boundary.yaml) — Memory and Knowledge Ownership Boundary — accepted
- [ADR-0052](docs/adr/0052-skill-topology-scheduler-and-capability-bidding.yaml) — Skill Topology Scheduler and Capability Bidding — accepted
- [ADR-0053](docs/adr/0053-cohesive-agent-swarm-execution.yaml) — Cohesive Agent Swarm Execution — Workflow Authority Invariant + SpawnEnvelope — accepted
- [ADR-0059](docs/adr/0059-code-as-contract-architectural-enforcement.yaml) — Code-as-Contract Architectural Enforcement (Introduces Rule R-C.a) — accepted
- [ADR-0064](docs/adr/0064-layer-0-governing-principles.yaml) — Layer-0 Governing Principles + CLAUDE.md Restructure — accepted
- [ADR-0065](docs/adr/0065-competitive-baselines.yaml) — Competitive Baselines (Four Pillars) — accepted
- [ADR-0066](docs/adr/0066-independent-module-evolution.yaml) — Independent Module Evolution — accepted
- [ADR-0067](docs/adr/0067-spi-dfx-tck-codesign.yaml) — SPI + DFX + TCK Co-Design — accepted
- [ADR-0068](docs/adr/0068-layered-4plus1-and-architecture-graph.yaml) — Layered 4+1 and Architecture Graph as Twin Sources of Truth — accepted
- [ADR-0069](docs/adr/0069-l0-ironclad-rules.yaml) — Layer-0 Ironclad Rules — promotion of LucioIT W1 §6/§7 to governing principles — accepted
- [ADR-0071](docs/adr/0071-engine-contract-structural-wave.yaml) — Engine Contract Structural Wave — umbrella for ADR-0072..0075 + L0 principle P-M — accepted
- [ADR-0075](docs/adr/0075-evolution-scope-boundary.yaml) — Evolution Scope Boundary -- server-controlled evolution surface, fourth L1 expr… — accepted
- [ADR-0077](docs/adr/0077-schema-first-domain-contracts.yaml) — Schema-First Domain Contracts — codify the W2.x prose-enum prohibition (P-M cro… — accepted
- [ADR-0081](docs/adr/0081-resilience-contract-dual-surface-reconciliation.yaml) — ResilienceContract dual-surface reconciliation (operation-policy + skill-capaci… — accepted
- [ADR-0089](docs/adr/0089-edge-plane-ingress-gateway-mandate.yaml) — Edge-Plane Ingress Gateway Mandate — client → bus → server is the only allowed… — accepted
- [ADR-0101](docs/adr/0101-rc22-polymorphic-deployment-topology.yaml) — rc22 — Polymorphic Deployment Topology (Mode A Platform-Centric + Mode B Busine… — accepted
- [ADR-0102](docs/adr/0102-rc22-evolution-plane-online-offline-duality.yaml) — rc22 — Evolution Plane Online/Offline Duality (Offline T+1 + Online Dual-Track… — accepted
- [ADR-0103](docs/adr/0103-rc22-agent-middleware-naming-and-capability-services-distribution.yaml) — rc22 — agent-middleware naming resolution (REJECT rename + REJECT 7th module) +… — accepted
- [ADR-0117](docs/adr/0117-rc37-ascend-kunpeng-strategic-repositioning.yaml) — rc37 strategic repositioning: Ascend/Kunpeng hardware-synergy platform; drop FS… — accepted
- [ADR-0119](architecture/decisions/0119.md) — Single-Source Rendering for derived architecture documents (release notes, READ… — accepted
- [ADR-0120](docs/adr/0120-brand-audience-b-alignment.yaml) — Brand & Audience B alignment: KEEP `spring-ai-ascend` identity and Audience B p… — accepted
- [ADR-0134](docs/adr/0134-tool-call-iteration-loop.yaml) — Tool-Call Iteration Loop: agent-driven vs. planner-driven execution modes for L… — accepted
- [ADR-0135](docs/adr/0135-agent-session-as-run-projection.yaml) — AgentSession as Run-Projection: no separate AgentSession SPI; conversation cont… — accepted
- [ADR-0136](docs/adr/0136-vocabulary-reconciliation-pr71-task-vs-run.yaml) — Vocabulary Reconciliation: PR 71 'Task' ≡ existing platform Task entity (not Ru… — accepted
- [ADR-0137](docs/adr/0137-suspendsignal-canonical-interruptsignal-glossary.yaml) — SuspendSignal remains canonical; InterruptSignal / InterruptReason are L1 gloss… — accepted
- [ADR-0138](docs/adr/0138-agent-service-five-layer-l1-ratification.yaml) — agent-service 5-layer L1 ratification: PR 71 layers (Access / Session&Task / Ev… — accepted
- [ADR-0139](docs/adr/0139-fast-slow-path-narrowed-semantics.yaml) — Fast-Path / Slow-Path narrowed semantics: Fast-Path = in-process reactive synch… — accepted
- [ADR-0140](docs/adr/0140-engine-adapter-layer-split.yaml) — Engine Adapter Layer split: Layer 5 in ADR-0138 5-layer model decomposes into L… — accepted
- [ADR-0141](docs/adr/0141-internal-event-queue-design-only.yaml) — Internal Event Queue (ADR-0138 Layer 3) is a design_only sub-section of agent-s… — accepted
- [ADR-0142](docs/adr/0142-run-aggregate-single-owner.yaml) — Run aggregate (Run record + RunStatus + RunStateMachine + RunRepository) is OWN… — accepted
- [ADR-0144](docs/adr/0144-layer-vs-package-matrix.yaml) — Layer ↔ Package matrix unifies the rc22 ADR-0100 5-component package-structural… — accepted
- [ADR-0145](docs/adr/0145-run-event-sealed-hierarchy.yaml) — Sealed RunEvent hierarchy specification: defines the polymorphic event type tha… — accepted
- [ADR-0146](docs/adr/0146-suspend-reason-taxonomy-alignment-2026-05-22.yaml) — SuspendReason taxonomy canonical alignment to 2026-05-22 expansion-proposal-res… — accepted
- [ADR-0147](docs/adr/0147-structurizr-workspace-authority.yaml) — Structurizr workspace closure as the architecture authoring root, with programm… — accepted
- [ADR-0148](architecture/decisions/0148.md) — Wave 0 spike results — Structurizr workspace authority feasibility — accepted
- [ADR-0149](architecture/decisions/0149.md) — Structurizr workspace authority — W0..W5 shipped; W6/W7 entry-criteria document… — accepted
- [ADR-0150](docs/adr/0150-architecture-design-system-unification-under-structurizr.yaml) — Architecture design system unified under architecture/ — L1 corpus + module ARC… — accepted
- [ADR-0151](docs/adr/0151-l1-feature-registry-canonical-schema.yaml) — L1 Feature Registry canonical schema — SAA Feature tag + AI Execution Boundary… — accepted
- [ADR-0152](docs/adr/0152-uniform-l1-per-view-mechanism-and-l0-mounting.yaml) — Uniform L1 per-view mechanism + L0 mounting under architecture/docs/L0/ — accepted
- [ADR-0153](docs/adr/0153-l1-feature-registry-closure.yaml) — L1 Feature Registry closure — Rule G-14 blocking flip + plan completion — accepted
- [ADR-0154](docs/adr/0154-fact-layer-authority.yaml) — Fact-Layer Authority — generated structured facts as the AI's primary L1 input — accepted
- [ADR-0155](docs/adr/0155-agent-service-l1-v1-2-internal-module-design.yaml) — AgentService L1 v1.2 — internal module design absorption (M1–M6 + 6 boundary re… — accepted
- [ADR-0156](docs/adr/0156-product-authority-and-traceability.yaml) — Product Authority and Traceability Chain — ProductClaim as the binding axis bet… — accepted
- [ADR-0157](docs/adr/0157-engineering-frame-ontology.yaml) — EngineeringFrame Ontology — structural axis between Module and FunctionPoint fo… — accepted
- [ADR-0158](docs/adr/0158-engine-port-transport-agnostic-boundary.yaml) — Engine Boundary (EnginePort) — transport-agnostic Service↔Engine contract absor… — accepted
