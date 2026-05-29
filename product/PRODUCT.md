# `spring-ai-ascend` — Product Authority

This file is the **Tier-1 product authority** for the `spring-ai-ascend` project. Auto-loaded on every AI session per `gate/always-loaded-budget.txt`. Any ADR, contract, rule, or gate in this repo MUST be able to answer "which Product Claim do I serve?" by reference to `product/claims.yaml` — or honestly mark itself `governance_infra: true`.

Companion files (read in this order when you need depth):
- `product/claims.yaml` — Product Claims registry (PC-001 .. PC-005, schema-validated)
- `product/personas.yaml` — Personas registry (Persona-A .. Persona-F)
- `product/journey.md` — Canonical user journey (12 stages)
- `architecture/docs/L0/ARCHITECTURE.md` — L0 architectural constraints (loaded on demand by `/design-mode`)
- `README.md` — developer-facing technical introduction (engineering audience; complementary to this file)

## Authoritative user inputs (English translation — do not paraphrase)

2026-05-28, the product owner gave the following 5-bullet specification of what `spring-ai-ascend` is for. These are the source-of-truth for every claim below. The verbatim Chinese original is archived at `product/source-inputs/2026-05-28-product-owner-input.zh.md` (not auto-loaded); the English below is the authoritative translation.

> 1) Fundamentally this project aims to build an enterprise-grade agent platform. Across different enterprises there are two typical modes. The first is the **middle-office mode**: the middle-office department provides standard agent services; each business center, based on its own business requirements, develops agents in a configuration-driven way and pushes them to the agent service layer for hosting. When a business center drives an agent, all business-logic configuration and the actions that call business systems are required to be completed by the business side itself. The second is the **capability-reuse mode**: there is no strong middle-office department — the "middle office" is more the enterprise's IT department, which provides some enterprise-grade middleware and model-service capabilities. Business centers use the IT department's middleware and model-service capabilities, and self-build their own agent service and business-logic configuration modules.
>
> 2) From the perspective of the enterprise agent developer: over 90% of developers come from the microservices era. They naturally have sufficient understanding of the Spring ecosystem and the ability to master it. They want to continue developing and integrating agents in the agent era using a configuration-driven development style similar to what they already know. Therefore a Spring-ecosystem-based agent development platform is needed, one that can absorb enterprise-grade middleware.
>
> 3) Many of our large customers have already reached the 10,000-card A100 class. Now that Claw and Claude Code are widely recognized by customers, the agent service layer needs to carry higher traffic, deliver more stable service, and — once it enters core production systems — provide greater value under stricter usage standards and requirements.
>
> 4) Like models, an enterprise cannot be locked into a single agent framework. Therefore the platform must run multi-source, heterogeneous agent frameworks while also simplifying development.
>
> 5) The core element distinguishing an agent platform from a traditional software platform is the agents' capacity for **self-evolution** within it — agent execution must be observable (observability on the business side, platform side, and model side), agents must be evolvable (evolution of knowledge, memory, and skills based on middleware), and there must be trajectory support for evolving models via reinforcement learning.

## Elevator pitch

`spring-ai-ascend` is the Spring-native enterprise agent platform for the AI-platform era — letting Spring-shop enterprises build, deploy, and evolve multi-framework agents at production scale under their own organizational topology (middle-office or capability-reuse mode) on sovereign Huawei Ascend + Kunpeng infrastructure, with built-in identity, cost, safety, and observability governance that microservices teams already understand.

## v1.0 target buyer (2026-06-30 release)

**Financial-industry first.** v1.0 is built to serve enterprise financial-services buyers (banks, insurers, securities firms, fintech platforms) where:
- Multi-tenant isolation is table stakes (Persona-A / Persona-B / Persona-D requirement)
- Audit-grade observability is mandatory for regulatory submission (Persona-F primary persona for v1.0)
- Identity delegation must trace every agent action to an end-user identity (China: MLPS / PIPL / JR/T 0223-2021; international: SOC 2 / SR 11-7)
- Sandbox enforcement must subsume any logical permission grant — no honor-system

v1.1+ extends the same artefact set to other regulated verticals (manufacturing, healthcare, government). The platform positioning (`README.md`) stays vertical-neutral; `product/PRODUCT.md` is where the v1.0 GTM target is named.

## Product Claims (summary)

Full schema in `product/claims.yaml`. Each claim has `id`, `statement`, `beneficiaries[]`, `evidence_refs[]`, `success_metric`, `status`.

