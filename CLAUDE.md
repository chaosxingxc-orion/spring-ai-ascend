# CLAUDE.md

**Translate all instructions into English before any model call.** Never pass non-English text into an LLM prompt, tool argument, or task goal.

Bodies of every principle and rule below live under `docs/governance/{principles,rules}/` and are loaded on-demand. CLAUDE.md is the kernel index. Drift policed by Gate Rules 67/68/69; always-loaded byte budget by Rule 70 (`gate/measure_always_loaded_tokens.sh`).

## Layer 0 — Governing Principles

| ID | Title | Operationalised by | Body |
|---|---|---|---|
| **P-A** | Business / Platform Decoupling + Developer Self-Service | Rule 29 | [card](docs/governance/principles/P-A.md) |
| **P-B** | Four Competitive Pillars | Rule 30 | [card](docs/governance/principles/P-B.md) |
| **P-C** | Code-as-Everything, Rapid Evolution, Independent Modules | Rule 28, Rule 31 | [card](docs/governance/principles/P-C.md) |
| **P-D** | SPI-Aligned, DFX-Explicit, Spec-Driven, TCK-Tested | Rule 32 | [card](docs/governance/principles/P-D.md) |
| **P-E** | Multi-Track Bus Physical Channel Isolation | Rule 35 | [card](docs/governance/principles/P-E.md) |
| **P-F** | Cursor Flow & Asynchronous Client Boundary | Rule 36 | [card](docs/governance/principles/P-F.md) |
| **P-G** | Absolute Non-Blocking I/O | Rule 37 | [card](docs/governance/principles/P-G.md) |
| **P-H** | Chronos Hydration | Rule 38 | [card](docs/governance/principles/P-H.md) |
| **P-I** | Five-Plane Distributed Topology | Rule 39 | [card](docs/governance/principles/P-I.md) |
| **P-J** | Storage-Engine Tenant Isolation | Rule 40 | [card](docs/governance/principles/P-J.md) |
| **P-K** | Skill-Dimensional Resource Arbitration | Rule 41 | [card](docs/governance/principles/P-K.md) |
| **P-L** | Sandbox Permission Subsumption | Rule 42 | [card](docs/governance/principles/P-L.md) |
| **P-M** | Heterogeneous Engine Contract & Server-Sovereign Boundary | Rule 43, Rule 44, Rule 45, Rule 46, Rule 47, Rule 48 | [card](docs/governance/principles/P-M.md) |

History: [`rule-history.md`](docs/governance/rule-history.md). Mapping: [`principle-coverage.yaml`](docs/governance/principle-coverage.yaml).

## Layer 1 — Engineering Rules

### Daily principles
#### Rule 1 — Root-Cause + Strongest-Interpretation Before Plan

**Before writing any plan, fix, or feature — surface assumptions, name confusion, and state tradeoffs. Then (a) name the root cause mechanically and (b) choose the strongest valid reading of the requirement.**

Enforced by [`rule-01.md`](docs/governance/rules/rule-01.md).

---
#### Rule 2 — Simplicity & Surgical Changes

**Minimum code that solves the stated problem. Touch only what the task requires.**

Enforced by [`rule-02.md`](docs/governance/rules/rule-02.md).

---
#### Rule 3 — Pre-Commit Checklist

Before every commit, audit every touched file. Fix defects before committing — "I'll fix it later" is forbidden. **Smoke + lint** required before commits touching server entry points, runtime adapters, or dependency-wiring modules.

Enforced by [`rule-03.md`](docs/governance/rules/rule-03.md).

---
#### Rule 4 — Three-Layer Testing, With Honest Assertions

A feature is implementable only when all three layers are designed. A feature is shippable only when all three are green and Rule 9 passes.

Enforced by [`rule-04.md`](docs/governance/rules/rule-04.md).

---

### Class / resource patterns
#### Rule 5 — Concurrency / Async Resource Lifetime

**Every async or reactive resource has a lifetime bound to exactly one execution context.**

Enforced by [`rule-05.md`](docs/governance/rules/rule-05.md).

---
#### Rule 6 — Single Construction Path Per Resource Class

