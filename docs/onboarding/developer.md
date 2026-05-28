# Onboarding — Enterprise Agent Developer (Persona-C)

> Read time: ~5 minutes. After this, you can ship a first agent in days.

You are the **primary developer-facing persona for v1.0** of `spring-ai-ascend`. The platform was built assuming your background: ~10+ years Spring / Spring Boot / microservices; new to agent development. This doc gets you productive without you having to read 138 ADRs first.

## What this platform is, in one paragraph

`spring-ai-ascend` is an enterprise agent platform that lets you build agents the way you already build Spring services — config-driven, with Spring Boot starters, dependency-injected skills and models, ConfigurationProperties-validated config, full reuse of your existing Spring middleware (Data / Cloud / Security / Integration / Batch / Cache). The v1.0 release (2026-06-30) is hardened for **financial-industry production**: multi-tenant isolation enforced at the storage engine, identity delegation traceable end-to-end, immutable audit trail formatted for regulatory submission, sandbox enforcement that subsumes any logical permission grant.

Authoritative product framing: `product/PRODUCT.md` (auto-loaded — you've already read it implicitly). Product claims registry: `product/claims.yaml`. Your persona definition: `product/personas.yaml` → Persona-C.

## What you do not need to learn

- **You do NOT need to learn a new programming model.** Skills are Spring beans. Tools are typed POJOs. Config is `application.yaml`. DI works the way it always has.
- **You do NOT need to author JSON schemas by hand.** Tool descriptions are derived from your bean signatures at boot.
- **You do NOT need to memorize the 146 governance rules.** Phase-contract skills (`/design-mode`, `/impl-mode`, `/verify-mode`, `/commit-mode`, `/review-mode`) surface only the rules relevant to the phase you're in. Use them.

## First-agent quickstart

```bash
# 1. clone the BoM into your project
mvn dependency:get -Dartifact=com.huawei.ascend:spring-ai-ascend-bom:LATEST

# 2. add starter to your pom.xml
# <dependency>
#   <groupId>com.huawei.ascend</groupId>
#   <artifactId>spring-ai-ascend-starter</artifactId>
# </dependency>

# 3. declare your first agent in application.yaml
# ascend:
#   agents:
#     my-first-agent:
#       model: openai-gpt-5  # or local-ascend
#       skills: [bean-name-of-your-skill]
#       prompt: "you are a helpful assistant for ..."

# 4. run in dev posture
APP_POSTURE=dev ./mvnw spring-boot:run

# 5. invoke the agent
curl -X POST http://localhost:8080/v1/runs \
  -H "Content-Type: application/json" \
  -d '{"agentId": "my-first-agent", "input": "hello"}'
```

For the full step-by-step walkthrough of building the v1.0 reference agent (`loan-review-assistant`), see `samples/finance-loan-review/README.md` (lands in v1.0).

## The 12 stages of an agent's life on this platform

Read `product/journey.md` once. It walks Persona-C from discovery to evolution-loop closure in 12 stages. Stages 1-10 ship in v1.0; stages 11-12 (evolution) ship progressively in v1.5+.

## When you need to write code

Invoke `/impl-mode` (or run the `impl-mode` skill). It loads `docs/governance/contracts/engineering-implementation.md` which surfaces the rules relevant to writing production Java / yaml / Flyway / DI wiring:
- Rule R-A — Business/Platform Decoupling Enforcement (extend via SPI + ConfigurationProperties, not by patching `*.impl.*`)
- Rule R-G — Reactive External I/O (use `WebClient` / `R2dbcEntityTemplate`, ban `RestTemplate` / `JdbcTemplate`)
- Rule R-H — No `Thread.sleep` in business code (use `SuspendSignal` for long waits)
- Rule D-6 — Posture-Aware Defaults (every config knob declares behaviour for `dev` / `research` / `prod`)
- Rule D-7 — Concurrency / Async Resource Lifetime
- Rule D-8 — Single Construction Path Per Resource Class

You don't need to memorize these. The skill loads them when you enter `/impl-mode`.

## When you're about to commit

Invoke `/commit-mode`. It loads the pre-commit checklist (Rule D-3.a) and walks you through smoke + lint requirements.

## When something breaks

Invoke `/verify-mode`. It loads the integration-verification contract and `docs/runbooks/debug-first-evidence.md` (Rule D-3.b — Evidence-First Debug). You capture failing test FQN + trace ID + MDC slice + raw error message BEFORE you read any architecture doc.

## What you'll need to know about for v1.0 financial-vertical

These are the v1.0 ship-blockers. As a developer building an agent that targets the v1.0 release, your agent will:

1. **Carry tenant context.** Every Run has a `tenantId` and storage is row-level-security-isolated (Rule R-J). You don't enforce this — the framework does. Just inject the request context bean.
2. **Delegate user identity.** Every business-system call your agent makes carries the credit officer's identity (via OAuth2 token), NOT the service principal. The framework handles propagation. Just use the Spring Security context.
3. **Emit audit events.** Every Run + tool call + model invocation is persisted to the audit trail automatically (no code change from you). Schema in `docs/contracts/audit-trail.v1.yaml`.
4. **Stay inside the sandbox.** Your skill code runs under a financial sandbox policy that denies outbound internet, denies FS writes outside scratch, caps CPU/memory/wall-clock. Default config in `docs/governance/sandbox-policies.yaml#financial_default`.

If anything in steps 1-4 doesn't work for your use case, raise the issue in `/design-mode` — it loads the architecture-design contract for SPI-level changes.

## Where to find help

- **Stuck on Spring wiring**: same Spring docs you already know
- **Stuck on agent semantics**: `architecture/docs/L1/agent-service/scenarios.md`
- **Stuck on the contract format**: `docs/contracts/<contract-name>.v1.yaml`
- **Stuck on which rule applies**: invoke the right phase-contract skill — it tells you
- **Stuck on the product framing**: `product/PRODUCT.md` (you've already read it)

## What you don't need to do today

- Read CLAUDE.md kernel cover-to-cover (it's the navigation index; phase-contract skills load what you need)
- Read all 138 ADRs (the discovery index — landing in Phase A Wave 3 — gives one-liners; load full body only when relevant)
- Memorize rule numbers (rules cite themselves when they fire in the gate)

## Where this doc fits

This is the Persona-C-targeted onboarding. Other persona docs:
- `docs/onboarding/sre.md` — Persona-D (Production Operator)
- `docs/onboarding/architect.md` — Persona-E (Agent Architect)
- `docs/onboarding/compliance-reviewer.md` — Persona-F (Compliance / Risk Officer) — primary v1.0 persona
