# spring-ai-ascend

> Enterprise agent platform on Spring AI 2.0.0-M5 + Spring Boot 4.0.5 + Java 21 — as of v2.0.0-rc7 (2026-05-18 rc6 post-response review response on rc6).

## What is this?

`spring-ai-ascend` is a self-hostable agent runtime for financial-services teams. It ships a dual-mode orchestration kernel — deterministic graph state machines and ReAct-style agent loops sharing a single interrupt primitive — with audit-grade evidence, posture-aware fail-closed defaults, and an OSS-first integration model. Build on top of it the same way you would build on Spring Boot itself: pull in the BoM, write `@Bean` overrides for the SPI surface you need, and ship.

## Status

**L1 module-level architecture shipped.** W0 runtime kernel + L1 platform composition (JWT validation, tenant claim cross-check, durable idempotency, posture boot guard, W1 run HTTP API, high-cardinality metric scrub, Rule 28 Code-as-Contract governance) shipped; W2–W4 capabilities remain design contracts.

- Latest release: [docs/releases/2026-05-18-l0-rc8-corrective.en.md](docs/releases/2026-05-18-l0-rc8-corrective.en.md) (v2.0.0-rc8; additive uplift on v2.0.0-rc7 — rc7 prose error superseded, rc7 NOT retracted). Prior waves: [docs/releases/2026-05-18-l0-rc7-corrective.en.md](docs/releases/2026-05-18-l0-rc7-corrective.en.md) (v2.0.0-rc7, historical), [docs/releases/2026-05-18-l0-rc6-post-response.en.md](docs/releases/2026-05-18-l0-rc6-post-response.en.md) (v2.0.0-rc6), [docs/releases/2026-05-18-l0-rc4-cross-constraint-response.en.md](docs/releases/2026-05-18-l0-rc4-cross-constraint-response.en.md) (v2.0.0-rc5), [docs/releases/2026-05-18-beyond-sdd-review-response.en.md](docs/releases/2026-05-18-beyond-sdd-review-response.en.md).
- Per-capability shipped/deferred ledger: [docs/governance/architecture-status.yaml](docs/governance/architecture-status.yaml)
- Architecture baseline (v2.0.0-rc8 — 2026-05-18 rc7 post-corrective review response): 65 §4 constraints · 82 ADRs · 74 active gate rules · 149 self-tests · 45 active engineering rules · 13 Layer-0 governing principles · 104 enforcer rows · 371 Maven tests GREEN · 348 architecture-graph nodes · 486 architecture-graph edges (exact graph counts remeasured at verification). Canonical structured baseline lives in [`docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`](docs/governance/architecture-status.yaml) (single source of truth; enforced by gate Rules 82, 86, 87, 88, 89 — numeric-agreement on entrypoint counts + root ARCHITECTURE.md count/path truth + fenced-tree-block ownership truth + status-yaml allowed_claim module name truth + serial/parallel gate parity + self-test harness fail-closed coverage). rc8 wave summary: (a) GraphMemoryRepository ownership corpus reconciled across 5 surfaces — `module-metadata.yaml#spi_packages` ∩ actual Java file path pinned as canonical SSOT for SPI ownership by ADR-0082 (P0-1 closure); (b) `gate/check_parallel.sh` awk fixed to use an explicit `# === END OF RULES ===` terminator + tolerant em-dash/double-dash separator; canonical Rules 86/87 separators normalised to em-dash; `parallel_summary:` trailer added showing executed-vs-defined rule count; Rule 88 enforces parity at gate time (P0-2 closure); (c) `gate/test_architecture_sync_gate.sh` TOTAL hardcoded literal removed — TOTAL now derived from `passed+failed` at runtime; fail-closed `passed != TOTAL` clause added; inline Rule 86/87 fixtures wrapped as proper `test_rule86_*()` / `test_rule87_*()` functions so the parallel orchestrator picks them up; Rule 89 enforces these three invariants (P1-1 closure); (d) `agent-runtime-core/ARCHITECTURE.md` §2 Contents rewritten to authoritatively enumerate all 15 Java surfaces (TraceContext + ExecutorDefinition + S2C SPI trio + corrected RunRepository path); `agent-service/module-metadata.yaml` description rewritten to post-ADR-0079 ownership reality (P1-2 closure); (e) ADR-0081 verification line corrected from stale `≥142/142` to ASCII `149/149` matching the rc8 baseline (P2-1 closure); (f) Rule 86 extended with a fenced-tree-block second pass that validates `<pkg>/spi/` leaves under module headers against `module-metadata.yaml#spi_packages` (closes the regex-blind-spot that allowed the original rc7 drift to escape). 2 new prevention gate rules: 88 (`serial_parallel_gate_slug_parity`) · 89 (`self_test_harness_fail_closed_coverage`) — enforcers E121-E122. 1 new ADR: ADR-0082 (GraphMemoryRepository ownership canonical + topology-truth invariant). rc7 wave summary: (a) gate Rule 54 path + self-test fixture moved to `.spi/` package home per ADR-0080 — false-negative self-test closed (P0-1 closure); (b) root `ARCHITECTURE.md` rewritten end-to-end for the 9-module post-ADR-0078/0079 topology — tree, dep diagram, §4 #1/#22/#46/#53-65 all aligned to current sub-package paths (P0-2 closure); (c) `agent-service/ARCHITECTURE.md` §2.B orchestration + runs ownership rewritten to point at `agent-runtime-core` (kernel SPI types) + `agent-execution-engine` (executor adapters per ADR-0079) (P0-3 closure); (d) `ResilienceContract` dual-surface (operation-policy `resolve(operationId)` + skill-capacity `resolve(tenant, skill)` per ADR-0070) formally reconciled in `contract-catalog.md` + ADR-0030 + ADR-0044 + Javadoc `@see` cross-refs — ADR-0081 records the supersession of the pre-ADR-0070 `(tenantId, operationId)` plans (P1-1 closure); (e) `architecture-status.yaml` 4 `allowed_claim:` spots rewritten with explicit historical markers or current module names — family-sweep surfaced a 4th spot (line 720) the reviewer missed (P1-2 closure); (f) `SuspendSignal.java:44` Javadoc + `oss-bill-of-materials.md:213` updated with `.spi.` FQNs (P2-1 closure + 1 hidden defect closure); (g) ADR-0021 + ADR-0034 doc-precision addenda — `RunRepository` 6-method + `GraphMemoryRepository` 3-method axis enumerations added. 2 new prevention gate rules: 86 (`root_architecture_count_and_path_truth`) · 87 (`status_yaml_allowed_claim_module_name_truth`) — enforcers E119-E120. 1 new ADR: ADR-0081 (ResilienceContract dual-surface reconciliation).