**For every shared-state resource, exactly one builder/factory owns construction. All consumers receive the instance by dependency injection.**

Enforced by [`rule-06.md`](docs/governance/rules/rule-06.md).

---

### Delivery process
#### Rule 9 — Self-Audit is a Ship Gate, Not a Disclosure

A self-audit with open findings in a downstream-correctness category **blocks delivery**.

Enforced by [`rule-09.md`](docs/governance/rules/rule-09.md).

---
#### Rule 10 — Posture-Aware Defaults

**Every config knob, fallback path, and persistence backend declares its default behaviour under three postures: `dev` / `research` / `prod`.**

Enforced by [`rule-10.md`](docs/governance/rules/rule-10.md).

---

### Architectural enforcement
#### Rule 11 — Contract Spine Completeness

**Every persistent record class committed under `agent-runtime-core/src/main/java/ascend/springai/service/runtime/**/*.java` (or its successor module) MUST declare a `String tenantId` component validated by `Objects.requireNonNull(tenantId, "tenantId is required")` in its compact constructor. Process-internal value objects exempt themselves with a `// scope: process-internal` reason comment. Activated 2026-05-18 (Wave 4 Track B) — trigger met by `Run` and `IdempotencyRecord` carrying tenantId.**

Enforced by [`rule-11.md`](docs/governance/rules/rule-11.md).

---
#### Rule 20 — Run State Transition Validity

**Every `Run.withStatus(newStatus)` mutation MUST call `RunStateMachine.validate(this.status, newStatus)` before constructing the updated record. Illegal transitions MUST throw `IllegalStateException`.**

Enforced by [`rule-20.md`](docs/governance/rules/rule-20.md).

---
#### Rule 21 — Tenant Propagation Purity

**No production class under `ascend.springai.service.runtime..` (main sources) may import any class under `ascend.springai.service.platform..`. The original narrow case — no import of `TenantContextHolder` — remains the specific instance most likely to be violated and is asserted independently as defence-in-depth.**

Enforced by [`rule-21.md`](docs/governance/rules/rule-21.md).

---
#### Rule 24 — RunLifecycle Re-Authorization (cancel-only at W1)

**Every `POST /v1/runs/{runId}/cancel` operation MUST re-validate `(request.tenantId == Run.tenantId)`; mismatch returns HTTP 403 `tenant_mismatch`. Idempotent terminal->terminal same-status calls return 200; illegal transitions return 409 `illegal_state_transition`. The cancel surface emits a structured `WARN+` audit log line carrying `(runId, fromStatus, toStatus, actor, occurredAt)` MDC fields. Resume and retry sub-clauses (24.d) remain deferred to the W2 async orchestrator.**

Enforced by [`rule-24.md`](docs/governance/rules/rule-24.md).

---
#### Rule 25 — Architecture-Text Truth

**Every `shipped: true` row in `docs/governance/architecture-status.yaml` MUST have a non-empty `tests:` list pointing to a real test class. Every `implementation:` path MUST exist on disk. Every prose claim in `ARCHITECTURE.md` / `agent-*/ARCHITECTURE.md` that names an enforcer ("enforced by X", "asserted by X", "tested by X") MUST be backed by X actually performing the named assertion.**

Enforced by [`rule-25.md`](docs/governance/rules/rule-25.md).

---
#### Rule 28 — Code-as-Contract (L1 Governing Rule)

**Every active normative constraint MUST be enforced by code, registered in `docs/governance/enforcers.yaml`, and reach at least one of:**

1. An **ArchUnit test** that fails when the constraint is violated.
2. A **gate-script rule** in `gate/check_architecture_sync.sh` that exits non-zero.
3. An **integration test** that asserts the observable behaviour.
4. A **schema constraint** (NOT NULL / UNIQUE / CHECK / PRIMARY KEY) at the storage layer.
5. A **compile-time check** (`@ConfigurationProperties` + `@Valid`, sealed types, package-info enforcement).

Enforced by [`rule-28.md`](docs/governance/rules/rule-28.md).

---

