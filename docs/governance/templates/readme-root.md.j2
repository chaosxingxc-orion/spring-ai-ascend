# spring-ai-ascend

> An open-source, enterprise-grade **agent platform** built for the Huawei **Ascend (NPU)** + **Kunpeng (CPU)** stack — on Spring AI, Spring Boot, and Java 21.

`spring-ai-ascend` lets a team stand up its own governed agent runtime the way it
would stand up a Spring Boot service: import the BoM, override the SPI beans you
care about, and ship. It is designed for self-hosting on Huawei silicon —
**Kunpeng** (ARM64) for the JVM service tier and **Ascend** NPUs for model
serving — so an enterprise can run the whole agent stack on its own hardware,
OSS-first, with no proprietary-cloud lock-in.

> **What runs today vs. what's on the roadmap.** The shipped runtime is a
> hardware-agnostic Spring AI / Java kernel — it runs on any JVM, and natively on
> Kunpeng/ARM64, so you develop and test anywhere. Ascend-NPU-optimised model
> serving and Kunpeng-tuned deployment profiles are the platform's **design
> target**, not yet shipped code. This boundary is marked honestly throughout;
> the machine-readable, per-capability ledger is
> [`docs/governance/architecture-status.yaml`](docs/governance/architecture-status.yaml).

## Why it's built this way

The platform optimises four pillars:

- **Performance** — a non-blocking run spine and parallel module build; the
  deployment target pairs Ascend NPU model serving with Kunpeng ARM throughput.
- **Cost** — OSS-first integration and self-hosting on commodity Kunpeng/Ascend
  hardware instead of metered proprietary services.
- **Developer onboarding** — extend via `@Bean` SPI overrides, exactly like
  Spring Boot; a runnable quickstart reaches a first agent run with no
  platform-team hand-holding.
- **Governance** — audit-grade evidence, posture-aware fail-closed defaults, and
  a Code-as-Contract gate that keeps the docs and the code honest.

Measured baselines: [`docs/governance/competitive-baselines.yaml`](docs/governance/competitive-baselines.yaml).

## What you can build on it

- **Dual-mode orchestration.** One runtime runs both deterministic **graph**
  state machines and ReAct-style **agent loops**, sharing a single interrupt
  primitive (`SuspendSignal`). A graph node can call an agent loop, which can
  call another graph — arbitrary bidirectional nesting, one `Run` lineage
  throughout.
- **Pluggable by SPI, not by patching.** Memory, run persistence, model gateway,
  tool authorization, and resilience are SPI surfaces you implement and wire by
  dependency injection; in-memory reference implementations ship for local dev.
- **Multi-tenant + audit-grade.** Every run carries a tenant id; storage-engine
  isolation, durable idempotency, and structured audit logging are first-class.
- **Posture-aware.** `dev` is permissive for fast iteration; `research`/`prod`
  fail closed at startup when required configuration is missing.

## Quick start

```bash
# Compile + unit + integration tests + the quality gate (the canonical command)
./mvnw -T 1C -Pquality verify
```

Use `verify`, not `test` — `test` skips the `*IT.java` integration enforcers.
`-T 1C` builds the reactor modules in parallel. Posture is selected by the
`APP_POSTURE` environment variable (`dev` / `research` / `prod`); `dev` allows
in-memory backends and only WARNs on missing config. The full
boot-and-first-run walkthrough is in [docs/quickstart.md](docs/quickstart.md).

## Architecture at a glance

The runtime is split across **8 Maven modules**, each pinned to exactly one of
five deployment planes so workloads with different runtime characteristics
(latency-sensitive HTTP, throughput-heavy ML, untrusted sandbox code) never
share infrastructure:

