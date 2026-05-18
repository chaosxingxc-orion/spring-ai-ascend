---
level: L1
view: logical
module: agent-runtime-core
status: active
freeze_id: null
covers_views: [logical]
spans_levels: [L1]
authority: "ADR-0079 (T2.B2 engine extraction with shared runtime-core); ADR-0078 (agent-service consolidation)"
---

# agent-runtime-core — L1 architecture (2026-05-18 T2.B2 wave)

> Owner: AgentService team | Wave: W1+ | Maturity: shipped (T2.B2 extraction)

## 1. Purpose

`agent-runtime-core` hosts the **pure-Java domain entities and SPI surfaces**
shared between `agent-service` (which implements the runtime adapters) and
`agent-execution-engine` (which owns the engine envelope + executor SPIs).

It exists to break the back-dep cycle that T2.B2 surfaced: a naive engine
extraction would create the pre-Phase-C / historical `agent-execution-engine
→ agent-runtime → agent-execution-engine` loop (the `agent-runtime` module
has since been consolidated into `agent-service` per ADR-0078, with shared
kernel SPI types extracted here to `agent-runtime-core` per ADR-0079).
By hoisting `Run`, `RunContext`, `SuspendSignal`, `IdempotencyRecord`, and
the `Orchestrator`/`Checkpointer`/`RunRepository` SPI interfaces into a
shared core module, both sides depend downward only.

## 2. Contents

Authoritative enumeration of every Java surface owned by `agent-runtime-core` (post-ADR-0079, verified 2026-05-18). Memory SPI is **not** owned by this module — per ADR-0079/ADR-0082 it remains on `agent-service`.

- `runs/Run.java`, `RunMode.java`, `RunStatus.java`, `RunStateMachine.java` — Run lifecycle DFA + entity (Rule 11 + Rule 20).
- `runs/spi/RunRepository.java` — SPI interface (relocated to `runs.spi/` by the 2026-05-18 SPI integrity remediation; declared in `module-metadata.yaml#spi_packages` as `ascend.springai.service.runtime.runs.spi`).
- `orchestration/spi/Orchestrator.java`, `RunContext.java`, `SuspendSignal.java`, `Checkpointer.java`, `TraceContext.java`, `ExecutorDefinition.java` — orchestration SPI (the 6 canonical surfaces extracted per ADR-0079; declared as `ascend.springai.service.runtime.orchestration.spi`).
- `s2c/spi/S2cCallbackEnvelope.java`, `S2cCallbackTransport.java`, `S2cCallbackResponse.java` — S2C callback SPI (3 surfaces moved here in rc3 + ADR-0079; declared as `ascend.springai.service.runtime.s2c.spi`; SuspendSignal.forClientCallback(...) variant per ADR-0074 carries the checked-suspension bridge).
- `idempotency/IdempotencyRecord.java` — contract-spine entity (Rule 11 tenantId enforcement).

Total: 4 runs entities + 1 runs SPI + 6 orchestration SPI + 3 s2c SPI + 1 idempotency entity = 15 Java sources (excluding `package-info.java`). Out of scope (kept on `agent-service` deliberately): memory SPI, resilience SPI, reference adapters, HTTP edge.

## 3. Forbidden imports

Pure-Java only. No Spring, no Jackson, no Reactor, no middleware. Enforced by
ArchUnit `SpiPurityGeneralizedArchTest` (E48) in agent-service test scope.

## 4. Consumers

- `agent-service` (full runtime adapter + HTTP edge + memory.spi + resilience.spi).
- `agent-execution-engine` (engine envelope + executor SPI).
- `spring-ai-ascend-graphmemory-starter` (transitively via agent-service).