### Governing principles (Layer-0 enforceable expressions)
#### Rule 29 — Business/Platform Decoupling Enforcement

**Platform code MUST NOT contain business-specific customizations. Business and example code MUST extend the platform via SPI + `@ConfigurationProperties` only — never by patching `*.impl.*` or `ascend.springai.service.platform..`. The platform MUST ship a runnable quickstart (`docs/quickstart.md`) referenced from `README.md` so a developer reaches first-agent execution without platform-team intervention.**

Enforced by [`rule-29.md`](docs/governance/rules/rule-29.md).

---
#### Rule 30 — Competitive Baselines Required

**Every release MUST publish `docs/governance/competitive-baselines.yaml` declaring four pillar dimensions — `performance`, `cost`, `developer_onboarding`, `governance` — each with a named `baseline_metric` and a `current_value` (or `N/A` for not-yet-instrumented). The most recent `docs/releases/*.md` release note MUST mention all four pillar names. A regression in any `current_value` MUST be paired with a `regression_adr:` reference in the row.**

Enforced by [`rule-30.md`](docs/governance/rules/rule-30.md).

---
#### Rule 31 — Independent Module Evolution

**Every reactor module under `<module>/pom.xml` MUST own a sibling `<module>/module-metadata.yaml` declaring `module`, `kind ∈ {platform | domain | starter | bom | sample}`, `version`, and `semver_compatibility`. Each module MUST build and test in isolation via `mvn -pl <module> -am test`. Inter-module dependency direction is governed by Rule 10 (`module_dep_direction`).**

Enforced by [`rule-31.md`](docs/governance/rules/rule-31.md).

---
#### Rule 32 — SPI + DFX + TCK Co-Design

**Every module declared `kind: domain` in `module-metadata.yaml` MUST expose at least one `*.spi.*` package containing ≥ 1 public interface, listed under `spi_packages:`. Every module with `kind: platform` or `kind: domain` MUST publish a `docs/dfx/<module>.yaml` covering five DFX dimensions — `releasability`, `resilience`, `availability`, `vulnerability`, `observability` — each with a non-empty body. The sibling `<module>-tck` reactor module and conformance suite are deferred per `CLAUDE-deferred.md` 32.b / 32.c (W2 trigger).**

Enforced by [`rule-32.md`](docs/governance/rules/rule-32.md).

---

### Vibe-Coding-era structural discipline
#### Rule 33 — Layered 4+1 Discipline

**Every architecture artefact (`ARCHITECTURE.md` section, `docs/adr/*.yaml`, `docs/L2/*.md`, `docs/reviews/*.md`) MUST declare two front-matter keys: `level: L0 | L1 | L2` and `view: logical | development | process | physical | scenarios`. The root `ARCHITECTURE.md` is the canonical L0 corpus; per-module `agent-*/ARCHITECTURE.md` files are L1; deep technical designs in `docs/L2/` are L2. Each level MUST organise its content under the 4+1 view headings; L2 MAY omit views not relevant to the feature. All change proposals in `docs/reviews/` MUST declare `affects_level:` and `affects_view:`. Phase-released L0/L1 artefacts are read-only — further edits MUST flow through `docs/reviews/`.**

Enforced by [`rule-33.md`](docs/governance/rules/rule-33.md).

---
#### Rule 34 — Architecture-Graph Truth

**`docs/governance/architecture-graph.yaml` is the single machine-readable index of architectural relationships. It MUST be generated, never hand-edited, by `gate/build_architecture_graph.sh` from authoritative inputs (`docs/governance/principle-coverage.yaml`, `enforcers.yaml`, `architecture-status.yaml`, `module-metadata.yaml`, and the `docs/adr/*.yaml` corpus). The graph MUST encode at minimum these edge classes: `principle → rule`, `rule → enforcer`, `enforcer → test`, `enforcer → artefact`, `capability → test`, `module → module` (allowed / forbidden), `adr → adr` (`supersedes` / `extends` / `relates_to`), and `(level, view) → artefact`. The `supersedes` and `extends` sub-graphs MUST be DAGs. Every edge endpoint MUST resolve to a real graph node or file path. The build script MUST be idempotent — re-running on the same inputs MUST produce a byte-identical output.**

