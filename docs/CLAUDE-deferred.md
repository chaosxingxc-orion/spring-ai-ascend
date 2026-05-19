# CLAUDE-deferred.md

Rules deferred from `CLAUDE.md`. Each has an explicit re-introduction trigger.
Do not activate these rules before the trigger condition is met.

---

## Rule 7 — Resilience Must Not Mask Signals

**Re-introduction trigger**: first soft-fallback path committed (target: W2 LLM gateway).

**Rule**: Every silent-degradation path emits a loud, structured signal. Required for each fallback branch:

1. **Countable**: named metric counter (e.g. `*_fallback_total`).
2. **Attributable**: `WARNING+` log with run id and trigger reason at the branch entry.
3. **Inspectable**: run metadata carries a `fallback_events` list. Non-empty fallback_events = not "successful".
4. **Gate-asserted**: operator-shape gate asserts fallback counts are zero.

---

## Rule 8 — Operator-Shape Readiness Gate

**Re-introduction trigger**: first shippable jar with a real external dependency (LLM provider
or database) booting under a process supervisor.

**Rule**: No artifact ships until it runs in the exact operator shape downstream will use.
Green unit tests, green Layer 3 E2E, and a clean self-audit do not authorize delivery alone.

Before any artifact leaves the repo, the following must pass in a clean environment:

1. **Long-lived process** — managed process supervisor (systemd / docker / kubernetes); not a foreground shell run.
2. **Real external dependencies** — real LLM provider, real database — pointing at what downstream will use.
3. **Sequential real-dependency runs (N≥3)** — three back-to-back invocations; each reaches terminal success in ≤ `2 × observed_p95`; fallback count `== 0`.
4. **Cross-context resource stability** — runs 2 and 3 reuse the same client instances as run 1 (Rule D-7 stress test).
5. **Lifecycle observability** — each run reports a non-null current stage within 30 s; finished-at populated on terminal.
6. **Cancellation round-trip** — cancel on live run → 200 + terminal; cancel on unknown id → 404.

Gate pass recorded in `docs/delivery/<date>-<sha>.md`. Unrecorded ≠ passed.

---

## Rule 13 — P1 Cost-of-Use Constraints

**Re-introduction trigger**: first context-cache, cost-accounting, or small/large-model handoff capability committed (target: W3).

**Rule (draft)**: Every capability that invokes an LLM call declares its cost profile and cache eligibility. A gate check verifies that:
- Any capability marked `cache_eligible=true` is tested against a real provider cache-hit scenario (not mocked).
- Token budgets are declared in capability metadata; a gate asserts actual usage ≤ declared budget × 1.2.

This rule converts P1 roadmap intent into a pre-commit enforcement path.

---

## Rule 14 — P3 Self-Evolution Constraints

**Re-introduction trigger**: first skill-registry, memory-compression, or knowledge-dedup capability committed (target: W3).

**Rule (draft)**: Every self-modifying capability (skill updates, memory compression, knowledge-source dedup) declares:
- An immutability invariant for its SPI interface (signature frozen unless a new major version is declared).
- A quality floor: recall-at-K ≥ baseline across the regression corpus.
- Monotonicity: updated memory or skills may not reduce retrieval quality below the unmodified baseline.

This rule converts P3 roadmap intent into a pre-commit enforcement path.

---

## Rule 15 — Streamed Handoff Mode Conformance

**Re-introduction trigger**: first `Flux<T>` / SSE return from `Orchestrator` or any northbound controller (target: W2).

**Rule**: Every streaming surface MUST declare and enforce:
- (a) Backpressure strategy (bounded buffer, drop, or error on overflow).
- (b) Cancellation propagation: caller cancel → `RunStatus.CANCELLED` set on the Run.
- (c) Heartbeat cadence ≤ 30 s — positive liveness signal, not absence of error.
- (d) Terminal frame carries `runId` + final `RunStatus` + error payload if applicable.
- (e) Typed progress event shape (`progress | cost | tool_call | partial_output | terminal`) — no raw `Object`.

Composes with: ARCHITECTURE.md §4 #11 (`streamed_handoff_mode`, `orchestrator_cancellation_handshake`).

---

## Rule 16 — Cognitive Resource Arbitration

**Re-introduction trigger**: first `ResilienceContract` consumer that invokes an external tool or skill (not just LLM) (target: W2).

**Rule**: Every skill invocation MUST declare:
- (a) `operationId` in `skill:<name>` namespace.
- (b) Tenant-scoped quota key (prevents one tenant from exhausting shared capacity).
- (c) Global skill capacity key (caps concurrent invocations platform-wide).
- (d) Saturation policy: skill-full suspends the Run (`SUSPENDED + suspendedAt + reason=RateLimited`), not fails it.
- (e) Call-tree budget: parent Run's remaining token/cost budget is propagated through `RunContext` to child Runs.

Composes with: ARCHITECTURE.md §4 #12 (`skill_capacity_matrix`, `call_tree_budget_propagation`); Rule 13 (P1 cost-of-use).

