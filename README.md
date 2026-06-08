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
- **Governance** — audit-grade evidence and posture-aware fail-closed defaults.
  Governance (gates, ADRs, enforcers) constrains the engineering **main-path**;
  the searchable **AI knowledge system** (`knowledge/`) sits outside it, kept
  honest by advisory integrity scripts rather than blocking gates.

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

The runtime ships as **4 Maven modules**, each pinned to a deployment plane so
workloads with different runtime characteristics (latency-sensitive HTTP,
throughput-heavy ML, untrusted sandbox code) never share infrastructure:

| Module | Plane | What it does |
|--------|-------|--------------|
| `agent-runtime` | Compute & Control | Run-owning runtime SDK — framework-neutral engine (`engine.spi.AgentRuntimeHandler` + `StreamAdapter`, openJiuwen adapter), `Run` lifecycle, engine dispatch, session, task-centric control, internal event queue, access (A2A), and the bootable runtime app (`app.RuntimeApp` / `LocalA2aRuntimeHost`) |
| `agent-service` | Compute & Control | Enterprise serviceization façade (skeleton) — registration/discovery driving runtime-built Agent instances via agent-runtime (ADR-0159) |
| `agent-bus` | Bus & State Hub | Cross-plane control surfaces (client→server ingress, server→client callback) + the neutral `bus.spi.engine` boundary |
| `spring-ai-ascend-dependencies` | (build-time) | Bill of Materials |

Each module declares its identity in `module-metadata.yaml`, its L1 design in
`architecture/docs/L0/ARCHITECTURE.md`, and its five DFX dimensions in `docs/dfx/<module>.yaml`.
Cross-service traffic on the Bus & State Hub plane is sliced into three
physically isolated channels — `control` (PAUSE/KILL intents, never blocked),
`data` (run payloads), `rhythm` (heartbeats). The full system boundary, the
constraint corpus, and the SPI contracts live in [architecture/docs/L0/ARCHITECTURE.md](architecture/docs/L0/ARCHITECTURE.md);
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

Full matrix: [docs/architecture/l0/cross-cutting/posture-model.md](docs/architecture/l0/cross-cutting/posture-model.md).

## Architecture facts and version scope

The project keeps two related systems separate.

The architecture fact system is organized by L0/L1/L2 and 4+1 views. It answers
what the system is, which modules and state owners are allowed, which
cross-cutting constraints cannot be violated, and which ADRs or generated facts
are authoritative. This system lives under [`architecture/`](architecture/).

The version scope system is organized from requirements to scenarios, L1 feature
use cases, L2 function points, delivery slices, and acceptance plans. It answers
what a version commits to deliver and how that scope is validated. This system
lives under [`version-scope/`](version-scope/).

Version scope documents may reference architecture facts and may request
architecture changes, but they do not directly become architecture truth.

## Reading path

Architecture truth starts in [`architecture/`](architecture/). The
[`docs/`](docs/) tree contains contracts, ADRs, governance, evidence, proposals,
and history; it can support architecture claims, but it does not replace the
architecture-of-record.

Human contributors should read:

1. [`architecture/README.md`](architecture/README.md) - authority model,
   directory roles, and edit policy.
2. [`architecture/workspace.dsl`](architecture/workspace.dsl) - canonical model
   of structure and relationships.
3. [`architecture/docs/L0/ARCHITECTURE.md`](architecture/docs/L0/ARCHITECTURE.md)
   - declarative system boundary and platform constraints.
4. [`architecture/docs/L1/README.md`](architecture/docs/L1/README.md) - module
   design index.
5. [`docs/contracts/contract-catalog.md`](docs/contracts/contract-catalog.md),
   [`docs/adr/`](docs/adr/), and [`docs/governance/`](docs/governance/) when
   cited by canonical architecture.
6. [`docs/quickstart.md`](docs/quickstart.md) for operational onboarding.

AI assistants should read:

1. [`architecture/facts/generated/`](architecture/facts/generated/) before prose
   for factual claims about code, contracts, tests, modules, or runtime config.