## Quick start

```bash
./mvnw -T 1C verify
```

`verify` (not `test`) is the canonical command — `test` skips the `*IT.java` enforcers, several of which are ship-blocking under Rule 9. `-T 1C` builds independent reactor modules in parallel; surefire runs JUnit classes concurrently inside each fork (toggle with `-DjunitParallel=false`); failsafe runs IT classes sequentially within a fork (Spring Boot 4.0.5 isn't thread-safe at `SpringApplication.run()`).

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
`docs/dfx/<module>.yaml` declares the five DFX dimensions (Rule 32).

### Five-plane topology (P-I)

Each module is pinned to exactly one of five deployment planes. Workloads
with different runtime characteristics MUST NOT share infrastructure — see
`docs/governance/principle-coverage.yaml` for the principle ↔ rule map and
`docs/governance/bus-channels.yaml` for the three-track channel isolation
that protects the Bus & State Hub plane.

### Three-track bus channel isolation (P-E / Rule 35)

Cross-service internal traffic is sliced into three physically isolated
channels declared in `docs/governance/bus-channels.yaml`:

| Channel | Cargo | Priority |
|---------|-------|----------|
| `control` | PAUSE / KILL / CANCEL intents | highest — never blocks for `data` congestion |
| `data` | run payload bodies (≤16 KiB inline cap §4 #13) | normal |
| `rhythm` | heartbeat / liveness pulses | lowest — drops oldest if saturated |

### W2.x heterogeneous engine contract (v2.0.0-rc3 headline)

The engine surface is a structured contract: `docs/contracts/engine-envelope.v1.yaml`
governs registration / matching / observability; engines fire canonical
`HookPoint` events declared in `docs/contracts/engine-hooks.v1.yaml`; the
server-to-client capability protocol uses `docs/contracts/s2c-callback.v1.yaml`;
the evolution-scope discriminator lives in
`docs/governance/evolution-scope.v1.yaml`. Authority: Rules 43–48 +
ADR-0071..0077. Release note:
[docs/releases/2026-05-16-W2x-engine-contract-wave.en.md](docs/releases/2026-05-16-W2x-engine-contract-wave.en.md).

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

- [docs/releases/](docs/releases/) — formal release notes.
- [docs/governance/architecture-status.yaml](docs/governance/architecture-status.yaml) — capability ledger.
- [gate/README.md](gate/README.md) — architecture-sync gate (current rule + self-test counts in [`docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics`](docs/governance/architecture-status.yaml); 2026-05-18 rc4 cross-constraint review response added Rules 80–83 with 8 new gate self-test cases; 2026-05-18 rc5 post-response review response added Rules 84–85 + Rule 82 numeric-agreement strengthening with 9 new gate self-test cases).
- [docs/cross-cutting/oss-bill-of-materials.md](docs/cross-cutting/oss-bill-of-materials.md) — OSS dependency policy.
