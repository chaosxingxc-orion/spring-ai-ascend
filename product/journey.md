# Canonical User Journey — `spring-ai-ascend`

Authority: `product/PRODUCT.md`

This is the **canonical journey** the platform must support end-to-end. The protagonist is **Persona-C** (Enterprise Agent Developer, Spring background) — chosen as canonical because she represents ~90% of the agent-developer population per the product owner's input. Persona-D (SRE), Persona-E (Architect), and Persona-F (Compliance) appear at specific stages.

For the **v1.0 financial-industry release (2026-06-30)** the scenario is a concrete one: a **bank developer (Persona-C)** building a **loan-review-assistant agent** that augments a credit officer's pre-approval review with LLM-summarized risk indicators drawn from the applicant's existing data.

Walking this journey is the **canonical end-to-end test** of whether PC-001 .. PC-005 actually deliver on the production system.

## 12 stages

Each stage names the action, the claim it serves, the existing feature / artefact behind it, and its v1.0 ship status.

| # | Stage | Action | Claim | Feature / artefact | v1.0 status |
|---|---|---|---|---|---|
| 1 | Discovery | Persona-C reads `product/PRODUCT.md`, recognizes Spring agent platform framing, identifies herself in Persona-C | PC-001 | `product/PRODUCT.md` | ships in v1.0 (Phase A Wave 1) |
| 2 | Bootstrap | Adds `spring-ai-ascend-starter` to `pom.xml`; `./mvnw spring-boot:run` in `dev` posture; a stub agent runs | PC-001 | `agent-service` Spring Boot starter; `docs/quickstart.md` | shipped |
| 3 | Configure first agent | `application.yaml` declares the agent's tools, model, skills via ConfigurationProperties; no Java boilerplate beyond `@EnableAgents` annotation | PC-001 | `FEAT-RUN-LIFECYCLE-CONTROL` + skill SPI | shipped (lifecycle) / design_only (skill SPI surface) |
| 4 | Wire business middleware | Agent calls business APIs (DB / MQ / internal HTTP) via the same Spring-injected clients she uses in microservices | PC-001 | Rule R-A (no platform patching needed) | shipped |
| 5 | Wire identity | Agent acts on behalf of a credit officer; OAuth2/OIDC token carries tenant claim; capability-scoped delegation to downstream business systems | PC-003 | §4 #56 (JWT tenant cross-check); Spring Security integration | **v1.0 ship-blocker (financial mandate)** |
| 6 | Local test (dev posture) | Agent runs locally; she uses `agent-runtime` traces to inspect Run lifecycle; iterates on prompt + skill config | PC-001, PC-005.a | posture `dev` defaults (R-D-6); platform-axis observability | shipped |
| 7 | Promote to research posture | Flips posture to `research`; tenant isolation enforced at storage engine (RLS); idempotency keys active; cost budget enforced | PC-003 | Rule R-J (RLS); IdempotencyStore; skill-capacity matrix | shipped |
| 8 | Compliance review | Persona-F (compliance officer) reviews the agent's sandbox policy + identity delegation + audit trail format against bank's internal compliance checklist (mapped to JR/T 0223-2021 + SR 11-7) | PC-003 | Rule R-L (sandbox subsumption); `audit-trail.v1.yaml` (NEW v1.0) | **v1.0 ship-blocker — audit-trail schema must land** |
| 9 | Deploy | Pushes the agent definition to the bank's central platform team's hosted runtime (Persona-A operates it — middle-office mode for v1.0) | PC-002 | module `kind:` taxonomy; deployment manifest; `deploy/middle-office-reference/` | **v1.0 ship-blocker — middle-office reference manifest must land** |
| 10 | Observe in production | Three-axis dashboards (business KPI / platform SLO / model behavior); cost per Run attributed per tenant per agent; Persona-D operates this | PC-003, PC-005.a | OTel-compatible spans; cost recorder | v1.0 ships platform-axis + model-axis; business-axis deferred to v1.1+ |
| 11 | Evolve | Over weeks, the agent's knowledge & memory grow via the evolution middleware; skill success rates tracked per (skill, tenant, agent); underperforming skills swapped | PC-005.b | `graphmemory-starter`; memory SPI; skill versioning | **design_only at v1.0 — ships v1.5+** |
| 12 | Close the loop | Persona-E (architect) reviews the trajectory export from this agent's Runs; decides to RL-tune the model on the bank's accumulated trajectories; refined model deployed via model gateway; subsequent Runs use the refined model | PC-005.c, PC-004 | trajectory contract; model gateway | **design_only at v1.0 — first real loop closure target v1.6+** |