2. [`architecture/workspace.dsl`](architecture/workspace.dsl), then
   [`architecture/docs/L0/ARCHITECTURE.md`](architecture/docs/L0/ARCHITECTURE.md)
   and the relevant [`architecture/docs/L1/`](architecture/docs/L1/) module.
3. [`docs/contracts/`](docs/contracts/), [`docs/adr/`](docs/adr/), and
   [`docs/governance/`](docs/governance/) only as supporting authority for the
   specific claim being checked.
4. [`docs/architecture/`](docs/architecture/), [`docs/logs/`](docs/logs/),
   [`docs/reviews/`](docs/reviews/), and [`docs/archive/`](docs/archive/) only
   as non-overriding context unless a canonical architecture file explicitly
   promotes a specific artifact.

## Legacy reading path (superseded)

Whether you are a new human contributor or an AI assistant, follow this order for an unbiased architecture picture. Each step names the surface's **rhetorical stance** so you don't conflate it with another slice.

1. **`architecture/workspace.dsl`** + **`architecture/README.md`** — the architecture authority (`唯一主入口` / sole main entry; ADR-0147 + ADR-0150). Structurizr DSL workspace carrying system/container/component structure, Feature/Capability/FunctionPoint instances, dependencies, contracts, decisions, and views.
2. **`architecture/docs/L0/ARCHITECTURE.md`** (this repo root, L0 frozen) — **declarative** L0 system boundary + 65 numbered architectural constraints (§4 #1..#65). What the platform commits to structurally.
3. **`CLAUDE.md`** — **enforceable** Layer-0 governing principles (P-A..P-M) + Layer-1 engineering rules (D-/R-/G-/M- namespace). Each rule cites the §4 constraint it enforces.
4. **`architecture/docs/L1/README.md`** — L1 module design index. Pick the module you're working on; read its `.md` or 4+1 directory.
5. **`docs/contracts/contract-catalog.md`** — **runtime promise** surface (HTTP API + SPI + envelopes + OpenAPI). What the system commits to at runtime.
6. **`docs/quickstart.md`** — **operational** onboarding (boot, post `POST /v1/runs`, observe).
7. **`docs/overview.md`** — narrative tour (after-the-fact prose for non-architecture readers).

These 7 surfaces present **distinct slices**: workspace (structure) → constraints (declarative) → rules (enforceable) → L1 (module design) → contracts (runtime) → quickstart (boot) → overview (narrative). Loading all 7 in order produces a complete, unbiased architecture understanding. Loading any one in isolation produces a partial view.

## Where to go next (cross-links beyond the Reading path)

- [docs/contracts/](docs/contracts/) — full contract corpus (each contract has authority ADR + enforcer).
- [docs/adr/README.md](docs/adr/README.md) — full Architecture Decision Records corpus (the canonical count lives in docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics; currently 65 active+locked ADRs but only the baseline_metrics number is authoritative).
- [docs/governance/architecture-status.yaml](docs/governance/architecture-status.yaml) — per-capability shipped/deferred ledger.
- [docs/governance/SESSION-START-CONTEXT.md](docs/governance/SESSION-START-CONTEXT.md) — same Reading path, expressed as an always-load table for AI sessions.

## Project status & governance

**L1 module-level architecture shipped.** The W0 runtime kernel and L1 platform
composition (JWT validation, tenant cross-check, durable idempotency, posture
boot guard, the W1 run HTTP API, Code-as-Contract governance) are shipped; W2–W4
capabilities — including the Ascend/Kunpeng-optimised deployment path — remain
design contracts. Per-capability detail is the single source of truth in
[`docs/governance/architecture-status.yaml`](docs/governance/architecture-status.yaml).

A Code-as-Contract gate keeps the documentation and the code in lockstep and
fails closed on drift. Its current baseline:
**65 §4 constraints · 65 ADRs · 32 active gate rules · 102 gate self-tests**,
plus 13 Layer-0 governing principles, 53 active engineering rules, 41 enforcer
rows, and a 381-node / 573-edge architecture graph — all maintained in
[`docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`](docs/governance/architecture-status.yaml)
(the canonical source for every count); see [gate/README.md](gate/README.md) for
how it runs.

Release history and per-wave change declarations live in
[docs/logs/releases/](docs/logs/releases/).