---

## Rule 17 — Degradation Authority and Resume Re-Authorization

**Re-introduction trigger**: first soft-fallback path committed (composes with Rule 7 trigger — W2 LLM gateway).

**Rule**:
- **Degradation authority**: S-side (system) may substitute means only (alternative tool/model/provider) without C-side (caller) approval. Ends-modification (changing the goal, expanding scope, dropping a required action) is surfaced as a typed `BusinessDegradationRequest` to C-side for explicit approval before proceeding.
- **Resume re-authorization**: every resume on a `SUSPENDED` Run MUST re-validate `(request.tenantId == Run.tenantId)`; mismatch returns HTTP 403. Actor identity at resume is captured in an audit envelope (who resumed, when, from which request).

Composes with: ARCHITECTURE.md §4 #14 (`resume_reauthorization_check`, `suspend_reason_taxonomy`); Rule 7 (resilience signal masking).

---

## Rule 18 — Eval Harness Gate

**Re-introduction trigger**: first shipped capability with a golden corpus + LLM-as-judge evaluator committed (target: W4).

**Rule**: Every capability with a `corpus.jsonl` entry under `docs/eval/` MUST pass its declared regression thresholds before merge:

1. **Corpus run**: the eval runner re-runs every input in `docs/eval/<capability>/corpus.jsonl` against the current model + prompt.
2. **Judge evaluation**: the LLM-as-judge (configured model, versioned prompt template) scores each output against the expected.
3. **Threshold gate**: every metric named in `docs/eval/<capability>/thresholds.yaml` must be ≥ its declared threshold; any metric below threshold blocks the merge.
4. **Baseline protection**: a merge that lowers a threshold value without a corresponding corpus expansion MUST include an explicit justification comment in the PR description.

Composes with: ARCHITECTURE.md §4 #18 (`eval_harness_contract`).

---

## Rule 22 — PayloadCodec Discipline [Deferred to W2]

**Re-introduction trigger**: first `Checkpointer` implementation that persists bytes to a durable store (target: W2 Postgres `PostgresCheckpointer`).

**Rule**: Every payload type that crosses a suspend/resume JVM boundary MUST have a registered `PayloadCodec<T>` with a stable `codecId` and `typeRef`. `RawPayload(Object)` MUST be rejected at the persistence boundary; it is valid only within a single in-process JVM execution context. `EncodedPayload(byte[], String codecId, String typeRef)` is the mandatory persistence wire format.

Composes with: ARCHITECTURE.md §4 #21 (`payload_codec_spi`); ADR-0022.

---

## Rule 23 — Suspension Write Atomicity Enforcement [Deferred to W2]

**Re-introduction trigger**: first W2+ `Orchestrator` implementation that performs both a `RunRepository.save(suspended)` and a `Checkpointer.save(payload)` for suspension.

**Rule**: Any W2+ Orchestrator that performs the suspension pair MUST:
1. Document its atomicity strategy in Javadoc on the suspend-transition method.
2. Wrap both writes in a single Postgres `@Transactional` block (same `DataSource`), OR use the transactional outbox pattern (ADR-0007) for non-DB Checkpointer backends.
3. Enforce the contract with an integration test that kills the JVM mid-write and asserts post-restart consistency (e.g., via `ProcessBuilder` + DB state check).

An implementation that cannot demonstrate this contract is a ship-blocking defect per Rule D-5 (category: "Run lifecycle — checkpoint/resume atomicity").

Composes with: ARCHITECTURE.md §4 #23 (`suspension_write_atomicity_contract`); ADR-0024; ADR-0007.

---

## Rule 19 — Runtime Hook Conformance

**Re-introduction trigger**: first W2 LLM gateway capability committed (first `ChatClient` call in production code path).

**Rule**: Every LLM invocation, tool call, and agent lifecycle transition MUST be invoked through `HookChain.invoke(...)`, not via a direct provider client call:

1. **No bypass**: an ArchUnit test (`HookChainConformanceTest`) asserts that no class outside the `hookchain` package calls `ChatClient.call(...)`, tool-execution methods, or `AgentLoopExecutor.reason(...)` directly. A violation is a compile-gate failure.
2. **Hook failure safety**: a hook that throws a checked exception MUST be caught; failure is logged at `WARNING+` with `runId` + hook class name; invocation continues. An unchecked exception propagates and fails the invocation — hooks are responsible for safety.
3. **Hook ordering**: hooks execute in `@Order` registration sequence; lower order = earlier execution. `BEFORE_*` hooks run ascending; `AFTER_*` hooks run ascending in the same order (not reversed).
4. **Gate-asserted**: the operator-shape gate asserts that at least one hook (PII filter or token counter) is registered and fires on every real-provider invocation.

Composes with: ARCHITECTURE.md §4 #16 (`runtime_hook_spi`).

---

## Rule 26 — Skill Lifecycle Conformance [Deferred to W2]

**Re-introduction trigger**: first `Skill` SPI implementation committed (target: W2).

