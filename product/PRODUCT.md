# `spring-ai-ascend` — Product Authority

This file is the **Tier-1 product authority** for the `spring-ai-ascend` project. Auto-loaded on every AI session per `gate/always-loaded-budget.txt`. Any ADR, contract, rule, or gate in this repo MUST be able to answer "which Product Claim do I serve?" by reference to `product/claims.yaml` — or honestly mark itself `governance_infra: true`.

Companion files (read in this order when you need depth):
- `product/claims.yaml` — Product Claims registry (PC-001 .. PC-005, schema-validated)
- `product/personas.yaml` — Personas registry (Persona-A .. Persona-F)
- `product/journey.md` — Canonical user journey (12 stages)
- `architecture/docs/L0/ARCHITECTURE.md` — L0 architectural constraints (loaded on demand by `/design-mode`)
- `README.md` — developer-facing technical introduction (engineering audience; complementary to this file)

## Authoritative user inputs (verbatim — do not paraphrase)

2026-05-28, the product owner gave the following 5-bullet specification of what `spring-ai-ascend` is for. These are the source-of-truth for every claim below.

> 1）本质上我这个项目是想做一个企业级智能体平台，在不同企业会有典型的两种模式：一种模式是**中台模式**，也就是中台部门提供标准的智能体服务，各业务中心基于自身业务的要求，可配置的开发智能体并推送到智能体服务层代管，同时业务中心在驱动智能体的时候，需要所有的业务逻辑配置和调用业务系统的动作都要求在业务侧自行完成；第二种模式是**能力复用模式**，没有一个很强的中台部门，中台部门更多的是企业的IT部门，提供一些企业级中间件和模型服务的能力，业务中心使用中台部门的中间件，模型服务的能力，自建智能体服务和业务逻辑配置的模块。
>
> 2）针对企业智能体开发者视角，90%以上的开发者都是从微服务时代过来的，他们天生对 Spring 生态有足够的了解和学习能力的掌握，他们希望在智能体时代继续使用类似于配置化开发的方式来开发和集成智能体，因此需要一套基于 Spring 生态体系的智能体开发平台，能够吸收企业级中间件。
>
> 3）我们的大客户很多已经达到了万卡 A100 级的水平，在 Claw 和 Claude Code 被客户广泛认可的现在，智能体服务层需要承载更高的流量，更稳定的服务，以及进入核心生产系统后提供更大的价值和更严格的使用标准和要求。
>
> 4）一个企业对于智能体框架和模型类似，不可能被一套框架限制死，因此在这个平台上运行多源异构的智能体框架，同时还需要简化开发。
>
> 5）智能体平台区别于传统软件平台的核心要素是智能体在其中能够**自进化的能力**——智能体执行可观测（业务侧、平台侧、模型侧均要具备可观测能力），同时可演进（基于中间件的知识、记忆和技能的演进），以及对模型通过强化学习来演进所需要的轨迹支撑。

## Elevator pitch

`spring-ai-ascend` is the Spring-native enterprise agent platform for the AI-platform era — letting Spring-shop enterprises build, deploy, and evolve multi-framework agents at production scale under their own organizational topology (中台 or 能力复用) on sovereign Huawei Ascend + Kunpeng infrastructure, with built-in identity, cost, safety, and observability governance that microservices teams already understand.

## v1.0 target buyer (2026-06-30 release)

**Financial-industry first.** v1.0 is built to serve enterprise financial-services buyers (banks, insurers, securities firms, fintech platforms) where:
- Multi-tenant isolation is table stakes (Persona-A / Persona-B / Persona-D requirement)
- Audit-grade observability is mandatory for regulatory submission (Persona-F primary persona for v1.0)
- Identity delegation must trace every agent action to an end-user identity (中国 等保 / PIPL / JR/T 0223-2021; 国际 SOC 2 / SR 11-7)
- Sandbox enforcement must subsume any logical permission grant — no honor-system

v1.1+ extends the same artefact set to other regulated verticals (manufacturing, healthcare, government). The platform positioning (`README.md`) stays vertical-neutral; `product/PRODUCT.md` is where the v1.0 GTM target is named.

## Product Claims (summary)

Full schema in `product/claims.yaml`. Each claim has `id`, `statement`, `beneficiaries[]`, `evidence_refs[]`, `success_metric`, `status`.

| ID | One-line claim | Primary persona |
|---|---|---|
| **PC-001** | Build agents the way you already build Spring services — config-driven, Spring Boot starter-based, ConfigurationProperties-validated, full reuse of existing Spring middleware | Persona-C (Spring developer, ~90% of agent-developer population) |
| **PC-002** | Deploy under your organization's shape AND your sovereignty boundary — same artifact set supports 中台 + 能力复用 + on-prem Ascend+Kunpeng | Persona-A (中台 buyer) + Persona-B (能力复用 buyer) |
| **PC-003** | Production-grade for the AI-platform era — long-horizon Run state machine, RLS multi-tenancy, reactive I/O, posture-aware defaults, idempotency, capability-scoped identity, sandbox subsumption, cost governance, audit-grade observability | Persona-D (SRE) + Persona-F (Compliance) |
| **PC-004** | Run any agent framework, governed uniformly — multiple agent frameworks (graph / ReAct / supervisor-worker / debate / external SDK), multiple LLM providers, multiple orchestration patterns through one Engine Contract | Persona-E (Architect) |
| **PC-005** | Agents that evolve, not just execute — three-axis observability (business/platform/model), evolvable knowledge+memory+skill middleware, RL trajectory export for model fine-tuning | Persona-E + Persona-C |

PC-001..PC-004 are competitive (others could replicate). **PC-005 is the differentiator** — it requires co-designed observability + middleware + trajectory contract that cannot be bolted on.

## Personas (summary)

Full schema in `product/personas.yaml`. Each persona has `id`, `role`, `org_context`, `daily_job`, `success_criteria`, `pain_points`.

| ID | Role | v1.0 priority |
|---|---|---|
| **Persona-A** | Platform Team Lead (中台 buyer) | secondary v1.0 (能力复用-mode deploys in v1.1) |
| **Persona-B** | Enterprise IT Capability Provider (能力复用 buyer) | deferred to v1.1 |
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
- **Source ADR**: ADR-0155 (Product Authority and Traceability Chain) — to be filed in Phase A Wave 3 of the plan at `D:\.claude\plans\ai-l0-adr-ai-l1-adr-adr-ai-ai-1-2-3-ai-effervescent-flask.md`.
- **Governing rule**: Rule G-16 .. G-21 (Phase A Wave 5 — ProductClaim Referential Integrity / No Orphan Artefacts / Traceability Chain Completeness / Auto-Load Tier Integrity / Governance-Infra Honesty / Placeholder Decreasing).
