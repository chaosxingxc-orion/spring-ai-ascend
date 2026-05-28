# Onboarding — Enterprise SRE / Production Operator (Persona-D)

> Read time: ~5 minutes. After this, you can promote an agent through the dev/research/prod postures and operate it at 10K-card-class scale.

You are the **production-operator persona** for `spring-ai-ascend`. The platform was built assuming your background: ~10+ years operating Spring services at scale; comfortable with reactive runtimes, multi-tenant SLAs, capacity planning, and incident response. You'll find that agent workloads have new failure modes (non-deterministic outputs, long-horizon suspension, cost-per-Run variance) but the operational primitives map cleanly to what you already know.

## What this platform is, from your perspective

`spring-ai-ascend` is a Spring-native enterprise agent runtime engineered for production: reactive non-blocking I/O, storage-engine-enforced multi-tenancy (Postgres RLS), idempotent admission, posture-aware defaults, capability-scoped agent identity, sandbox subsumption, per-tenant cost/capacity governance, audit-grade observability. For v1.0 (2026-06-30) the operational target is **financial-industry production** — every dimension above is hardened to a regulatory-submission grade.

Authoritative product framing: `product/PRODUCT.md`. Your persona: `product/personas.yaml` → Persona-D. Your primary product claim: **PC-003** (Production-grade for the AI-platform era).

## The three postures — your most important control

Every workload runs under one of three postures, declared by environment variable `APP_POSTURE`:

| Posture | Behaviour | Use case |
|---|---|---|
| `dev` | Permissive defaults; in-memory adapters; lenient validation; no audit emission | Local development and unit tests |
| `research` | Production-shape adapters; strict validation; tenant RLS enforced; cost budget enforced; audit emitted | Staging, integration testing, performance benchmarking |
| `prod` | All `research` constraints + fail-closed on missing required config (`@RequiredConfig` validates at startup) + immutable audit storage backend | Production rollout |

Rule D-6 enforces that every config knob, fallback path, and persistence backend declares default behaviour under all three postures. Rule §4 #58 (`PostureBootGuard` + `@RequiredConfig`) ensures `prod` startup fails closed when required config is missing.

**Operator action**: workloads promote by environment variable. Same artifact, three operational modes. No re-packaging.

## What you'll be observing — the platform-axis SLI/SLO set

For v1.0, the **platform-axis observability** is shipped functional. Three other axes (business / model / cost) ship progressively in v1.x weekly cadence.

| SLI | Source | Default SLO target |
|---|---|---|
| Run admission p95 latency | `POST /v1/runs` controller | < 500ms |
| Run lifecycle transition latency (admit → complete) | RunStateMachine spans | per-engine; declared in `competitive-baselines.yaml#performance` |
| Tool-call p95 latency | OTel span per skill invocation | per-skill; declared in skill metadata |
| Model-invocation p95 latency | Model gateway span | per-provider |
| Per-tenant request rate | Capacity matrix counters | declared in `docs/governance/skill-capacity.yaml` |
| Tenant isolation breaches | RLS error rate | **zero tolerance** — any cross-tenant read attempt is a P0 |
| Sandbox policy violations | `SandboxExecutor` rejection counter | low-rate; spikes indicate adversarial input |
| Audit-trail gaps | RunEvent emission delta | zero — every Run event MUST land in audit storage |

All spans are OpenTelemetry-compatible. You can use Grafana / Tempo / Jaeger / OpenObserve / Phoenix — anything that ingests OTel.

## What's new about operating an agent runtime

Three operational concerns that microservices don't have:

1. **Long-horizon suspension.** A Run can suspend for minutes-to-days waiting on tool, human, or external event. Server thread MUST NOT block during this time (Rule R-H bans `Thread.sleep` in business code; Rule R-F requires Cursor Flow on long-horizon endpoints). The runtime persists suspended Runs and resumes via the bus. Your capacity model: count **active executing Runs**, not total Runs in the system.
2. **Cost-per-Run variance.** Token cost depends on input + model + tool calls + retries. Same workload can swing 10× cost between Runs. Per-tenant per-agent token budgets enforced by model gateway (`docs/governance/skill-capacity.yaml` capacity matrix). Daily roll-up reports per (tenant, agent) pair.
3. **Non-deterministic outputs.** Idempotent admission via `IdempotencyStore` SPI ensures duplicate requests don't double-execute. Retry semantics are at-most-once by default; opt-in at-least-once requires explicit annotation.

## Capacity governance — `skill-capacity.yaml`

`docs/governance/skill-capacity.yaml` declares per-skill capacity per tenant + global. Rule R-K enforces that `ResilienceContract.resolve(tenant, skill)` consults this matrix. Over-capacity resolution returns `SkillResolution.reject(SuspendReason.RateLimited)` rather than admit-or-fail. **Operator action**: edit the matrix per-tenant to allocate skill capacity; gate enforces it.

## Sandbox policies — `sandbox-policies.yaml`

`docs/governance/sandbox-policies.yaml` declares `default_policy:` (six required keys: `outbound_network`, `filesystem_read`, `filesystem_write`, `cpu_cap_millicores`, `memory_cap_megabytes`, `wall_clock_cap_seconds`) plus per-skill overrides. For v1.0 financial workloads, `financial_default` policy ships — deny outbound internet, deny FS writes outside scratch, cap CPU/memory/wall-clock to conservative values. Rule R-L enforces logical permission ⊆ physical sandbox.

## When you're operating: phase-contract skills you'll use

- **`/verify-mode`** — when something is wrong. Loads `docs/governance/contracts/integration-verification.md` + `docs/runbooks/debug-first-evidence.md`. Rule D-3.b mandates evidence-first debug: failing test FQN + trace ID + MDC slice + raw error message BEFORE consulting ADRs.
- **`/commit-mode`** — when pushing operator-side config changes (capacity matrix, sandbox policies, deployment manifests). Loads the pre-commit checklist + lockstep-baseline discipline.

## v1.0 financial-vertical operational ship-blockers

For 2026-06-30 release, the v1.0 features your role depends on:

1. **Immutable audit trail backend wired** — append-only Postgres table + checksum chain; schema in `docs/contracts/audit-trail.v1.yaml`.
2. **Kunpeng+Ascend performance baseline declared** — p95 latency + throughput targets in `competitive-baselines.yaml#performance` for one reference workload.
3. **Cost governance v1** — per-tenant per-agent token budget enforced; daily roll-up report generation cron.
4. **中台-mode reference deployment manifest** — `deploy/middle-office-reference/`; one Kubernetes deployment showing a bank's central platform team operating the runtime.
5. **Tenant isolation IT pass on RLS** — `agent-service/.../TenantIsolationIT.java` must be green; cross-tenant cancel/read returns 404 not 403 (W0 narrow direction per ADR-0108).

## Other personas

- `docs/onboarding/developer.md` — Persona-C (Agent Developer)
- `docs/onboarding/architect.md` — Persona-E (Agent Architect)
- `docs/onboarding/compliance-reviewer.md` — Persona-F (Compliance / Risk Officer) — primary v1.0 persona