| ID | One-line claim | Primary persona |
|---|---|---|
| **PC-001** | Build agents the way you already build Spring services — config-driven, Spring Boot starter-based, ConfigurationProperties-validated, full reuse of existing Spring middleware | Persona-C (Spring developer, ~90% of agent-developer population) |
| **PC-002** | Deploy under your organization's shape AND your sovereignty boundary — same artifact set supports middle-office + capability-reuse modes + on-prem Ascend+Kunpeng | Persona-A (middle-office buyer) + Persona-B (capability-reuse buyer) |
| **PC-003** | Production-grade for the AI-platform era — long-horizon Run state machine, RLS multi-tenancy, reactive I/O, posture-aware defaults, idempotency, capability-scoped identity, sandbox subsumption, cost governance, audit-grade observability | Persona-D (SRE) + Persona-F (Compliance) |
| **PC-004** | Run any agent framework, governed uniformly — multiple agent frameworks (graph / ReAct / supervisor-worker / debate / external SDK), multiple LLM providers, multiple orchestration patterns through one Engine Contract | Persona-E (Architect) |
| **PC-005** | Agents that evolve, not just execute — three-axis observability (business/platform/model), evolvable knowledge+memory+skill middleware, RL trajectory export for model fine-tuning | Persona-E + Persona-C |

PC-001..PC-004 are competitive (others could replicate). **PC-005 is the differentiator** — it requires co-designed observability + middleware + trajectory contract that cannot be bolted on.

## Personas (summary)

Full schema in `product/personas.yaml`. Each persona has `id`, `role`, `org_context`, `daily_job`, `success_criteria`, `pain_points`.

| ID | Role | v1.0 priority |
|---|---|---|
| **Persona-A** | Platform Team Lead (middle-office buyer) | secondary v1.0 (capability-reuse-mode deploys in v1.1) |
| **Persona-B** | Enterprise IT Capability Provider (capability-reuse buyer) | deferred to v1.1 |
| **Persona-C** | Enterprise Agent Developer (Spring background, ~90% of dev population) | primary developer for v1.0 |
| **Persona-D** | Enterprise SRE / Production Operator | primary operator for v1.0 |
| **Persona-E** | Enterprise Agent Architect | primary for PC-004 + PC-005 conversations |
| **Persona-F** | Enterprise Compliance / Risk Officer | **PRIMARY for v1.0** (financial vertical mandate) |

## Canonical journey

A first-time Spring developer (Persona-C) goes from "I have a business process that needs LLM augmentation" to "agent is in production, evolving". 12 stages from discovery to evolution-loop closure. Full sequence + per-stage claim binding in `product/journey.md`.

v1.0 ships stages 1-10 functional (stages 11-12 evolution loop is `design_only`).

## What `spring-ai-ascend` deliberately does NOT do

Disclaimed explicitly to keep claims honest under Rule G-3.e:

- **No no-code / drag-drop agent builder UI.** Config-driven YAML is the abstraction; UI is downstream / customer-built.
- **No vendor-hosted SaaS.** Sovereignty positioning is anti-SaaS.
- **No browser-based agent execution.** Server-side execution is the model; client-side via S2C callback only.
- **No mobile SDK.** Agent-client SDK is server-callable; mobile is downstream.
- **No native chat UI.** We ship contract + reference samples only.

## How this file relates to the rest of the repo

- `README.md` introduces the project as an Ascend+Kunpeng general enterprise agent platform — vertical-neutral, developer-facing technical framing. **Do not financialise README.**
- `product/PRODUCT.md` (this file) is the **product authority** — names v1.0 buyer, claims, personas, journey. Auto-loaded so every AI session starts with product context before governance.
- `architecture/docs/L0/ARCHITECTURE.md` declares the 65 §4 architectural constraints. Each constraint cited by an enforcer (`docs/governance/enforcers.yaml`); each enforcer cited by a rule card (`docs/governance/rules/rule-*.md`).
- `architecture/features/features.dsl` is the L1 Feature Registry. Every SAA Feature element MUST declare `saa.productClaim` resolving to a `PC-NNN` in `product/claims.yaml` OR `governance-infra`.

## Authority + lifecycle

- **Author of this file**: product owner (chao). AI may draft refinements; only product owner signs off changes to the elevator pitch, the 5 PC statements, or the v1.0 buyer scope.
- **Source ADR**: ADR-0156 (Product Authority and Traceability Chain) — filed in Phase A Wave 3; ADR-0156 is the source authority for this file.
- **Governing rule**: Rule G-16 .. G-21 (Phase A Wave 5 — ProductClaim Referential Integrity / No Orphan Artefacts / Traceability Chain Completeness / Auto-Load Tier Integrity / Governance-Infra Honesty / Placeholder Decreasing).
