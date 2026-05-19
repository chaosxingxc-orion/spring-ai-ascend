# spring-ai-ascend

> Enterprise agent platform on Spring AI 2.0.0-M5 + Spring Boot 4.0.5 + Java 21 — as of v2.0.0-rc12 (2026-05-19 rc11 contract-authority-constraint review response; K-α/β/γ/δ/ε/ζ/η family taxonomy + 4 new prevention rules 101-104; ADR-0087).

## What is this?

`spring-ai-ascend` is a self-hostable agent runtime for financial-services teams. It ships a dual-mode orchestration kernel — deterministic graph state machines and ReAct-style agent loops sharing a single interrupt primitive — with audit-grade evidence, posture-aware fail-closed defaults, and an OSS-first integration model. Build on top of it the same way you would build on Spring Boot itself: pull in the BoM, write `@Bean` overrides for the SPI surface you need, and ship.

## Status

**L1 module-level architecture shipped.** W0 runtime kernel + L1 platform composition (JWT validation, tenant claim cross-check, durable idempotency, posture boot guard, W1 run HTTP API, high-cardinality metric scrub, Rule R-C.a Code-as-Contract governance) shipped; W2–W4 capabilities remain design contracts.

- Latest release: [docs/logs/releases/2026-05-19-l0-rc12-corrective.en.md](docs/logs/releases/2026-05-19-l0-rc12-corrective.en.md) (v2.0.0-rc12; L0 authority ratchet + deploy-truth + contract truth + terminal-verb scope; closes rc11 review P1-1..P1-6 + P2-1..P2-5 across K-α..K-η families; ADR-0087). Prior waves: [docs/logs/releases/2026-05-19-l0-rc11-corrective.en.md](docs/logs/releases/2026-05-19-l0-rc11-corrective.en.md) (v2.0.0-rc11; closes rc10 post-corrective review + Rule G-2.f widening + v2.0.0-rc10 (retracted)), [docs/logs/releases/2026-05-19-l0-rc9-corrective.en.md](docs/logs/releases/2026-05-19-l0-rc9-corrective.en.md) (v2.0.0-rc9; CI-green restoration), [docs/logs/releases/2026-05-18-l0-rc8-corrective.en.md](docs/logs/releases/2026-05-18-l0-rc8-corrective.en.md), [docs/logs/releases/2026-05-18-l0-rc7-corrective.en.md](docs/logs/releases/2026-05-18-l0-rc7-corrective.en.md), [docs/logs/releases/2026-05-18-l0-rc6-post-response.en.md](docs/logs/releases/2026-05-18-l0-rc6-post-response.en.md), [docs/logs/releases/2026-05-18-l0-rc4-cross-constraint-response.en.md](docs/logs/releases/2026-05-18-l0-rc4-cross-constraint-response.en.md).
- Per-capability shipped/deferred ledger: [docs/governance/architecture-status.yaml](docs/governance/architecture-status.yaml)
- Architecture baseline: **65 §4 constraints · 86 ADRs · 116 active gate rules · 180 gate self-tests** · 13 Layer-0 governing principles · 142 enforcer rows · 30 active engineering rules (D-/R-/G-/M- namespace per [ADR-0086](docs/adr/0086-rule-namespace-ratchet.yaml); Wave-6 Stream-C consolidation completed rc12) · 371 Maven tests green · 363 architecture-graph nodes / 539 edges. Canonical structured baseline lives in [`docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`](docs/governance/architecture-status.yaml) (single source of truth). Wave history lives at [`docs/logs/governance-waves.md`](docs/logs/governance-waves.md). Gate enforcement: Rule G-2.b (numeric-agreement on entrypoint counts) + Rule G-2.d (root ARCHITECTURE count/path truth) + Rule G-2.e (status-yaml allowed_claim truth) + Rule G-5.a (serial/parallel parity) + Rule G-5.b (self-test fail-closed) + Rule G-5.c (baseline-vs-manifest agreement) + Rule 101 (rc12 rule_namespace_authority_completeness).

## Quick start

```bash
./mvnw -T 1C verify
```

