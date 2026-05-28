# COMPLIANCE-REVIEW — loan-review-assistant (v1.0 worked example)

> **Audience**: Persona-F (Enterprise Compliance / Risk Officer).
> **Source checklist**: `docs/onboarding/compliance-reviewer.md` items 1-7, and stage 8 of `product/journey.md`.
> **Subject agent**: `loan-review-assistant` defined in `samples/finance-loan-review/application.yaml`.
> **Posture under review**: `APP_POSTURE=prod` promotion candidate.
> **Sign-off model**: every row must be either `PASS` (evidence cited) or `BLOCK` (open finding + ADR or remediation ticket).

This document is the worked example: it walks each of the 7 checklist items, naming the concrete evidence row a reviewer would attach to a production-rollout submission for this specific agent. Item numbering matches the onboarding doc.

---

## Item 1 — Tenant isolation (storage engine)

**Requirement**: cross-tenant read / write impossible; tenant identity flows from HTTP edge to storage layer (Rule R-J.a + §4 #56).

| Evidence row              | Source                                                                                          | Status |
|---------------------------|-------------------------------------------------------------------------------------------------|--------|
| `tenantId` non-blank guard | `samples/finance-loan-review/src/main/java/com/example/finance/loanreview/domain/LoanApplication.java` constructor | PASS — `IllegalArgumentException` on blank, citing Rule R-C.c. |
| Skill-SPI tenant propagation | `SkillInvocation` constructor in `agent-middleware/.../skill/spi/SkillInvocation.java`         | PASS — Rule R-C.c enforced at SPI boundary. |
| RLS migration              | _N/A — this sample is stateless._                                                              | PASS (by absence). Any deployment that adds state MUST add the RLS migration + re-run this row. |

**Reject if**: this sample is forked into a stateful deployment without paired RLS migration. Re-route to Persona-E for `/design-mode` review.

---

## Item 2 — Identity delegation (end-user OAuth token, not service principal)

**Requirement**: downstream CIF / core / bureau calls carry the credit officer's OAuth token, NOT a service principal (§4 #56).

| Evidence row                          | Source                                                                              | Status |
|---------------------------------------|-------------------------------------------------------------------------------------|--------|
| Stub skills do not call out at all    | `samples/finance-loan-review/src/main/java/com/example/finance/loanreview/skill/*.java` | PASS — synthetic data only; no outbound HTTP. |
| Production-wiring contract            | `application.yaml#finance.cif.base-url` placeholder `stub://...`                    | PASS — must be replaced with real endpoint AND identity-propagation `WebClient` filter when promoted; re-run this row at that time. |
| Identity-propagation IT                | `agent-service/.../IamBridgeIT.java` (platform-level)                              | PASS (transitive) — covers the propagation mechanism the sample inherits. |

**Reject if**: any skill in a derived deployment is observed calling outward with a hard-coded bearer token or a `client_credentials` flow.

---

## Item 3 — Audit trail (regulatory-submission format)

**Requirement**: every Run + tool call + model invocation persisted immutably matching `docs/contracts/audit-trail.v1.yaml`, with `(runId, tenantId, userId, fromStatus→toStatus, occurredAt, evidence_ref)`.

| Evidence row                        | Source                                                                                 | Status |
|-------------------------------------|----------------------------------------------------------------------------------------|--------|
| Audit contract referenced            | `application.yaml#ascend.audit.contract: docs/contracts/audit-trail.v1.yaml`           | PASS — explicit binding. |
| Sample audit log                     | `samples/finance-loan-review/audit-trail-sample.jsonl` (emitted at first run)          | PASS — sink configured via `ascend.audit.sink`. Reviewer attaches the first 24h of output. |
| `runId` propagation into output      | `LoanRiskSummary#runId` field                                                          | PASS — every produced summary carries the correlation key. |

**Reject if**: any Run emits Run-admission events but skips per-skill or per-model-call events. Cross-check `audit-trail.v1.yaml` event-kind enumeration against the log.

---

## Item 4 — Sandbox enforcement (physical subsumes logical)

**Requirement**: physical sandbox enforces a permission set ≥ logical grants (Rule R-L); over-wide logical grants rejected at admission.

| Evidence row                          | Source                                                                                  | Status |
|---------------------------------------|-----------------------------------------------------------------------------------------|--------|
| Policy binding                         | `application.yaml#ascend.agents.loan-review-assistant.sandbox-policy: financial_default` | PASS — names the canonical baseline. |
| Baseline policy definition             | `docs/governance/sandbox-policies.yaml#financial_default`                              | PASS — deny outbound, FS write confined to `/var/agent-data/scratch`, 2 vCPU / 2 GiB / 60s. |
| Override surface empty                 | `application.yaml#ascend.sandbox.overrides: { }`                                       | PASS — no widening; if a deployment adds entries, route to ADR per `compliance-reviewer.md` checklist item 4. |
| `pii_egress_to_model: false` honored   | Skill stubs return aggregate features only (no full name / national ID / account number) | PASS — see PII discipline notes in `*Skill.java` javadoc. |

**Reject if**: `ascend.sandbox.overrides` adds keys not paired with an ADR; OR any skill returns raw PII columns that would reach the LLM unredacted.

---

## Item 5 — Cost governance (per-tenant per-agent budget)

**Requirement**: per-tenant per-agent token budget; over-budget Runs `SuspendReason.RateLimited` (Rule R-K).

| Evidence row                              | Source                                                            | Status |
|-------------------------------------------|-------------------------------------------------------------------|--------|
| Capacity rows for each skill              | `docs/governance/skill-capacity.yaml`                             | OPEN — sample does NOT add per-deployment rows; reviewer adds three rows (cifSkill, transactionHistorySkill, creditBureauSkill) per tenant during rollout. |
| `ResilienceContract.resolve` consults matrix | Platform-level (Rule R-K enforcer)                              | PASS (transitive). |
| Daily cost report                         | Deployment infrastructure (out of sample scope)                  | OPEN — required for production sign-off. |

**Reject if**: any of the three skills is unlisted in `skill-capacity.yaml` for the target tenant; OR no daily per-(tenant, agent) cost report exists.

---

## Item 6 — Posture-promotion discipline

**Requirement**: `dev` → `research` → `prod` promoted with `@RequiredConfig` validation passing on boot (§4 #58 — PostureBootGuard).

| Evidence row                       | Source                                          | Status |
|------------------------------------|-------------------------------------------------|--------|
| Posture injection point             | `application.yaml#app.posture: ${APP_POSTURE:dev}` | PASS — defaults to `dev` locally; CI sets `research`; production sets `prod`. |
| Posture log entry                   | `docs/logs/` per-deployment posture record       | OPEN — added at promotion time; reviewer attaches the link. |
| Boot-time validation output         | Container start-up log; `PostureBootGuard`       | OPEN — captured on first prod boot and attached. |

**Reject if**: `prod` start succeeds with placeholder `LLM_PROVIDER` or `stub://` URLs still wired in. `@RequiredConfig` should fail closed.

---

## Item 7 — Model risk documentation (SR 11-7-aligned)

**Requirement**: each model in use has a risk-assessment artefact covering purpose, training-data scope, failure modes, monitoring plan, fallback procedure.

| Evidence row                              | Source                                                  | Status |
|-------------------------------------------|---------------------------------------------------------|--------|
| Model risk doc                            | `docs/model-risk/loan-review-assistant.md`              | OPEN — author from `docs/model-risk/_template.md` before promotion. |
| Provider model-card                       | LLM provider's published model card                     | OPEN — attach a copy at submission time. |
| Fallback procedure                        | Documented in the model risk MD, section "Fallback"     | OPEN — covers the `risk band = HIGH on data gap` rule already implemented in the prompt. |

**Reject if**: model risk doc absent, OR fallback procedure missing, OR the prompt (in `application.yaml#ascend.agents.loan-review-assistant.prompt`) does not include the "never fabricate missing inputs" instruction (already present).

---

## Roll-up: sign-off decision

| Item | Topic                      | Status this sample      |
|------|----------------------------|-------------------------|
| 1    | Tenant isolation           | PASS                    |
| 2    | Identity delegation        | PASS (stub) / OPEN (prod wiring) |
| 3    | Audit trail                | PASS (contract bound)   |
| 4    | Sandbox enforcement        | PASS                    |
| 5    | Cost governance            | OPEN — needs per-deployment rows |
| 6    | Posture promotion          | PASS (mechanism) / OPEN (per-promotion evidence) |
| 7    | Model risk documentation   | OPEN — needs per-agent MD |

This sample agent **passes all platform-level requirements**. Production rollout of a derived deployment is BLOCKED until the OPEN rows above are filled with per-tenant evidence. Persona-F countersigns by attaching the filled-in table to the promotion ticket.

## Authority and provenance

- Source checklist: `docs/onboarding/compliance-reviewer.md`.
- Journey stage 8 (Persona-F sign-off): `product/journey.md`.
- Sandbox baseline: `docs/governance/sandbox-policies.yaml#financial_default`.
- Skill SPI: `agent-middleware/.../skill/spi/Skill.java` (ADR-0127).
- Product claims served: PC-003 (financial-vertical mandate), PC-001 (code-as-contract).