**Rule**: Every `Skill` implementation MUST honour the complete lifecycle contract defined in ADR-0030:

1. **Mandatory init**: `Skill.init(SkillContext)` MUST be called before `execute`. An ArchUnit test (`SkillLifecycleConformanceTest`) asserts no class outside `skill.spi.*` calls `execute()` without a preceding `init()` in the same execution context.
2. **Suspend/resume pair**: when a Run is suspended, `Skill.suspend(SkillContext) → SkillResumeToken` MUST be called on any Skill holding external resources (DB connections, file handles, HTTP sessions). Resources must be released at `suspend` and reacquired at `resume`.
3. **Mandatory teardown**: `Skill.teardown(SkillContext)` MUST be called on all code paths — normal completion, exception, and cancellation. Implement using try-finally in the execution harness.
4. **Cost receipt**: every `Skill.execute` MUST return a `SkillCostReceipt` capturing `inputTokens`, `outputTokens`, `wallClockMs`, `cpuMillis`, and optionally `currencyCode`/`cost`. The harness aggregates receipts and attaches them to the Run.

Composes with: ARCHITECTURE.md §4 #27 (`skill_spi_lifecycle_resource_matrix`); ADR-0030; Rule 13 (P1 cost-of-use).

---

## Rule 27 — Untrusted Skill Sandbox Mandate [Deferred to W3]

**Re-introduction trigger**: first `UNTRUSTED`-tier `Skill` implementation committed in research or prod posture (target: W3).

**Rule**: In `research` or `prod` posture, any `Skill` with `SkillTrustTier.UNTRUSTED` MUST be routed through a non-`NoOpSandboxExecutor` implementation:

1. **Startup gate**: on application startup in `research`/`prod` posture, if any registered Skill carries `UNTRUSTED` trust tier, the container MUST assert that a non-NoOp `SandboxExecutor` bean is present. Missing sandbox → startup failure with clear error message referencing ADR-0030.
2. **Posture model**: `dev` posture emits a `[WARN]` log when `UNTRUSTED` skills execute without a real sandbox (allows iteration without Docker/GraalVM setup). `research`/`prod` posture fails-closed per Rule D-6.
3. **VETTED bypass**: `SkillTrustTier.VETTED` skills may route through `NoOpSandboxExecutor` in all postures. Trust-tier assignment is declared in `Skill.metadata()` and is immutable at runtime.

Composes with: ARCHITECTURE.md §4 #27 (`skill_spi_lifecycle_resource_matrix`); ADR-0030; ADR-0018 (`SandboxExecutor` SPI); Rule D-6 (posture-aware defaults).

---

## Rule R-B.b — Baseline Regression → ADR Pairing [Deferred to W1]

**Re-introduction trigger**: first revision of `docs/governance/competitive-baselines.yaml` that lowers a `current_value` vs the prior git revision (target: W1, when at least one dimension is measurable).

**Rule (draft)**: A git-diff gate rule MUST compare the previous and current revision of `docs/governance/competitive-baselines.yaml`. Any dimension whose `current_value` regresses MUST carry a `regression_adr: ADR-NNNN` reference in the same row pointing to a justification ADR. Missing regression-ADR → gate failure.

Composes with: ARCHITECTURE.md §4 #61; ADR-0065; Rule R-B (Competitive Baselines).

---

## Rule R-B.d — Automated Pillar Measurement [Deferred to W2 / W3]

**Re-introduction trigger**: (i) first perf benchmark harness in CI for `30.d.performance`; (ii) first cost-accounting hook landing per Rule 13 trigger for `30.d.cost`; (iii) CI-timed onboarding script for `30.d.developer_onboarding`; (iv) governance dashboard for `30.d.governance`.

**Rule (draft)**: Each pillar dimension MUST be measured automatically (no manual `N/A` placeholders) once its trigger fires. The measurement MUST update `current_value` on every release; the gate MUST reject `current_value: N/A` for a dimension whose trigger has fired.

Composes with: ARCHITECTURE.md §4 #61; ADR-0065; Rule 13 (P1 cost-of-use, deferred W3); Rule 18 (Eval harness, deferred W4).

---

## Rule R-C.b.b — Runtime Semver Compatibility Enforcement [Deferred to W2]

**Re-introduction trigger**: first BoM release that drops a previously-published artifact, OR first starter that introduces a breaking config change without a major-version bump (target: W2).

**Rule (draft)**: A gate rule MUST cross-check `<module>/module-metadata.yaml`'s `semver_compatibility` against the artifact's actual API delta. A starter that introduces a breaking config change without a major-version bump → gate failure. A BoM revision that removes a coordinate without a deprecation window declared in `module-metadata.yaml` → gate failure.

Composes with: ARCHITECTURE.md §4 #62; ADR-0066; Rule R-C.b (Independent Module Evolution).

---

## Rule R-D sub-clause .a.b — TCK Reactor Module Scaffolding [Deferred to W2]

**Re-introduction trigger**: first alternative implementation of any `agent-runtime` SPI is proposed — Postgres `Checkpointer`, Temporal `RunRepository`, or Redis `IdempotencyStore` (target: W2).

