# finance-loan-review — v1.0 reference agent

> **Target persona**: Persona-F (Enterprise Compliance / Risk Officer) — primary v1.0 gating persona.
> **Product claims served**: PC-003 (Production-grade for the AI-platform era — financial-vertical mandate for v1.0), PC-001 (Code-as-Contract).
> **Sandbox baseline**: `financial_default` from `docs/governance/sandbox-policies.yaml`.

## What this agent does

`loan-review-assistant` is the v1.0 reference financial agent. Given a `LoanApplication` (applicant identifier, requested amount, purpose, term), it produces a `LoanRiskSummary` that a credit officer uses as a recommendation **input**, not a final decision.

The agent composes three skills, each a `BUILTIN` `Skill` SPI implementation:

| Skill bean              | Calls                          | Returns                                |
|-------------------------|--------------------------------|----------------------------------------|
| `cifSkill`              | Customer Information File API  | applicant KYC + demographic profile    |
| `transactionHistorySkill` | core banking transaction API | last-90-day debits/credits + balance series |
| `creditBureauSkill`     | credit-bureau gateway          | bureau score + adverse-event flags     |

All three are **stubbed** in this sample — they return synthetic finance data, not real customer records. Wiring to production CIF/core/bureau endpoints is a per-deployment override in `application.yaml` and is gated by Persona-F sign-off.

The output `LoanRiskSummary` carries:
- aggregate risk band (`LOW | MEDIUM | HIGH | DECLINE_RECOMMENDED`)
- top three factors (signed contributions)
- a free-text rationale string, written by the LLM after redaction
- a `runId` correlation key for the audit trail (see compliance item #3)

## How to run locally

```bash
# 1. Build the parent project (skip tests for speed):
./mvnw -pl samples/finance-loan-review -am install -DskipTests

# 2. Set env vars (no real keys — stubs only):
export APP_POSTURE=dev
export LLM_PROVIDER=openai-gpt-4o-mini   # placeholder, no real key needed for stub flow

# 3. Launch:
./mvnw -pl samples/finance-loan-review spring-boot:run
```

> This sample is **not** registered in the parent reactor `<modules>` block by default — it ships as a documentation artefact for Persona-F. Add it explicitly to your local reactor when you want it built end-to-end.

## Compliance review checklist (Persona-F entry point)

Use this section to certify the agent for financial-industry production rollout. Each bullet maps to one of the 7 checklist items in `docs/onboarding/compliance-reviewer.md`; the worked example with evidence rows is in [`COMPLIANCE-REVIEW.md`](./COMPLIANCE-REVIEW.md).

- [ ] **Item 1 — Tenant isolation**: confirm `tenantId` flows from HTTP edge through every skill invocation; Postgres RLS migrations paired with any persistence the agent introduces.
- [ ] **Item 2 — Identity delegation**: confirm downstream CIF/core/bureau calls carry the credit officer's OAuth token, not a service principal.
- [ ] **Item 3 — Audit trail**: confirm every Run + skill call + model invocation emits an event matching `docs/contracts/audit-trail.v1.yaml`.
- [ ] **Item 4 — Sandbox enforcement**: confirm `application.yaml` declares `sandbox-policy: financial_default`; no widening overrides without an ADR.
- [ ] **Item 5 — Cost governance**: confirm a row in `docs/governance/skill-capacity.yaml` for each of the three skills under each tenant.
- [ ] **Item 6 — Posture-promotion discipline**: confirm `APP_POSTURE=prod` boots cleanly with `@RequiredConfig` validation passing.
- [ ] **Item 7 — Model risk documentation**: confirm `docs/model-risk/loan-review-assistant.md` exists per SR 11-7 template.

## What is intentionally NOT in this sample

- Real LLM API keys / business credentials — stubs only.
- Production CIF / core-banking / credit-bureau wiring — placeholder methods returning synthetic responses.
- Flyway migrations — this reference agent persists no state; it is request-scoped and stateless across invocations. Any deployment that adds persistence MUST add the migration + RLS pair (Rule R-J.a) and re-run Persona-F's checklist Item 1.

## Authority and provenance

- Product claims served: `product/claims.yaml` → PC-003, PC-001.
- Persona profile: `product/personas.yaml` → Persona-F.
- Onboarding flow: `docs/onboarding/compliance-reviewer.md`.
- Sandbox baseline: `docs/governance/sandbox-policies.yaml#financial_default`.
- Skill SPI: `agent-middleware/src/main/java/com/huawei/ascend/middleware/skill/spi/Skill.java` (ADR-0127).
