# Onboarding — Enterprise Compliance / Risk Officer (Persona-F)

> **PRIMARY v1.0 PERSONA.** Read time: ~5 minutes. After this, you have a working checklist to audit and certify an agent for financial-industry production rollout.

You are the **gating persona for v1.0**. Without your sign-off, an agent does not enter financial-industry production on this platform. The platform was built assuming your background: risk / compliance / audit function in a regulated enterprise; familiar with regulatory documentation under 中国 等保 2.0/3.0 + 关键信息基础设施 + PIPL + 数据安全法 + JR/T 0223-2021 (金融数据安全 数据生命周期安全规范), and 国际 SOC 2 / ISO 27001 / SR 11-7 model risk.

## What this platform is, from your perspective

`spring-ai-ascend` is the first enterprise agent platform that codifies financial-industry production requirements as **enforceable structural rules** (not best-practice prose). Every dimension your audit asks about — tenant isolation, identity delegation, audit-trail format, sandbox enforcement, model-risk documentation — has a corresponding rule, an enforcer, and a gate check that runs on every commit. You're not certifying claims; you're certifying enforced behaviour.

Authoritative product framing: `product/PRODUCT.md`. Your persona: `product/personas.yaml` → Persona-F. Your primary product claim: **PC-003** (Production-grade for the AI-platform era — financial-vertical mandate for v1.0).

## The v1.0 financial certification checklist (what you actually do)

For each agent your team wants to promote to production, walk this checklist:

### 1. Tenant isolation — enforced at storage engine

- **Requirement**: cross-tenant read / write impossible; tenant identity flows from HTTP edge to storage layer.
- **Where to verify**: every Flyway migration creating a `tenant_id`-bearing table enables Postgres Row-Level Security in the same migration (Rule R-J.a); `agent-service/src/main/java/com/huawei/ascend/service/runtime/**` integration tests under `TenantIsolationIT.java` MUST be green.
- **Evidence for audit submission**: the gate output proving R-J.a passes + the IT report.
- **What to reject if missing**: any agent persisting state to a table without RLS migration paired in the same change.

### 2. Identity delegation — every action ties to a user

- **Requirement**: when an agent calls a downstream business system, the call carries the **end-user's** identity (e.g., credit officer's OAuth token), NOT a service principal.
- **Where to verify**: §4 #56 (JWT validation + tenant claim cross-check at every public endpoint); `agent-service/.../IamBridgeIT.java`; identity propagation through `SecurityContextHolder` to outbound `WebClient` calls.
- **Evidence for audit submission**: end-to-end identity-propagation trace showing `userId=<credit-officer-id>` on every downstream call in a sample Run.
- **What to reject if missing**: any agent where downstream calls use service-principal identity for user-initiated work.

### 3. Audit trail — regulatory-submission format

- **Requirement**: every Run + tool call + model invocation persisted immutably with `(runId, tenantId, userId, fromStatus→toStatus, occurredAt, evidence_ref)`. Append-only storage. Checksum chain for tamper-evidence.
- **Where to verify**: `docs/contracts/audit-trail.v1.yaml` (ships v1.0); audit storage backend (append-only Postgres table + checksum chain wired); `AuditTrailEmissionIT.java`.
- **Evidence for audit submission**: schema YAML + a complete trace for one sample Run.
- **What to reject if missing**: any agent emitting partial events (only Run admission but not tool calls, for example).

### 4. Sandbox enforcement — physical subsumes logical

- **Requirement**: the physical sandbox MUST enforce a permission set ≥ any logical grant the skill config declares (Rule R-L); over-wide logical grants rejected at admission, not at execution.
- **Where to verify**: `docs/governance/sandbox-policies.yaml#financial_default` for v1.0 baseline (deny outbound internet, deny FS write outside scratch, CPU/memory/wall-clock caps); `SandboxConformanceIT.java`; per-skill override review.
- **Evidence for audit submission**: the policy YAML the agent runs under + the conformance test pass.
- **What to reject if missing**: any agent whose declared permissions exceed `financial_default` without an explicit policy override AND an architectural review.

### 5. Cost governance — per-tenant per-agent budget

- **Requirement**: per-tenant per-agent token budget enforced by model gateway; over-budget Runs `SuspendReason.RateLimited` rather than admit-and-fail.
- **Where to verify**: `docs/governance/skill-capacity.yaml` declares capacity per tenant + global; `ResilienceContract.resolve(tenant, skill)` consults the matrix (Rule R-K).
- **Evidence for audit submission**: the capacity YAML + a daily cost report showing per-(tenant, agent) attribution.
- **What to reject if missing**: any agent without a declared budget OR without daily cost reporting.

### 6. Posture-promotion discipline

- **Requirement**: agent has passed `dev` → `research` → `prod` posture promotion; `prod` startup fails closed when required config is missing (`@RequiredConfig`).
- **Where to verify**: deployment manifest declares `APP_POSTURE=prod`; `PostureBootGuard` startup check (§4 #58); posture promotion log in `docs/logs/`.
- **Evidence for audit submission**: posture log + boot-time validation output.

### 7. Model risk documentation (SR 11-7-aligned for international submissions)

- **Requirement**: each model in use has a risk-assessment artefact covering purpose, training-data scope, failure modes, monitoring plan, fallback procedure.
- **Where to verify**: `docs/model-risk/<agent-id>.md` — template lands in v1.0; one instance per production agent.
- **Evidence for audit submission**: the model-risk MD + the model-card from the provider.

## What v1.0 ships for you specifically

The v1.0 release (2026-06-30) ships these compliance-officer-facing artefacts:
- `docs/contracts/audit-trail.v1.yaml` — schema
- `docs/governance/sandbox-policies.yaml#financial_default` — financial baseline
- `docs/model-risk/_template.md` — SR 11-7-aligned model risk doc template
- `samples/finance-loan-review/COMPLIANCE-REVIEW.md` — worked example for the v1.0 reference agent showing all 7 checklist items
- `docs/compliance/regulatory-mapping.md` — maps the platform's enforced behaviour to JR/T 0223-2021 sections + 等保 2.0/3.0 controls + SOC 2 CC controls

## When you need to dig deeper

- **`/review-mode`** — when investigating an incident or sweeping a corpus for compliance drift. Loads `docs/governance/contracts/review-response.md`.
- **Phase-contract skills**: not your primary mode — you'll mostly be reviewing artefacts other personas produced. But `/verify-mode` is useful if you need to walk through how the gate enforces a specific rule.

## Where to escalate

- Sandbox policy gaps for a new agent: route to Persona-E (Architect) for an SPI-level review via `/design-mode`.
- Audit-trail format changes needed for a new regulator: open an issue tagged `regulatory` + route to architecture for a contract `*.v1.yaml` revision.
- Model-risk template insufficient for your regulator: bring to product owner for v1.x extension.

## Other personas

- `docs/onboarding/developer.md` — Persona-C (Agent Developer)
- `docs/onboarding/sre.md` — Persona-D (Production Operator)
- `docs/onboarding/architect.md` — Persona-E (Agent Architect)