**Rule (draft)**: A sibling `agent-runtime-tck` reactor module MUST exist with a single `@TckSurfaceMarker` test asserting the SPI interface signatures it covers. Adding the module bumps `module_count_invariant` (Gate Rule 28e) from 4 to 5.

**Pre-promotion holding tank** (added 2026-05-18 by the Beyond-SDD review response, see [`docs/logs/reviews/spring-ai-ascend-beyond-sdd-response.en.md`](logs/reviews/spring-ai-ascend-beyond-sdd-response.en.md) §4): the SPI-contract semantics that the future TCK module will assert are already executable today, in two locations that lift-and-shift cleanly when the trigger fires:

1. **`agent-runtime-core/src/test/java/...`** — pure-JUnit library-mode tests (`RunStateMachineLibraryTest`, `SuspendSignalLibraryTest`, `S2cCallbackEnvelopeLibraryTest`, `RunRecordTenantLibraryTest`) exercise the SPI value-type algebra with no Spring context. These tests are universal — they apply to every conformant impl.
2. **`agent-service/src/test/java/.../inmemory/`** — `InMemoryCheckpointerTest`, `InMemoryCheckpointerSizeCapTest`, `InMemoryRunRegistryFindRootRunsTest` carry a `// TCK-promotion-candidate` class-level marker. On Rule R-D sub-clause .a.b trigger they move to `agent-runtime-tck/src/main/java/.../tck/` and the in-memory impl becomes one test target alongside Postgres/Temporal/Redis.

This holding tank honours Rule D-2 (Simplicity) — no module is scaffolded today for a single implementation — while making the W2 promotion mechanical.

Composes with: ARCHITECTURE.md §4 #63; ADR-0067; Rule R-D sub-clause .a (SPI + DFX + TCK Co-Design); Rule D-3.b (Evidence-First Debug Sequence) — the library-mode tests above ARE the evidence layer Rule D-3.b calls for.

---

## Rule R-D sub-clause .a.c — TCK Conformance Suite [Deferred to W2]

**Re-introduction trigger**: first alternative implementation is proposed AND its author requests "conformant" status (target: W2).

**Rule (draft)**: For every SPI under `<module>/spi_packages` declared in `module-metadata.yaml`, there MUST be a `<module>-tck` test class that an alternative implementation runs against to be accepted as conformant. The TCK MUST cover (a) happy-path semantics, (b) error contract (which exceptions on which inputs), (c) thread-safety claim, (d) tenant-scope honouring.

Composes with: ARCHITECTURE.md §4 #63; ADR-0067; Rule R-D sub-clause .a.

---

## Rule R-D sub-clause .a.d — Vulnerability Scanner Integration [Deferred to W2]

**Re-introduction trigger**: first CVE-bearing transitive dependency flagged manually OR first regulated-customer deployment requiring SCA reports (target: W2).

**Rule (draft)**: A CI workflow MUST run a CVE/SCA scanner (Dependency-Check, Snyk, Trivy, or equivalent) on every PR. Findings at severity ≥ HIGH block merge unless an allow-list entry with a `risk_acceptance_adr:` reference is present.

Composes with: ARCHITECTURE.md §4 #63; ADR-0067; Rule R-D sub-clause .a; per-module `docs/dfx/<module>.yaml` `vulnerability:` block.

---

## Rule R-E.b — Three-Track Channel Physical Implementation [Deferred to W2]

**Re-introduction trigger**: first deployable `agent-bus-java` reactor module shipping in research/prod posture with > 1 service instance (target: W2).

**Rule (draft)**: Each of the three channels declared in `docs/governance/bus-channels.yaml` MUST be backed by a distinct physical transport — Kafka topics with isolated partitions, separate Redis Streams, OR equivalent broker primitives. The `physical_channel:` identifier in the YAML MUST map to a concrete broker resource. Co-locating two channels on the same physical transport (even with different routing keys) is forbidden — the failure-isolation guarantee requires distinct underlying queues.

Composes with: ARCHITECTURE.md §6.4; ADR-0069; Rule R-E; LucioIT W1 §6.4.

---

## Rule R-G.c — agent-platform JdbcTemplate → R2DBC Migration [Deferred to W2]

**Re-introduction trigger**: first move of any HTTP edge endpoint from blocking Servlet to reactive WebFlux (target: W2 telemetry vertical).

**Rule (draft)**: `HealthCheckRepository` and `PlatformOssApiProbe` (the two existing `JdbcTemplate` consumers in `agent-platform`) MUST be migrated to `R2dbcEntityTemplate`. Once migrated, Rule R-G widens to cover `agent-platform/src/main/java/**` in addition to the current `agent-runtime` scope.

Composes with: ARCHITECTURE.md §6.3; ADR-0069; Rule R-G; LucioIT W1 §6.3.

---

## Rule R-J.a.b — RLS Retrofit for Grandfathered Tables [Deferred to W2]