## What "v1.0 ships" actually means in this journey

Stages 1-10 must work functionally for an Persona-C developer at a partner bank by 2026-06-30. That means:
- Stage 1: `product/PRODUCT.md` exists and is auto-loaded ✓ (this file lands in Phase A Wave 1)
- Stage 5: IAM bridge ships — OAuth2/OIDC + tenant cross-check + capability-scoped delegation
- Stage 8: Audit-trail schema (`docs/contracts/audit-trail.v1.yaml`) shipped + immutable storage backend wired + Persona-F-facing review checklist exists
- Stage 9: middle-office-mode reference deployment manifest at `deploy/middle-office-reference/` complete
- Stage 11-12: explicitly **NOT** shipping at v1.0 — the evolution loop is part of v1.5+ via weekly cadence Phase B cluster cycles for memory / knowledge / trajectory.

## How the journey is verified

- **Wave 1 manual smoke test**: walk the chain `PC-001 ← claims.yaml ← features.dsl[FEAT-RUN-LIFECYCLE-CONTROL] ← ADR-0020 ← rule-R-C.2.md ← Enforcer E2` and `PC-003 ← claims.yaml ← (same chain)` — both resolve.
- **v1.0 release-gate test**: a real Persona-C from a partner bank walks stages 1-10 end-to-end on the v1.0 release build with a synthetic-finance dataset; Persona-F signs off on stage 8 before promotion.
- **Phase B cycle convergence test**: every cluster cycle replays this journey's relevant stages against the cluster's features, looking for stage-to-claim mismatches.

## Loan-review-assistant scenario details (the v1.0 concrete instance)

To keep the abstract journey grounded, here is the specific v1.0 working example:

- **Agent**: `loan-review-assistant`
- **Business process it augments**: pre-approval review of a small-business loan application
- **What the agent does on each Run**:
  1. Receives the loan-application case-id and the credit officer's identity
  2. Calls the bank's existing CIF (customer information file) API for the applicant's profile (via Spring `WebClient`)
  3. Calls the bank's existing transaction-history API for the past 24 months of bank statements
  4. Calls the bank's existing credit-bureau gateway for the applicant's external credit report
  5. Asks the LLM (via the model gateway) to summarize: cash-flow stability, debt-service ratio, red flags
  6. Returns a structured `LoanRiskSummary` to the credit officer's reviewing UI
- **Why it serves PC-003**:
  - Multi-tenant: agent runs in the bank's tenant, isolated by RLS from any other tenant
  - Identity delegation: every call to CIF / transaction-history / credit-bureau carries the credit officer's identity, not a service principal — Persona-F mandate
  - Audit trail: every Run + every external call + every model invocation persisted with `(runId, tenantId, userId=creditOfficer, occurredAt, evidence_ref)` — regulatory submission ready
  - Sandbox: agent cannot egress to public internet; cannot write outside its scratch dir; CPU/memory/wall-clock-time capped
  - Cost: per-Run token cost attributed to the (tenant=bank, agent=loan-review-assistant) pair
- **Why it serves PC-001**:
  - Spring `WebClient` injected the same way the bank's existing microservices use it
  - LoanRiskSummary is a Spring-managed POJO with Bean Validation
  - Skill config is `application.yaml`, not a new DSL
- **Why it tests PC-002**:
  - The bank operates `agent-service` centrally (middle-office); the loan-review-assistant team only contributes the agent definition + the business-side skill implementations

## Out-of-scope for the v1.0 journey

These stages exist in the design but are NOT verified at v1.0 release:

- Stage 11-12 (evolution loop) — design_only at v1.0
- Persona-B (capability-reuse) deployment path — design_only at v1.0
- Multi-engine swap at stage 3 (the v1.0 graph-state engine is the only engine type) — second engine adapter ships in v1.1
- MCP-bridge for skill exposure — ships in v1.2

These move to v1.1+ weekly-cadence releases.