`verify` (not `test`) is the canonical command — `test` skips the `*IT.java` enforcers, several of which are ship-blocking under Rule D-5. `-T 1C` builds independent reactor modules in parallel; surefire runs JUnit classes concurrently inside each fork (toggle with `-DjunitParallel=false`); failsafe runs IT classes sequentially within a fork (Spring Boot 4.0.5 isn't thread-safe at `SpringApplication.run()`).

Posture is selected by the `APP_POSTURE` environment variable (`dev` / `research` / `prod`). `dev` is permissive (in-memory backends allowed, missing config emits WARN); `research` and `prod` fail-closed at startup if required config is missing.

## Modules — six-team-facing-modules materialization (Phase C + engine extraction complete)

The L0 architecture (CLAUDE.md P-A..P-M) declares **six team-facing modules**.
Phase C (ADR-0078, 2026-05-18) consolidated the prior `agent-platform` + `agent-runtime` into `agent-service`. Engine extraction T2.B2 (ADR-0079, 2026-05-18) introduced a shared `agent-runtime-core` module hosting `Run` / `RunContext` / `SuspendSignal` / `ExecutorDefinition` / S2C SPI types, and moved the engine SPI + `EngineRegistry` + `EngineEnvelope` into `agent-execution-engine` (resolving the prior back-dep cycle). The reactor now ships **9 Maven modules** (6 team-facing + agent-runtime-core shared kernel + BoM + graphmemory starter).

| Module | Plane (P-I) | Owner team | Maturity today |
|--------|-------------|-----------|----------------|
| `agent-client` | Edge Access | AgentClient | skeleton (SDK; W3+ per ADR-0049) |
| `agent-service` | Compute & Control | AgentService | shipped — HTTP edge (`service.platform.*`) + cognitive runtime kernel (`service.runtime.*`) per Phase C (ADR-0078) |
| `agent-runtime-core` | Compute & Control | AgentService | shipped — shared kernel types (Run, RunContext, SuspendSignal, ExecutorDefinition, S2C SPI) per ADR-0079 |
| `agent-middleware` | Compute & Control | Middleware | shipped — RuntimeMiddleware SPI + HookDispatcher extracted from runtime per ADR-0073 |
| `agent-execution-engine` | Compute & Control | AgentExecutionEngine | shipped — engine SPI (ExecutorAdapter, GraphExecutor, AgentLoopExecutor, EngineHookSurface) + EngineRegistry + EngineEnvelope extracted per ADR-0079; reference adapters remain in `agent-service.runtime` |
| `agent-bus` | Bus & State Hub | AgentBus | skeleton (contracts only; W2 impl per ADR-0050) |
| `agent-evolve` | Evolution | AgentEvolve | skeleton (Python ML pipeline; Java adapter deferred) |
| `spring-ai-ascend-dependencies` | (build-time) | platform | shipped (BoM) |
| `spring-ai-ascend-graphmemory-starter` | Bus & State Hub | AgentBus | shipped (graphmemory SPI scaffold; ADR-0034) |

Per-module `module-metadata.yaml` is the authoritative identity + dependency
declaration. Per-module `ARCHITECTURE.md` carries the L1 view. Per-module
`docs/dfx/<module>.yaml` declares the five DFX dimensions (Rule R-D sub-clause .a).

### Five-plane topology (P-I)

Each module is pinned to exactly one of five deployment planes. Workloads
with different runtime characteristics MUST NOT share infrastructure — see
`docs/governance/principle-coverage.yaml` for the principle ↔ rule map and
`docs/governance/bus-channels.yaml` for the three-track channel isolation
that protects the Bus & State Hub plane.

### Three-track bus channel isolation (P-E / Rule R-E)

Cross-service internal traffic is sliced into three physically isolated
channels declared in `docs/governance/bus-channels.yaml`:

| Channel | Cargo | Priority |
|---------|-------|----------|
| `control` | PAUSE / KILL / CANCEL intents | highest — never blocks for `data` congestion |
| `data` | run payload bodies (≤16 KiB inline cap §4 #13) | normal |
| `rhythm` | heartbeat / liveness pulses | lowest — drops oldest if saturated |

### W2.x heterogeneous engine contract

The engine surface is a structured contract: `docs/contracts/engine-envelope.v1.yaml`
governs registration / matching / observability; engines fire canonical
`HookPoint` events declared in `docs/contracts/engine-hooks.v1.yaml`; the
server-to-client capability protocol uses `docs/contracts/s2c-callback.v1.yaml`;
the evolution-scope discriminator lives in
`docs/governance/evolution-scope.v1.yaml`. Authority: Rules 43–48 +
ADR-0071..0077. Release note:
[docs/logs/releases/2026-05-16-W2x-engine-contract-wave.en.md](docs/logs/releases/2026-05-16-W2x-engine-contract-wave.en.md).

## Integration paths

| Path | When to use | Entry point |
|------|-------------|-------------|
| Drop-in `@Bean` override | You implement `GraphMemoryRepository`; starter auto-config wires it | `spring-ai-ascend-graphmemory-starter` |
| Direct Spring AI / Spring Data | Use `ChatMemory`, `VectorStore`, `CrudRepository` directly without starters | No starter needed |
| BoM import only | Pin SDK + OSS versions; manage wiring yourself | `spring-ai-ascend-dependencies` BoM |

## Runtime model

`Run.mode` discriminates `GRAPH` (deterministic state machine) from `AGENT_LOOP` (ReAct-style LLM reasoning). Both modes share one interrupt primitive — `SuspendSignal` — which the `Orchestrator` catches to checkpoint the parent, dispatch a child Run, and resume the parent with the child's result. Three-level bidirectional nesting (graph → agent-loop → graph) is proved by `NestedDualModeIT`.

The full architectural constraint set (§4 #1–#63) and the deferred-capability roadmap (W1–W4) live in [ARCHITECTURE.md](ARCHITECTURE.md) and [docs/governance/architecture-status.yaml](docs/governance/architecture-status.yaml). They are not duplicated here.

## Posture model

| Posture | Behavior |
|---------|----------|
| `dev` (default) | Permissive — in-memory backends allowed; missing config emits WARN, not exception |
| `research` | Fail-closed — required config present or `IllegalStateException`; durable persistence expected |
| `prod` | Fail-closed — same as research; stricter enforcement planned for W2 |

Full matrix: [docs/cross-cutting/posture-model.md](docs/cross-cutting/posture-model.md).

## Reading order

1. **README.md** — you are here.
2. **[docs/governance/architecture-status.yaml](docs/governance/architecture-status.yaml)** — per-capability shipped/deferred ledger (the canonical machine-readable index; an earlier README incorrectly linked to a non-existent `docs/STATE.md`).
3. **[ARCHITECTURE.md](ARCHITECTURE.md)** — system boundary, §4 constraints, SPI contracts, decision chains.
4. **[docs/contracts/](docs/contracts/)** — HTTP API contracts, SPI semantic contracts, pinned OpenAPI snapshot, engine envelope, engine hooks, S2C callback.
5. **[docs/adr/README.md](docs/adr/README.md)** — Architecture Decision Records (ADR-0001 … ADR-0080).
6. **[CLAUDE.md](CLAUDE.md)** — Layer-0 governing principles + Layer-1 engineering rules. Current rule + principle counts live in [`docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`](docs/governance/architecture-status.yaml). See also [docs/quickstart.md](docs/quickstart.md).
7. **[docs/CLAUDE-deferred.md](docs/CLAUDE-deferred.md)** — every staged rule + sub-clause with its explicit re-introduction trigger.
8. **[docs/governance/SESSION-START-CONTEXT.md](docs/governance/SESSION-START-CONTEXT.md)** — machine-readable entrypoint context (graph traversal cues).
9. **[docs/governance/principle-coverage.yaml](docs/governance/principle-coverage.yaml)** — Layer-0 principle ↔ Layer-1 rule traceability.
10. **[docs/governance/retracted-tags.txt](docs/governance/retracted-tags.txt)** — released tags retracted by superseding fixes.
11. **[docs/governance/competitive-baselines.yaml](docs/governance/competitive-baselines.yaml)** — P-B measurement baseline (Performance / Cost / Developer Onboarding / Governance).

## See also

- [docs/logs/releases/](docs/logs/releases/) — formal release notes.
- [docs/governance/architecture-status.yaml](docs/governance/architecture-status.yaml) — capability ledger.
- [gate/README.md](gate/README.md) — architecture-sync gate (current rule + self-test counts in [`docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`](docs/governance/architecture-status.yaml); 2026-05-18 rc4 cross-constraint review response added Rules 80–83 with 8 new gate self-test cases; 2026-05-18 rc5 post-response review response added Rules 84–85 + Rule G-2 sub-clause .b numeric-agreement strengthening with 9 new gate self-test cases).
- [docs/cross-cutting/oss-bill-of-materials.md](docs/cross-cutting/oss-bill-of-materials.md) — OSS dependency policy.
