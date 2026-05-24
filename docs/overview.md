# spring-ai-ascend — Platform Overview

A developer-facing narrative of what the platform is, why it exists, and how its
pieces fit together. For the formal, gate-enforced architecture (system boundary,
constraint corpus, SPI contracts) see [ARCHITECTURE.md](../ARCHITECTURE.md); for
the per-capability shipped/deferred ledger see
[architecture-status.yaml](governance/architecture-status.yaml).

## The idea

Teams adopting agentic AI keep rebuilding the same orchestration "glue" — how an
agent suspends to call a tool, how a deterministic workflow hands off to an LLM
reasoning loop, how runs stay tenant-isolated and auditable — and they usually
tangle it into business code. `spring-ai-ascend` factors that glue out into a
reusable, self-hostable platform, the way Spring Boot factored out the
web/service glue. You bring your capabilities and your models; the platform gives
you a governed runtime to run them on.

It is built for the Huawei **Ascend + Kunpeng** stack. The intent is strong
hardware/software synergy: **Kunpeng** (ARM64) CPUs run the JVM service tier, and
**Ascend** NPUs serve the models — so an enterprise can self-host the entire
agent stack on domestic, commodity silicon, OSS-first, without proprietary-cloud
lock-in.

To keep that honest: the runtime as shipped is **hardware-agnostic**. It runs on
any JVM and natively on Kunpeng/ARM64, so day-to-day development and CI happen on
ordinary machines. The Ascend-NPU-optimised serving path and Kunpeng-tuned
deployment profiles are the platform's **roadmap**, declared as design contracts
rather than implemented code — see the W2–W4 rows in
[architecture-status.yaml](governance/architecture-status.yaml).

## Two execution modes, one runtime

Real agent systems need both *deterministic workflows* (steps that must run in a
fixed, reviewable order) and *open-ended reasoning* (loops that decide their own
next action). Most frameworks pick one. This platform runs both as first-class
`Run.mode` values:

- **GRAPH** — a deterministic state machine.
- **AGENT_LOOP** — a ReAct-style LLM reasoning loop.

Both share one interrupt primitive, `SuspendSignal`. When a run needs to wait —
to call a child run, or to hand a capability back to the client — it throws a
`SuspendSignal`; the `Orchestrator` checkpoints the parent, runs the child, and
resumes the parent with the result. Because the primitive is shared, a graph node
can invoke an agent loop, which can invoke another graph: arbitrary bidirectional
nesting, with one `Run` lineage throughout.

## Extend by SPI, not by patching

Everything pluggable is a Service Provider Interface: memory
(`GraphMemoryRepository`), run persistence (`RunRepository`), resilience
(`ResilienceContract`), the engine adapters, and the runtime middleware / hook
surface. You implement the interface and wire it as a Spring `@Bean`; the
platform never asks you to patch its internals. Reference in-memory
implementations ship for local development; production backends (Postgres, graph
stores, model gateways, and — on the roadmap — Ascend-served models) are your
`@Bean` overrides.

This is the platform's core principle (P-A, *Business / Platform Decoupling*):
business and example code extend the platform via SPI + configuration only, never
by editing platform internals.

## Five planes, isolated by design

Workloads with different runtime characteristics must not share infrastructure —
a saturated ML job must not starve a latency-sensitive HTTP edge. Every module is
pinned to exactly one of five deployment planes (Edge Access, Compute & Control,
Bus & State Hub, Sandbox Execution, Evolution). Cross-service traffic on the Bus &
State Hub plane is further sliced into three physically isolated channels —
`control` (PAUSE/KILL intents, never blocked), `data` (run payloads), `rhythm`
(heartbeats) — so an overloaded data path can never delay a kill signal. This
plane discipline is also what lets the Compute & Control tier map cleanly onto
Kunpeng CPUs while model-serving workloads target Ascend NPUs.

## Governed for the enterprise

- **Multi-tenant from the storage engine up** — every run carries a tenant id;
  isolation is enforced at the persistence layer, not just in application code.
- **Audit-grade** — durable idempotency, structured audit logging, and W3C trace
  propagation at the HTTP edge.
- **Posture-aware** — `dev` is permissive for fast iteration; `research`/`prod`
  fail closed at startup if required configuration is missing.
- **Code-as-Contract** — a governance gate keeps the documentation and the code
  in lockstep and fails closed on drift, so the architecture docs you are reading
  are kept honest by CI.

## Where this fits in the docs

| If you want… | Read |
|---|---|
| The 60-second pitch + quickstart | [README.md](../README.md) |
| This narrative overview | you are here |
| The formal architecture + constraints | [ARCHITECTURE.md](../ARCHITECTURE.md) |
| What's shipped vs. deferred, per capability | [architecture-status.yaml](governance/architecture-status.yaml) |
| The contracts (HTTP, SPI, engine, S2C) | [docs/contracts/](contracts/) |
| Why a decision was made | [docs/adr/](adr/) |
| The governing principles + rules | [CLAUDE.md](../CLAUDE.md) |