**Re-introduction trigger**: first multi-tenant production tenant goes live with the `idempotency_dedup` table populated (target: W2).

**Rule (draft)**: A new Flyway migration (V3 or later) MUST `ALTER TABLE idempotency_dedup ENABLE ROW LEVEL SECURITY` and add per-tenant `CREATE POLICY` rules. After landing, the table is removed from `gate/rls-baseline-grandfathered.txt` and Rule R-J.a enforces RLS on it directly.

Composes with: ARCHITECTURE.md §7.2; ADR-0069; Rule R-J.a; LucioIT W1 §7.2.

---

## Rule R-K.c — Run/Step Suspension Transition [Deferred to W2]

**Re-introduction trigger**: first W2 async orchestrator implementation that consumes a `SkillResolution.reject(SuspendReason.RateLimited)` and emits a corresponding `Run.withSuspension(...)` (or dependent-step suspension) transition. Conditioned on the same trigger as Rule R-M sub-clause .d.c (W2 async orchestrator landing).

**Rule (draft)**: When `ResilienceContract.resolve(tenant, skill)` returns `SkillResolution.admitted = false` with `SuspendReason.RateLimited(SKILL_CAPACITY_EXCEEDED)`, the W2 orchestrator MUST translate the rejection into an actual `RunStatus.SUSPENDED` transition (per Rule R-C.d state-machine validity) on the dependent step that owns the skill call. The parent `Run` stays `RUNNING` so unrelated sub-branches continue (sub-Run granularity — composes with Rule R-M sub-clause .d.b "post-review strengthening"). The suspension MUST carry the `SuspendReason.RateLimited` payload into the persisted `Run.suspendReason` field so observability surfaces the saturating skill key. Failure to translate the rejection is a ship-blocking defect under Rule D-5.

**Background**: At W1.x scope, the active Rule R-K kernel only commits to the decision-envelope behaviour — `ResilienceContract.resolve` returns a `SkillResolution` carrying the reason, and the caller (today: no W2 orchestrator yet) is responsible for the actual `Run` transition. ADR-0070 §Consequences explicitly notes: *"Run-row carries no suspendReason field — the reason lives on SkillResolution. W2 orchestrator wiring will add Run.suspendReason when it actually transitions runs to SUSPENDED."* The rc10 post-corrective architecture review (finding P1-1) flagged the W1.x Rule R-K kernel for overclaiming end-state behaviour; rc11 narrows the kernel and lands Rule R-K.c as the deferred companion.

**Why deferral, not immediate fix**: The runtime translation requires the W2 async orchestrator's bus-level suspension primitive (`SuspendSignal` Chronos Hydration per Rule R-H) plus a durable `Run.suspendReason` column (Flyway V3+). Retrofitting `SyncOrchestrator` alone would either (a) require reimplementing the bus wake-pulse in-memory (massive W1 scope creep) or (b) emit a half-measure that block-waits on a different primitive. Better to land the whole non-blocking story together when the W2 async orchestrator ships — same logic as Rule R-M sub-clause .d.c (S2C non-blocking lifecycle promotion).

Composes with: Rule R-K (Skill Capacity Matrix); Rule R-C.d (Run State Transition Validity); Rule R-H (Chronos Hydration); Rule R-M sub-clause .d.b ("post-review strengthening" — sub-Run granularity for skill saturation, identical pattern); Rule R-M sub-clause .d.c (W2 async orchestrator landing — shared trigger); ADR-0070 §Consequences; ADR-0085 (rc11 corpus-truth wave authority).

---

## Rule R-L.b — SandboxExecutor Subsumption Runtime Check [Deferred to W2]

**Re-introduction trigger**: first sandboxed skill ships (`code-interpreter` or `untrusted-tool`) in research or prod posture (target: W2).

**Rule (draft)**: `SandboxExecutor.execute(skill, logical_grant)` MUST cross-reference `logical_grant` against the per-skill row in `docs/governance/sandbox-policies.yaml`. If `logical_grant` declares any capability (outbound destination, filesystem path, syscall) wider than what the per-skill physical limit allows, the executor MUST reject the call with `SandboxSubsumptionViolation` BEFORE invoking the sandboxed code. Test: a synthetic request granting `outbound_network: allow_all` to a skill whose YAML declares an allowlist of `["api.openai.com:443"]` MUST be rejected.

Composes with: ARCHITECTURE.md §7.4; ADR-0069; Rule R-L; LucioIT W1 §7.4.

---

## Rule R-M sub-clause .b.b — Run.engineType Field Persistence [Deferred to W2]

**Re-introduction trigger**: first W2+ orchestrator implementation that persists `Run` to Postgres and requires a discriminator column independent of `Run.mode` (target: W2; promoted when a third engine type ships or when `Run.mode` ceases to be 1:1 with engine identity).