Enforced by [`rule-34.md`](docs/governance/rules/rule-34.md).

---

### L0 ironclad rules (W1.x absorption of LucioIT L0 §6/§7)
#### Rule 35 — Three-Track Channel Isolation

**Cross-service internal communication MUST be sliced into three physically isolated channels declared in `docs/governance/bus-channels.yaml`: `control` (out-of-band, highest priority), `data` (in-band, heavy-load), and `rhythm` (heartbeat/liveness). No two channels may share a `physical_channel:` identifier. The `data` channel inherits the 16 KiB inline-payload cap from §4 #13.**

Enforced by [`rule-35.md`](docs/governance/rules/rule-35.md).

---
#### Rule 36 — Cursor Flow Mandate

**Every long-horizon Runtime API endpoint MUST return a Task Cursor immediately and MUST NOT hold the client connection while work executes. The contract surface (request → cursor → polled status / SSE / Webhook) MUST be declared in `docs/contracts/openapi-v1.yaml` for at least one runs operation; new long-running endpoints MUST follow the same shape.**

Enforced by [`rule-36.md`](docs/governance/rules/rule-36.md).

---
#### Rule 37 — Reactive External I/O

**No production class under `agent-service/src/main/java/ascend/springai/service/runtime/**` may import `org.springframework.web.client.RestTemplate` or `org.springframework.jdbc.core.JdbcTemplate`. External I/O in runtime code MUST go through Reactive (`WebClient` / `R2dbcEntityTemplate`) or Virtual-Thread-backed clients.**

Enforced by [`rule-37.md`](docs/governance/rules/rule-37.md).

---
#### Rule 38 — No Thread.sleep in Business Code

**No production class under `agent-service/src/main/java/ascend/springai/service/platform/**` or `agent-service/src/main/java/ascend/springai/service/runtime/**` may invoke `Thread.sleep(...)` or `TimeUnit.<unit>.sleep(...)`. Long-horizon waits MUST be expressed as declarative suspension (`SuspendSignal`) and resumed by the bus-level Tick Engine.**

Enforced by [`rule-38.md`](docs/governance/rules/rule-38.md).

---
#### Rule 39 — Five-Plane Manifest

**Every `<module>/module-metadata.yaml` MUST declare `deployment_plane:` whose value is one of `edge | compute_control | bus_state | sandbox | evolution | none`. The plane assignment MUST match the L0 §7.1 topology — Edge Access (Agent Client SDK), Compute & Control (Runtime + Execution Engine), Bus & State Hub (Bus + Middleware persistence), Sandbox Execution (untrusted code), Evolution (Python ML). BoMs and build-time-only modules use `none`.**

Enforced by [`rule-39.md`](docs/governance/rules/rule-39.md).

---
#### Rule 40 — Storage-Engine Tenant Isolation

**Every Flyway migration that creates a table with a `tenant_id` column MUST enable Postgres Row-Level Security in the same migration (`ALTER TABLE <name> ENABLE ROW LEVEL SECURITY` plus per-tenant `CREATE POLICY`). Migrations predating this rule are listed in `gate/rls-baseline-grandfathered.txt` and MUST be retrofitted in W2.**

Enforced by [`rule-40.md`](docs/governance/rules/rule-40.md).

---
#### Rule 41 — Skill Capacity Matrix

**`docs/governance/skill-capacity.yaml` MUST exist and declare, per skill, both `capacity_per_tenant` and `global_capacity` fields plus a `queue_strategy` (`suspend` or `fail`). The runtime `ResilienceContract.resolve(tenant, skill)` MUST consult this matrix; over-cap callers are SUSPENDED, not rejected (Chronos Hydration interlock with Rule 38).**

Enforced by [`rule-41.md`](docs/governance/rules/rule-41.md).

---
#### Rule 42 — Sandbox Permission Subsumption

