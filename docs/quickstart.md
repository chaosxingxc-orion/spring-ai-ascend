# Quickstart — First Agent on `spring-ai-ascend`

> Goal: reach your first authenticated agent invocation without modifying any
> platform source file. Required by `CLAUDE.md` Rule R-A (Business/Platform
> Decoupling + Developer Self-Service).

This document is referenced from the root [`README.md`](../README.md) and is
gated by gate-rule `quickstart_present`.

---

## 1. Prerequisites

- JDK 21 (any vendor; tested with Temurin and OpenJDK).
- Maven 3.9+ (or use the bundled wrapper `./mvnw`).
- Optional for `prod` / `research` posture: Vault, Postgres 16, an LLM provider.

`dev` posture (the default) needs nothing else; in-memory backends are wired
automatically. Set posture via the `APP_POSTURE` env var.

## 2. Build the reactor

```bash
./mvnw -T 1C -q clean install
```

The build runs unit + ArchUnit tests for every reactor module. `-T 1C` builds
independent modules in parallel; surefire runs JUnit classes concurrently within
each fork. Add `-DjunitParallel=false` to debug intermittent test failures.

For a fast inner loop, build just one module and its dependencies:
`./mvnw -pl agent-execution-engine -am test -q` (see §7 for why this module is
especially quick).

## 3. Boot `agent-service`

```bash
./mvnw -pl agent-service spring-boot:run
```

The HTTP edge starts on port 8080.

Smoke check:

```bash
curl -s http://localhost:8080/v1/health
# {"status":"UP","sha":"...","db_ping_ns":0,"ts":"..."}
```

## 4. Drive your first Run (in-process)

In `dev` posture, the orchestration stack is fully in-memory. Drop a
`@Configuration` class into your own application that wires a custom
`GraphExecutor` and submit a `Run`:

```java
@Configuration
public class MyFirstAgent {

  @Bean
  GraphExecutor myGraphExecutor() {
    return new SequentialGraphExecutor();   // reference impl shipped at W0
  }

  @Bean
  CommandLineRunner driver(Orchestrator orchestrator) {
    return args -> {
      // Orchestrator.run(runId, tenantId, executorDefinition, initialPayload)
      // is the canonical entry point (see
      // com.huawei.ascend.engine.orchestration.spi.Orchestrator#run).
      // It synchronously creates the Run if absent, marks it RUNNING, and
      // recursively drives the suspend/resume loop until SUCCEEDED / FAILED.
      UUID runId = UUID.randomUUID();
      var def = new ExecutorDefinition.GraphDefinition(
              Map.of("start", (ctx, payload) -> "hello-" + payload),
              Map.of(),
              "start");
      Object result = orchestrator.run(runId, "tenant-demo", def, "world");
      System.out.println("Result: " + result);
    };
  }
}
```

No platform-team intervention required. The patterns this exercises:

- Extension via **SPI** (`GraphExecutor`, `Orchestrator`, `RunRepository`) —
  not by patching `*.impl.*` or `com.huawei.ascend.service.platform.**`.
- Configuration via `@Bean` and `@ConfigurationProperties` — never by source
  patches into the platform module.

## 5. Switch posture

Set `APP_POSTURE=research` or `prod` and re-run. Now:

- `IdempotencyStore` must be a durable bean (otherwise startup throws).
- `IdempotencyHeaderFilter` rejects missing `Idempotency-Key` headers on
  POST/PUT/PATCH.
- The in-memory `SyncOrchestrator` refuses to construct (use a durable
  alternative wired by your own `@Configuration`).

See [`docs/governance/posture-coverage.md`](governance/posture-coverage.md)
for the full matrix.

## 6. Where to go next

- Architecture and SPI surface: [`ARCHITECTURE.md`](../ARCHITECTURE.md).
- HTTP contract surface: [`docs/contracts/`](contracts/).
- Engineering rules you must honour: [`CLAUDE.md`](../CLAUDE.md).
- DFX coverage per module: [`docs/dfx/`](dfx/).
- Module metadata (kind / version / semver): each module's
  `module-metadata.yaml`.

## 7. When a test fails

Before you start reasoning about the failure, run the six-step **Evidence-First
Debug Sequence** in [`docs/runbooks/debug-first-evidence.md`](runbooks/debug-first-evidence.md).
Authority: CLAUDE.md Rule D-3 (Evidence-First Debug). The runbook tells you what to capture (failing
FQN → trace ID → MDC slice → raw error → transition history) BEFORE you open
`ARCHITECTURE.md`. Spec reading is allowed in step 6, after evidence is recorded.

For library-mode pure-JUnit tests (`./mvnw -pl agent-execution-engine test`),
the orchestration SPI module runs in under 2 seconds. Use this loop when you
want sub-second feedback on the SPI value-type algebra. The orchestration SPI
lives in `agent-execution-engine`; the run + idempotency entities live in
`agent-service`; the server-to-client transport SPI lives in `agent-bus`.

If anything in this quickstart requires modifying platform source to make it
work — file an issue tagged `decoupling-defect`. Rule R-A says: developers
build agents against the platform, not into the platform.