**Rule (draft)**: Promote `Run.mode` (`GRAPH | AGENT_LOOP`) to a first-class `Run.engineType` field. Flyway migration V3+ adds `engine_type VARCHAR(64) NOT NULL` with a check constraint against the `known_engines[].id` set declared in `docs/contracts/engine-envelope.v1.yaml`. A backfill statement maps `mode='GRAPH' → engine_type='spring_ai_graph_v1'` and `mode='AGENT_LOOP' → engine_type='iterative_agent_loop_v1'` (or whichever ids ship). The Java `Run` record gains `String engineType()` as a non-null accessor; `RunMode` is retained for backward-compatible reads but deprecated. Dispatch routing prefers `Run.engineType` over `Run.mode`.

Composes with: ARCHITECTURE.md (Run record §source-tree); ADR-0072 §Consequences (the deferral was first declared here); Rule R-M sub-clause .b; Rule R-C.c (Contract Spine Completeness).

---

## Rule R-M sub-clause .b.c — Parent-Run Propagation on Child Failure [Deferred to W2]

**Re-introduction trigger**: first W2 async orchestrator implementation that processes child-run failures asynchronously across JVM-boundary (target: W2 Postgres-backed orchestrator).

**Rule (draft)**: When a child Run terminates with `FAILED` (including `engine_mismatch` per Rule R-M sub-clause .b), the parent Run MUST also transition to `FAILED` with a propagated reason that names the failing child (`child_failed:<childRunId>:<originalReason>`). The current `SyncOrchestrator.executeLoop()` only transitions the originating child Run; the parent remains in `SUSPENDED` waiting for a child result that will never arrive. W2 async orchestrator MUST add a parent-propagation hook that fires on every terminal child transition.

Composes with: Rule R-C.d (Run State Transition Validity); Rule R-M sub-clause .b (Strict Engine Matching); ARCHITECTURE.md §4 #20.

---

## Rule R-M sub-clause .c.b — HookOutcome Run-State Consumption [Deferred to W2 Telemetry Vertical]

**Re-introduction trigger**: first consumer hook (TokenCounterHook / PiiRedactionHook / CostAttributionHook / LlmSpanEmitterHook) lands in W2 Telemetry Vertical. At that point a real middleware will return `HookOutcome.Fail` / `ShortCircuit` and the orchestrator must consume the outcome.

**Rule (draft)**: When a middleware returns `HookOutcome.Fail(reason)`, the orchestrator MUST transition the Run to `RunStatus.FAILED` with `finishedAt` set, fire `HookPoint.ON_ERROR` carrying the failure reason, and re-throw a typed exception so the caller observes failure. When a middleware returns `HookOutcome.ShortCircuit(value)`, the orchestrator MUST skip the wrapped engine call and treat `value` as the engine's return. The fail-fast property within the dispatcher chain (already enforced by `HookDispatcher` at W2.x) is preserved.

Authority: post-release architecture review §P0-3 (plan D); ADR-0073 §Consequences ("Outcomes are LOGGED, NOT acted upon at Phase 2 (W2 wires Fail → Run.FAILED, ShortCircuit → return result)"); CLAUDE.md Rule R-M sub-clause .c W2.x scope clarification paragraph.

Composes with: Rule R-M sub-clause .c (Runtime-Owned Middleware via Engine Hooks); Rule R-C.d (Run State Transition Validity); ADR-0073.

---

## Rule R-M sub-clause .d.b — ResilienceContract s2c.client.callback Wiring [Deferred to W2]

**Re-introduction trigger**: first production S2C deployment with > 1 concurrent client (target: W2; conditioned on the first non-in-memory `S2cCallbackTransport` implementation shipping).

**Rule (draft)**: `ResilienceContract.resolve(tenant, "s2c.client.callback")` MUST consult the `s2c.client.callback` row in `docs/governance/skill-capacity.yaml` at runtime. When per-tenant or global capacity is exhausted, the second concurrent caller MUST be SUSPENDED (Chronos Hydration per Rule R-H) carrying `SuspendReason.RateLimited(S2C_CALLBACK_CAPACITY_EXCEEDED)`, NOT failed. The in-memory transport at W2.x consults the matrix but does not yet enforce it because there is only one client; production transports (webhook, SSE, WebSocket) must enforce on every dispatch.

**Post-review strengthening (plan G):** Over-capacity skill use MUST suspend **only the dependent step**, NOT the whole run nor unrelated LLM inference threads in the same Run. The W2 orchestrator-admission path is the contract surface: a step blocked on skill capacity is suspended via `SuspendSignal` carrying the step key + skill key; the parent Run stays `RUNNING` so unrelated branches continue. This is the 2D defence net (Tenant Quota × Global Skill Capacity per Rule R-K) applied at sub-Run granularity.

Composes with: Rule R-M sub-clause .d (S2C Callback Envelope + Lifecycle Bound); Rule R-K (Skill Capacity Matrix); ADR-0074; ADR-0069 / LucioIT W1 §7.3; post-release review §4 skill-capacity orchestration binding.

---

## Rule R-M sub-clause .d.c — S2C Non-Blocking Lifecycle Promotion [Deferred to W2]