**`docs/governance/sandbox-policies.yaml` MUST exist with a `default_policy:` block declaring at least six required keys: `outbound_network`, `filesystem_read`, `filesystem_write`, `cpu_cap_millicores`, `memory_cap_megabytes`, `wall_clock_cap_seconds`. Enforcement-mode keys (e.g. `syscalls`) MAY be added beyond the required six. Per-skill rows MUST NOT widen the default policy beyond what the physical sandbox can enforce. The runtime `SandboxExecutor` MUST refuse a logical permission grant whose scope exceeds the declared physical limits.**

Enforced by [`rule-42.md`](docs/governance/rules/rule-42.md).

---

### W2.x Engine Contract Structural Wave (P-M)
#### Rule 43 — Engine Envelope Single Authority

**Every Run dispatch MUST go through `EngineRegistry.resolve(envelope)` (or the convenience `resolveByPayload(def)`). Pattern-matching on `ExecutorDefinition` subtypes outside `ascend.springai.service.runtime.engine.EngineRegistry` is forbidden. The envelope schema `docs/contracts/engine-envelope.v1.yaml` is the single source of truth for engine metadata; the `EngineEnvelope` Java record mirrors the schema and validates required fields (nullability, blanks) on construction. `known_engines` membership is enforced by `EngineRegistry.resolve(...)` and registry boot validation (Phase 5 R2 pilot — enforcer E84); constructor-level membership validation is deferred to Rule 48.c.**

Enforced by [`rule-43.md`](docs/governance/rules/rule-43.md).

---
#### Rule 44 — Strict Engine Matching

**A Run whose envelope declares `engine_type=X` MUST be executed only by the `ExecutorAdapter` registered under `X` in `EngineRegistry`. Mismatch raises `EngineMatchingException` and transitions the Run to FAILED with reason `engine_mismatch`. No fallback policy. No silent reinterpretation of the payload as another engine's configuration.**

Enforced by [`rule-44.md`](docs/governance/rules/rule-44.md).

---
#### Rule 45 — Runtime-Owned Middleware via Engine Hooks

**Cross-cutting policies (model gateway, tool authz, memory governance, tenant policy, quota, observability, sandbox routing, checkpoint, failure handling) MUST be expressed as `RuntimeMiddleware` listening on the canonical `HookPoint` events declared in `docs/contracts/engine-hooks.v1.yaml` (9 hooks: before/after LLM/tool/memory + before_suspension + before_resume + on_error). Engines MUST NOT depend on concrete middleware implementations. Hook ordering is declared (registration order); default failure propagation is fail-fast; `on_error` is best-effort.**

Enforced by [`rule-45.md`](docs/governance/rules/rule-45.md).

---
#### Rule 46 — S2C Callback Envelope + Lifecycle Bound

**Server-to-Client capability invocation MUST go through `S2cCallbackEnvelope` + `S2cCallbackTransport` SPI (both under `ascend.springai.service.runtime.s2c.spi` after the v2.0.0-rc3 package move per cross-constraint audit α-4 / β-2). The waiting Run MUST suspend via `SuspendSignal.forClientCallback(...)` — a checked-suspension variant introduced in v2.0.0-rc3 per cross-constraint audit α-2 / β-5 to preserve ADR-0019's compile-time-visible-suspension doctrine; the prior parallel unchecked `S2cCallbackSignal` was deleted. The orchestrator MUST mark the parent Run SUSPENDED with `SuspendReason.AwaitClientCallback`. Callbacks consume the `s2c.client.callback` skill capacity declared in `docs/governance/skill-capacity.yaml`. Client responses MUST be validated against `docs/contracts/s2c-callback.v1.yaml` (callback_id match, outcome enum membership) BEFORE resume; invalid response transitions Run to FAILED with reason `s2c_response_invalid`. Non-blocking lifecycle for the W2.x synchronous bridge is deferred to Rule 46.c (W2 async orchestrator).**

Enforced by [`rule-46.md`](docs/governance/rules/rule-46.md).

---
#### Rule 47 — Evolution Scope Default Boundary