| Module | Plane | What it does |
|--------|-------|--------------|
| `agent-client` | Edge Access | Client SDK surface (skeleton; W3+) |
| `agent-service` | Compute & Control | HTTP edge + runtime kernel — `Run` / `RunStateMachine`, the run HTTP API, JWT/tenant/idempotency/posture, and the core SPIs |
| `agent-execution-engine` | Compute & Control | Engine adapter + orchestration SPIs, `EngineRegistry`, `EngineEnvelope` |
| `agent-middleware` | Compute & Control | `RuntimeMiddleware` SPI + hook dispatch |
| `agent-bus` | Bus & State Hub | Cross-plane control surfaces (client→server ingress, server→client callback) |
| `agent-evolve` | Evolution | ML / self-improvement pipeline (skeleton) |
| `spring-ai-ascend-dependencies` | (build-time) | Bill of Materials |
| `spring-ai-ascend-graphmemory-starter` | Bus & State Hub | Graph-memory auto-config starter |

Each module declares its identity in `module-metadata.yaml`, its L1 design in
`ARCHITECTURE.md`, and its five DFX dimensions in `docs/dfx/<module>.yaml`.
Cross-service traffic on the Bus & State Hub plane is sliced into three
physically isolated channels — `control` (PAUSE/KILL intents, never blocked),
`data` (run payloads), `rhythm` (heartbeats). The full system boundary, the
constraint corpus, and the SPI contracts live in [ARCHITECTURE.md](ARCHITECTURE.md);
the narrative tour is [docs/overview.md](docs/overview.md).

## Extending the platform

| You want to… | Do this | Entry point |
|---|---|---|
| Plug in a graph-memory backend | Implement `GraphMemoryRepository`; the starter auto-wires it | `spring-ai-ascend-graphmemory-starter` |
| Use Spring AI primitives directly | Use `ChatMemory` / `VectorStore` / `CrudRepository` without a starter | (no starter needed) |
| Pin versions and wire it yourself | Import the BoM only | `spring-ai-ascend-dependencies` |

## Posture model

| Posture | Behavior |
|---------|----------|
| `dev` (default) | Permissive — in-memory backends allowed; missing config emits WARN |
| `research` | Fail-closed — required config present or startup fails |
| `prod` | Fail-closed — same, with stricter enforcement planned |

Full matrix: [docs/cross-cutting/posture-model.md](docs/cross-cutting/posture-model.md).

## Where to go next

- [docs/overview.md](docs/overview.md) — narrative platform overview (read this after the README).
- [ARCHITECTURE.md](ARCHITECTURE.md) — system boundary, architectural constraints, SPI contracts, decision chains.
- [docs/quickstart.md](docs/quickstart.md) — first-agent-run walkthrough.
- [docs/contracts/](docs/contracts/) — HTTP API + SPI semantic contracts, engine envelope/hooks, S2C callback.
- [docs/adr/README.md](docs/adr/README.md) — Architecture Decision Records.
- [CLAUDE.md](CLAUDE.md) — Layer-0 governing principles + Layer-1 engineering rules.
- [docs/governance/architecture-status.yaml](docs/governance/architecture-status.yaml) — per-capability shipped/deferred ledger.

## Project status & governance

**L1 module-level architecture shipped.** The W0 runtime kernel and L1 platform
composition (JWT validation, tenant cross-check, durable idempotency, posture
boot guard, the W1 run HTTP API, Code-as-Contract governance) are shipped; W2–W4
capabilities — including the Ascend/Kunpeng-optimised deployment path — remain
design contracts. Per-capability detail is the single source of truth in
[`docs/governance/architecture-status.yaml`](docs/governance/architecture-status.yaml).

A Code-as-Contract gate keeps the documentation and the code in lockstep and
fails closed on drift. Its current baseline:
**65 §4 constraints · 113 ADRs · 143 active gate rules · 258 gate self-tests**,
plus 13 Layer-0 governing principles, 43 active engineering rules, 176 enforcer
rows, and a 565-node / 1005-edge architecture graph — all maintained in
[`docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`](docs/governance/architecture-status.yaml)
(the canonical source for every count); see [gate/README.md](gate/README.md) for
how it runs.

Release history and per-wave change declarations live in
[docs/logs/releases/](docs/logs/releases/).