**Re-introduction trigger**: W2 async orchestrator lands (target: W2 scheduler wave). The synchronous `SyncOrchestrator.handleClientCallback` is replaced by a non-blocking equivalent that suspends the parent Run via the bus and resumes on the response wake-pulse without holding an OS thread.

**Rule (draft)**: `SyncOrchestrator.handleClientCallback` (or its W2 successor) MUST NOT block the orchestrator thread on the S2C response future. The waiting Run is suspended via the existing `SuspendSignal.forClientCallback(...)` checked-suspension path (already in place as of v2.0.0-rc3 per cross-constraint audit α-2 / β-5), but the *thread* must be released back to the scheduler instead of awaiting `.toCompletableFuture().join()`. Resume happens when the bus delivers the response wake-pulse.

**Background**: ADR-0074 §Consequences accepted a synchronous W2.x bridge for the SPI. The v2.0.0-rc1 cross-constraint audit (P0-1) noted this directly contradicts Rule R-M sub-clause .d's "MUST suspend, must not block a thread" and Principles P-F/P-G/P-H. The rc3 response: narrow Rule R-M sub-clause .d's prose to acknowledge the W2.x bridge as a deferred exception (this sub-clause); the structural fix lands when the async orchestrator ships.

**Why deferral, not immediate fix**: A non-blocking S2C resume requires the bus-level wake-pulse machinery that lands in W2 alongside `SuspendSignal` Chronos Hydration (Rule R-H / Rule R-K.b runtime integration). Retrofitting `SyncOrchestrator` alone would require either (a) reimplementing the wake-pulse in-memory (massive scope creep into W1) or (b) a half-measure that still blocks on a different primitive. Better to land the whole non-blocking story together when the W2 async orchestrator ships.

Composes with: Rule R-M sub-clause .d (S2C Callback Envelope + Lifecycle Bound); Rule R-H (No Thread.sleep in Business Code); Principle P-F (Cursor Flow); Principle P-G (Absolute Non-Blocking I/O); Principle P-H (Chronos Hydration); ADR-0074 §Consequences.

---

## Rule M-2 sub-clause .a.b — W3 Prose-Enum Schema-First Retrofit [Deferred to W3]

**Re-introduction trigger**: W3 contract-design sprint kickoff (default target: 2026-09-30 — the working sunset date encoded in `gate/schema-first-grandfathered.txt`). Activates earlier if any grandfather entry's `sunset_date` is moved earlier via ADR.

**Rule (draft)**: Every entry remaining in `gate/schema-first-grandfathered.txt` after its declared `sunset_date` MUST be retrofitted to a yaml schema under `docs/contracts/` or `docs/governance/`. The retrofit (a) replaces the prose enum with a fenced code block referencing the schema file, (b) lands a Java enum + ctor-level schema validation per Rule M-2 sub-clause .a doctrine, (c) removes the entry from the grandfather list. ADR-0078 (or successor) MUST schedule the retrofit waves; gate Rule 60 fails closed once any entry's sunset_date passes without retrofit (the sunset-fail logic is gate-enforced as of W2.x Phase 8).

Composes with: Rule M-2 sub-clause .a (Schema-First Domain Contracts); ADR-0077; ADR-0068 (Layered 4+1 corpus); gate Rule 60 self-tests.

---

## Rule 28k.b — Schema↔Java-Shape Parity ArchUnit [Deferred to W3]

**Re-introduction trigger**: W3 contract-design sprint kickoff — the same trigger as Rule M-2 sub-clause .a.b, since both close the structural gap surfaced by the v2.0.0-rc1 second-pass review F-α category audit.

**Rule (draft)**: For every `docs/contracts/*.v1.yaml` schema that declares a `required_fields:` or `hooks:` (or equivalent ordered enum) block AND a paired Java type whose name is named in the YAML header comment, an ArchUnit test MUST assert bidirectional shape parity:
- Every Java record component / enum constant has a matching YAML entry (no extra Java symbols).
- Every YAML entry has a matching Java record component / enum constant (no extra YAML entries).

**Background**: The W2.x wave shipped two strong parity enforcers — E77 (`engine_registry_covers_all_known_engines`: bidirectional `known_engines[].id` ↔ `ENGINE_TYPE`) and E78 (`engine_hooks_yaml_present_and_wellformed`: bidirectional `hooks:` list ↔ `HookPoint` enum). Three other mirror claims ship only schema-presence checks, not shape parity:
- `EngineEnvelope` record `required_fields:` ↔ Java record components (only nullability validated today, partially covered by E76+E84).
- `engine-hooks.v1.yaml` hook order ↔ `HookPoint` enum declaration order (E78 checks set membership, not order — though order is asserted in `engine-hooks.v1.yaml#ordering: declared`).
- `evolution-scope.v1.yaml` discriminators ↔ `EvolutionExport` enum constants (E87 is armed-empty until W2 RunEvent variants ship).

The rc2 second-pass review flagged these as P1 F-α instances (parity claims without binding cross-check). The reviewer's narrowed wording closes them at the prose level; this deferral records the structural fix.