**Every emitted `RunEvent` (when the variant ships in W2 per ADR-0022) MUST declare its `EvolutionExport` value (Java enum constants `IN_SCOPE | OUT_OF_SCOPE | OPT_IN`, mirrored by the yaml section names `in_scope | out_of_scope_default | opt_in_export` in `docs/governance/evolution-scope.v1.yaml`). Out-of-scope events MUST NOT be persisted by the evolution plane. Opt-in export requires the future `telemetry-export.v1.yaml` contract (W3 placeholder declared in `evolution-scope.v1.yaml#opt_in_export.contract_required`).**

Enforced by [`rule-47.md`](docs/governance/rules/rule-47.md).

---
#### Rule 48 — Schema-First Domain Contracts

**Every NEW domain enum or fixed-vocabulary taxonomy introduced in `ARCHITECTURE.md` (root) or `agent-*/ARCHITECTURE.md` (per-module) on or after 2026-05-16 MUST cite a yaml schema under `docs/contracts/` or `docs/governance/` within ±5 lines of the prose definition. Prose-defined enums of the shape `<TYPE> | <TYPE>` (uppercase identifiers separated by pipes) outside fenced code blocks (` ``` `) and yaml blocks are forbidden unless either (a) the section also references such a yaml schema or (b) the file is listed with a matching prefix in `gate/schema-first-grandfathered.txt`. The grandfather list is closed to new additions; every entry MUST declare a `sunset_date` (format `YYYY-MM-DD`) in the second pipe-delimited field. Gate Rule 60 fails closed once today's date exceeds any entry's sunset_date without retrofit; advancing a sunset_date forward requires an ADR cited inline in the entry description. Per-entry retrofit triggers and the default sunset schedule are documented in `CLAUDE-deferred.md` 48.b.**

Enforced by [`rule-48.md`](docs/governance/rules/rule-48.md).

---

### Token-optimization wave (2026-05-17)
#### Rule 67 — CLAUDE.md Kernel Size Bounded

**Each `#### Rule NN` section in `CLAUDE.md` MUST fit under the `kernel_cap:` declared in the matching `docs/governance/rules/rule-NN.md` card. Daily principles (Rules 1, 2, 3, 4, 9, 10) cap at 12 lines below the heading; architectural and ironclad rules (Rules 5, 6, 20–48 + 67–71) cap at 8.**

Enforced by [`rule-67.md`](docs/governance/rules/rule-67.md).

---
#### Rule 68 — CLAUDE.md Kernel Matches Card

**For every `docs/governance/rules/rule-NN.md` card, the `kernel:` scalar in YAML front-matter MUST byte-match (after whitespace normalisation) the body paragraph under `#### Rule NN` in `CLAUDE.md`. Drift in either direction fails the gate.**

Enforced by [`rule-68.md`](docs/governance/rules/rule-68.md).

---
#### Rule 69 — Every Active Rule Has a Card

**Every `#### Rule NN` heading in `CLAUDE.md` MUST have a sibling `docs/governance/rules/rule-NN.md` (zero-padded). Every card MUST either appear as a heading in `CLAUDE.md` or as a `Rule NN` reference in `docs/CLAUDE-deferred.md`. Orphan cards that satisfy neither fail the gate.**

Enforced by [`rule-69.md`](docs/governance/rules/rule-69.md).

---
#### Rule 70 — Always-Loaded Byte Budget

**Every file listed in `gate/always-loaded-budget.txt` MUST be at or below its declared byte ceiling. `gate/measure_always_loaded_tokens.sh` walks the budget file and exits non-zero on any overage. A ceiling of `0` means the file is kept on disk but excluded from the always-loaded budget (used after a file has been demoted to on-demand).**

Enforced by [`rule-70.md`](docs/governance/rules/rule-70.md).

---
#### Rule 71 — Deferred Doc Not in Always-Loaded Set

**`docs/CLAUDE-deferred.md` MUST NOT be auto-injected into the session context: no `@docs/CLAUDE-deferred.md` include directive in `CLAUDE.md`, and no `ALWAYS` / `ALWAYS-LOAD` marker on its row in `docs/governance/SESSION-START-CONTEXT.md`. Plain prose pointers ("see `docs/CLAUDE-deferred.md`") are fine — only the auto-load mechanisms are forbidden.**

Enforced by [`rule-71.md`](docs/governance/rules/rule-71.md).

---

### Gate-script efficiency wave (2026-05-17)
#### Rule 72 — Rule Duration Regression Check

**Every gate run records per-rule duration in `gate/log/runs/<sha>_<ts>/per-rule.ndjson`. After each successful run, `gate/lib/update_benchmark_baseline.sh` updates a rolling median over the last 5 runs at `gate/log/benchmarks/median.json`. Gate Rule 72 (`rule_duration_regression_check`) fails when any rule's current duration exceeds 2x its baseline median AND exceeds 200ms absolute. Bootstrap waits until 5 successful runs exist; until then Rule 72 vacuously passes.**

Enforced by [`rule-72.md`](docs/governance/rules/rule-72.md).

---
#### Rule 73 — Gate Config Well-Formed

**`gate/config.yaml` MUST validate against `gate/config.schema.yaml`. The gate fails closed on: missing required keys at any level, type mismatch, value outside declared min/max range, unknown keys (typo detection via `additionalProperties: false`), enum violation. Schema follows the wave's structural invariant: yaml → loader-validated env-vars → runtime-checked.**

Enforced by [`rule-73.md`](docs/governance/rules/rule-73.md).

---

### Linux-first dev environment (2026-05-18)
#### Rule 74 — Linux-First Dev Environment

**All shell-driven operations (gates, builds, tests, generated artefacts, `git push`) MUST be verified on Linux — native, WSL2 (preferred), or WSL1 (fallback) — before merging to `main`. Git Bash for Windows is a debugging shim, not a verification environment. `docs/governance/dev-environment.md` is the canonical setup + verification guide. Measured 2026-05-18: WSL is 6–20× faster than Git Bash, AND surfaces platform-portability bugs that Win-only verification hides.**

Enforced by [`rule-74.md`](docs/governance/rules/rule-74.md).

---

### SPI metadata integrity wave (2026-05-18)
#### Rule 75 — SPI Packages Populated

**Every `<module>/module-metadata.yaml#spi_packages` entry MUST resolve to a real directory under `<module>/src/main/java/...` containing at least one `.java` file beyond `package-info.java`. Entries whose inline comment includes BOTH `placeholder` AND an `ADR-NNNN` reference are exempt (deferred SPI work waived by an ADR).**

Enforced by [`rule-75.md`](docs/governance/rules/rule-75.md).

---
#### Rule 76 — No Split SPI Packages

**A given Java SPI package MUST be declared by exactly one Maven module's `module-metadata.yaml#spi_packages`. Two modules co-declaring the same package is a Maven split-package and fails Rule 76 — Maven test classpath, IDE ownership, and JPMS cannot reason about split-package SPI cleanly.**

Enforced by [`rule-76.md`](docs/governance/rules/rule-76.md).

---
#### Rule 77 — SPI Packages Dot-Spi Convention

**Every `spi_packages` entry MUST end in `.spi` OR contain `.spi.` (sub-packages). Operationalises Rule 32's `*.spi.*` literal convention — domain packages without a `.spi` token MUST NOT be declared as SPI.**

Enforced by [`rule-77.md`](docs/governance/rules/rule-77.md).

---
#### Rule 78 — DFX SPI Packages Match Module Metadata

**For every module with `kind ∈ {platform, domain}`, `docs/dfx/<module>.yaml` MUST declare a top-level `spi_packages:` block whose entries are an order-insensitive set match with the non-placeholder entries of `module-metadata.yaml#spi_packages`. Mis-nested (under `observability:` or other sub-keys) or omitted dfx blocks fail.**

Enforced by [`rule-78.md`](docs/governance/rules/rule-78.md).

---

## Deferred Rules

On-demand: [`docs/CLAUDE-deferred.md`](docs/CLAUDE-deferred.md). Currently deferred: Rules 7, 8, 13, 14, 15, 16, 17, 18, 19, 22, 23, 26, 27 + sub-clauses (Rules 11, 24, 29.c, 72 activated 2026-05-18 per Wave 4).