Composes with: Rule R-C.a (Code-as-Contract Coverage); Rule M-2 sub-clause .a (Schema-First Domain Contracts); ADR-0072; ADR-0073; ADR-0075; v2.0.0-rc1 second-pass review F-α category audit (`docs/reviews/2026-05-17-l0-w2x-rc1-second-pass-review-response.en.md` §2).

---

## Rule M-2 sub-clause .a.c — EngineEnvelope Strict-Construction Validation [Deferred to W2]

**Re-introduction trigger**: first `EngineEnvelope` construction outside a Spring-boot test harness — i.e., production code that constructs envelopes from external input (REST controller, message-bus consumer, client SDK) rather than from a programmer-controlled literal.

**Rule (draft)**: The `EngineEnvelope` record constructor MUST reject `engineType` values not present in `docs/contracts/engine-envelope.v1.yaml#known_engines[].id`. Today the constructor validates only nullability; `engineType` membership is enforced lazily by `EngineRegistry.resolve()` at dispatch time. W2 promotion: load `known_engines` once at JVM startup (or on first envelope construction), cache, and reject in the ctor. Rationale for the deferral: today every envelope is built inside a Spring-managed context where `EngineRegistry.validateAgainstSchema()` has already run at boot per ADR-0076; user-supplied envelopes do not yet exist.

Composes with: Rule M-2 sub-clause .a (Schema-First Domain Contracts); Rule R-M sub-clause .b (Strict Engine Matching); ADR-0072; ADR-0076.
---

## Rule R-A.c — Quickstart Smoke Run in CI (ACTIVATED 2026-05-18)

**Activated 2026-05-18 per Wave 4 Track E.** See `docs/governance/rules/rule-29c.md` for
the active rule card. The original deferred draft below is preserved for audit.

**Re-introduction trigger (original)**: first `.github/workflows/*.yml` (or sibling container-based CI
workflow) that boots a Spring Boot reactor end-to-end. Fired 2026-05-18 when
`.github/workflows/ci.yml` quickstart-smoke job landed.

**Rule (now active)**: A CI job MUST execute the `docs/quickstart.md` instructions on a clean
container and assert that `GET /v1/health` returns 200 within 60 s of `spring-boot:run` start.
Failure of this job is a ship-blocking finding under Rule D-5.

Composes with: ARCHITECTURE.md §4 #60; ADR-0064; Rule R-A.

---

## Rule R-I sub-clause .c — IngressGateway Runtime Implementation [Deferred to W3+]

**Re-introduction trigger**: first agent-client SDK release shipping production Java code
under `agent-client/src/main/java/` (W3+ per ADR-0049).

**Rule (draft)**: The bus-side implementation of `ascend.springai.bus.spi.ingress.IngressGateway`
MUST be wired to the agent-service `/v1/runs` HTTP routes via a bounded queue on the data
channel (`bus-channels.yaml#channels[id=data]`). Implementation MUST honour the Task Cursor
shape from Rule R-F: long-running requests (`request_type=RUN_CREATE`) return
`IngressResponse.accepted(requestId, cursor)`; non-cursor request types return inline.
The contract status in `docs/contracts/ingress-envelope.v1.yaml` MUST be promoted from
`design_only` to `runtime_enforced` in the same wave.

Composes with: Rule R-I sub-clause .a (Five-Plane Manifest); Rule R-E (Three-Track Channel
Isolation — selects the data channel); Rule R-F (Cursor Flow Mandate); ADR-0089; ADR-0050.

---

## Rule R-I sub-clause .d — Edge HTTP-Route Direct-Call Prohibition [Deferred to W3+]

**Re-introduction trigger**: same as R-I.c (first agent-client SDK release).

**Rule (draft)**: Once the W3+ SDK ships, an integration test MUST assert that an
agent-client request to a compute_control HTTP route (e.g., direct call to
`agent-service /v1/runs`) fails by network-level rejection (e.g., service mesh policy,
authn config) — not just by ArchUnit/static analysis. Until then, the W1 ArchUnit + gate
guards (E143 + Rule 105) cover the import surface; HTTP-level enforcement is the W3+
promotion gate.

Composes with: Rule R-I sub-clause .b (W1 invariant this strengthens); ADR-0089;
deployment-time mesh / load-balancer config.

---

## Rule R-I sub-clause .e — Bus Backpressure Mapping for Ingress [Deferred to W2]

**Re-introduction trigger**: first non-stub `BackpressureSignal` SPI implementation lands
in agent-bus (W2 per ADR-0050).

**Rule (draft)**: The IngressGateway MUST surface ingress-channel backpressure as
`IngressResponse.deferred(requestId)` (status DEFERRED) when the bus reports a non-zero
admit-rate hold. Clients MUST treat DEFERRED as a retry-with-backoff hint, not a
terminal failure. The retry policy itself is in scope for the SDK companion deferred
sub-clause (out of scope for the bus implementation).

Composes with: Rule R-K (Skill Capacity Matrix — the in-process analogue);
ADR-0089; ADR-0050.
