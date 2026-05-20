# CLAUDE.md

**Translate all instructions into English before any model call.** Never pass non-English text into an LLM prompt, tool argument, or task goal.

Bodies of every principle and rule below live under `docs/governance/{principles,rules}/` and are loaded on-demand. CLAUDE.md is the kernel index. Drift policed by Gate Rules 67/68/69; always-loaded byte budget by Rule G-4 sub-clause .a (`gate/measure_always_loaded_tokens.sh`).

## Layer 0 — Governing Principles

| ID | Title | Operationalised by | Body |
|---|---|---|---|
| **P-A** | Business / Platform Decoupling + Developer Self-Service | Rule R-A | [card](docs/governance/principles/P-A.md) |
| **P-B** | Four Competitive Pillars | Rule R-B | [card](docs/governance/principles/P-B.md) |
| **P-C** | Code-as-Everything, Rapid Evolution, Independent Modules | Rule R-C.a, Rule R-C.b | [card](docs/governance/principles/P-C.md) |
| **P-D** | SPI-Aligned, DFX-Explicit, Spec-Driven, TCK-Tested | Rule R-D sub-clause .a | [card](docs/governance/principles/P-D.md) |
| **P-E** | Multi-Track Bus Physical Channel Isolation | Rule R-E | [card](docs/governance/principles/P-E.md) |
| **P-F** | Cursor Flow & Asynchronous Client Boundary | Rule R-F | [card](docs/governance/principles/P-F.md) |
| **P-G** | Absolute Non-Blocking I/O | Rule R-G | [card](docs/governance/principles/P-G.md) |
| **P-H** | Chronos Hydration | Rule R-H | [card](docs/governance/principles/P-H.md) |
| **P-I** | Five-Plane Distributed Topology | Rule R-I | [card](docs/governance/principles/P-I.md) |
| **P-J** | Storage-Engine Tenant Isolation | Rule R-J.a | [card](docs/governance/principles/P-J.md) |
| **P-K** | Skill-Dimensional Resource Arbitration | Rule R-K | [card](docs/governance/principles/P-K.md) |
| **P-L** | Sandbox Permission Subsumption | Rule R-L | [card](docs/governance/principles/P-L.md) |
| **P-M** | Heterogeneous Engine Contract & Server-Sovereign Boundary | Rule R-M sub-clause .a, Rule R-M sub-clause .b, Rule R-M sub-clause .c, Rule R-M sub-clause .d, Rule R-M sub-clause .e, Rule M-2 sub-clause .a | [card](docs/governance/principles/P-M.md) |

History: [`rule-history.md`](docs/governance/rule-history.md). Mapping: [`principle-coverage.yaml`](docs/governance/principle-coverage.yaml).

## Layer 1 — Engineering Rules

### Daily principles
#### Rule D-1 — Root-Cause + Strongest-Interpretation Before Plan

**Before writing any plan, fix, or feature — surface assumptions, name confusion, and state tradeoffs. Then (a) name the root cause mechanically and (b) choose the strongest valid reading of the requirement.**

Enforced by [`rule-D-1.md`](docs/governance/rules/rule-D-1.md).

---
#### Rule D-2 — Simplicity & Surgical Changes

**Minimum code that solves the stated problem. Touch only what the task requires.**

Enforced by [`rule-D-2.md`](docs/governance/rules/rule-D-2.md).

---
#### Rule D-3 — Pre-Commit Checklist + Evidence-First Debug

**Before every commit, audit every touched file; fix defects before committing — "I'll fix it later" is forbidden; **smoke + lint** required before commits touching server entry points, runtime adapters, or dependency-wiring modules (sub-clause .a — Pre-Commit Checklist). When a Run fails, a test regresses, or a self-audit finding is opened, the first artefact captured MUST be observable evidence — failing test class FQN, trace ID, MDC slice (runId, tenantId, fromStatus→toStatus), and raw error message including stack frame line numbers; ARCHITECTURE.md / ADR consultation is permitted only AFTER evidence is recorded; self-audit findings under Rule D-5 that omit evidence citation are blocked (sub-clause .b — Evidence-First Debug; operationalised by `docs/runbooks/debug-first-evidence.md`).**

Enforced by [`rule-D-3.md`](docs/governance/rules/rule-D-3.md).

---
#### Rule D-4 — Three-Layer Testing, With Honest Assertions

A feature is implementable only when all three layers are designed. A feature is shippable only when all three are green and Rule D-5 passes.

Enforced by [`rule-D-4.md`](docs/governance/rules/rule-D-4.md).

---

### Class / resource patterns
#### Rule D-7 — Concurrency / Async Resource Lifetime

**Every async or reactive resource has a lifetime bound to exactly one execution context.**

Enforced by [`rule-D-7.md`](docs/governance/rules/rule-D-7.md).

---
#### Rule D-8 — Single Construction Path Per Resource Class

**For every shared-state resource, exactly one builder/factory owns construction. All consumers receive the instance by dependency injection.**

Enforced by [`rule-D-8.md`](docs/governance/rules/rule-D-8.md).

---

### Delivery process
#### Rule D-5 — Self-Audit is a Ship Gate, Not a Disclosure

A self-audit with open findings in a downstream-correctness category **blocks delivery**.

Enforced by [`rule-D-5.md`](docs/governance/rules/rule-D-5.md).

---
#### Rule D-6 — Posture-Aware Defaults

**Every config knob, fallback path, and persistence backend declares its default behaviour under three postures: `dev` / `research` / `prod`.**

Enforced by [`rule-D-6.md`](docs/governance/rules/rule-D-6.md).

---

### Architectural enforcement
#### Rule G-2 — Authority-Text Reality (doc / status / path / numeric / name truth)

**Authority-text reality across the active corpus: `shipped: true` rows in `architecture-status.yaml` MUST have real `tests:` + `implementation:` paths and enforcer-backed prose (sub-clause .a — Architecture-Text Truth). `architecture-status.yaml#baseline_metrics` is the single source for entrypoint counts; `README.md` + `gate/README.md` numeric claims MUST point to it AND match parsed values (sub-clause .b — Baseline Metrics Single Source). Active `agent-*/ARCHITECTURE.md` path claims MUST resolve or carry historical markers (sub-clause .c). Root `ARCHITECTURE.md` module-count + path claims MUST match `pom.xml` and `architecture-status.yaml#repository_counts`; fenced tree-diagram SPI leaves MUST match `module-metadata.yaml#spi_packages` (sub-clause .d). `architecture-status.yaml#allowed_claim` text, every active `.md/.yaml/.yml/.java`, AND files under `ops/**/*.{yaml,yml,tpl,md}` / `docs/contracts/*.yaml` / `**/module-metadata.yaml` MUST NOT contain current-tense pre-Phase-C module names (`agent-platform`, `agent-runtime`) outside marker windows listed in `gate/active-corpus-name-exemption-markers.txt`; file-path exemptions in `gate/active-corpus-name-exemption-paths.txt` (sub-clauses .e + .f + .h). The latest release note under `docs/logs/releases/*.md` MUST NOT contain absolute graph node/edge counts disagreeing with live values unless marked historical (sub-clause .g).**

Enforced by [`rule-G-2.md`](docs/governance/rules/rule-G-2.md).

---
#### Rule R-C — Code-as-Contract + Independent Module Evolution + Run Contract Spine

**Every active normative constraint in the platform corpus MUST be enforced by code, registered in `docs/governance/enforcers.yaml`, and reach ≥1 of: an ArchUnit test, a `gate/check_architecture_sync.sh` rule, an integration test, a storage-layer schema constraint (NOT NULL / UNIQUE / CHECK / PRIMARY KEY), or a compile-time check (`@ConfigurationProperties + @Valid`, sealed types, package-info enforcement) (sub-clause .a — Code-as-Contract). Every Maven module declares a sibling `module-metadata.yaml` with `module`, `kind ∈ {platform|domain|starter|bom|sample}`, `version`, `semver_compatibility`, `architecture_doc`, `dfx_doc`, `spi_packages`, `allowed/forbidden_dependencies`; each builds + tests in isolation (sub-clause .b — Independent Module Evolution). Every persistent record under `agent-service/src/main/java/ascend/springai/service/runtime/{runs,idempotency}/**/*.java` MUST declare a `String tenantId` validated by `Objects.requireNonNull` (sub-clause .c — Contract Spine; relocated from agent-runtime-core per ADR-0088). Every `Run.withStatus(newStatus)` MUST call `RunStateMachine.validate(this.status, newStatus)` (sub-clause .d — Run State Transition Validity). No production class under `service.runtime..` may import `service.platform..`; the original narrow `TenantContextHolder` ban is asserted independently as defence-in-depth (sub-clause .e — Tenant Propagation Purity).**

Enforced by [`rule-R-C.md`](docs/governance/rules/rule-R-C.md).

---

### Governing principles (Layer-0 enforceable expressions)
#### Rule R-A — Business/Platform Decoupling Enforcement

**Platform code MUST NOT contain business-specific customizations. Business and example code MUST extend the platform via SPI + `@ConfigurationProperties` only — never by patching `*.impl.*` or `ascend.springai.service.platform..`. The platform MUST ship a runnable quickstart (`docs/quickstart.md`) referenced from `README.md` so a developer reaches first-agent execution without platform-team intervention.**

Enforced by [`rule-R-A.md`](docs/governance/rules/rule-R-A.md).

---
#### Rule R-B — Competitive Baselines Required

**Every release MUST publish `docs/governance/competitive-baselines.yaml` declaring four pillar dimensions — `performance`, `cost`, `developer_onboarding`, `governance` — each with a named `baseline_metric` and a `current_value` (or `N/A` for not-yet-instrumented). The most recent `docs/logs/releases/*.md` release note MUST mention all four pillar names. A regression in any `current_value` MUST be paired with a `regression_adr:` reference in the row.**

Enforced by [`rule-R-B.md`](docs/governance/rules/rule-R-B.md).

---
#### Rule R-D — SPI + DFX + TCK Co-Design + Catalog Integrity

**Every `kind: domain` module exposes ≥1 `*.spi.*` package with ≥1 public interface listed under `spi_packages` in `module-metadata.yaml`, plus a `docs/dfx/<module>.yaml` covering five DFX dimensions (releasability, resilience, availability, vulnerability, observability); TCK conformance suites are deferred to W2 per `CLAUDE-deferred.md` 32.b/.c (sub-clause .a). Every `spi_packages` entry MUST resolve to a real directory with ≥1 `.java` beyond `package-info.java` (sub-clause .b), MUST be declared by exactly one Maven module (no split packages, sub-clause .c), and MUST end in `.spi` OR contain `.spi.` (sub-clause .d). Every `kind ∈ {platform, domain}` module's `docs/dfx/<module>.yaml` declares an order-insensitive set-matching `spi_packages` block vs `module-metadata.yaml#spi_packages` (sub-clause .e). Every row in `docs/contracts/contract-catalog.md` §2 Active SPI interfaces table (not `(internal)`-marked) MUST resolve back to `module-metadata.yaml#spi_packages` AND `docs/dfx/<module>.yaml#spi_packages` (sub-clause .f). Every `public interface` declaration under any `*/spi/*` path (excluding `target/`) MUST appear in the catalog as an Active SPI row OR be `(internal)`-marked (sub-clause .g).**

Enforced by [`rule-R-D.md`](docs/governance/rules/rule-R-D.md).

---

### Vibe-Coding-era structural discipline
#### Rule G-1 — Layered 4+1 Discipline + Architecture-Graph Truth

**Every architecture artefact (`ARCHITECTURE.md` section, `docs/adr/*.yaml`, `docs/L2/*.md`, `docs/logs/reviews/*.md`) MUST declare front-matter `level: L0|L1|L2` and `view: logical|development|process|physical|scenarios` per the 4+1 discipline (sub-clause .a); root `ARCHITECTURE.md` is L0 canonical, `agent-*/ARCHITECTURE.md` is L1, `docs/L2/` is L2; phase-released L0/L1 artefacts are read-only with further edits flowing through `docs/logs/reviews/`. The machine-readable index `docs/governance/architecture-graph.yaml` MUST be generated (never hand-edited) by `gate/build_architecture_graph.sh` from principle-coverage / enforcers / status / module-metadata / ADR yaml inputs; the graph encodes principle→rule, rule→enforcer, enforcer→test/artefact, capability→test, module→module (allowed/forbidden), adr→adr (supersedes/extends/relates_to as DAGs), and (level,view)→artefact edges; the build MUST be idempotent (byte-identical re-run) (sub-clause .b).**

Enforced by [`rule-G-1.md`](docs/governance/rules/rule-G-1.md).

---

### L0 ironclad rules (W1.x absorption of LucioIT L0 §6/§7)
#### Rule R-E — Three-Track Channel Isolation

**Cross-service internal communication MUST be sliced into three physically isolated channels declared in `docs/governance/bus-channels.yaml`: `control` (out-of-band, highest priority), `data` (in-band, heavy-load), and `rhythm` (heartbeat/liveness). No two channels may share a `physical_channel:` identifier. The `data` channel inherits the 16 KiB inline-payload cap from §4 #13.**

Enforced by [`rule-R-E.md`](docs/governance/rules/rule-R-E.md).

---
#### Rule R-F — Cursor Flow Mandate

**Every long-horizon Runtime API endpoint MUST return a Task Cursor immediately and MUST NOT hold the client connection while work executes. The contract surface (request → cursor → polled status / SSE / Webhook) MUST be declared in `docs/contracts/openapi-v1.yaml` for at least one runs operation; new long-running endpoints MUST follow the same shape.**

Enforced by [`rule-R-F.md`](docs/governance/rules/rule-R-F.md).

---
#### Rule R-G — Reactive External I/O

**No production class under `agent-service/src/main/java/ascend/springai/service/runtime/**` may import `org.springframework.web.client.RestTemplate` or `org.springframework.jdbc.core.JdbcTemplate`. External I/O in runtime code MUST go through Reactive (`WebClient` / `R2dbcEntityTemplate`) or Virtual-Thread-backed clients.**

Enforced by [`rule-R-G.md`](docs/governance/rules/rule-R-G.md).

---
#### Rule R-H — No Thread.sleep in Business Code

**No production class under `agent-service/src/main/java/ascend/springai/service/platform/**` or `agent-service/src/main/java/ascend/springai/service/runtime/**` may invoke `Thread.sleep(...)` or `TimeUnit.<unit>.sleep(...)`. Long-horizon waits MUST be expressed as declarative suspension (`SuspendSignal`) and resumed by the bus-level Tick Engine.**

Enforced by [`rule-R-H.md`](docs/governance/rules/rule-R-H.md).

---
#### Rule R-I — Five-Plane Manifest

**Every `<module>/module-metadata.yaml` MUST declare `deployment_plane:` whose value is one of `edge | compute_control | bus_state | sandbox | evolution | none`. The plane assignment MUST match the L0 §7.1 topology — Edge Access (Agent Client SDK), Compute & Control (Runtime + Execution Engine), Bus & State Hub (Bus + Middleware persistence), Sandbox Execution (untrusted code), Evolution (Python ML). BoMs and build-time-only modules use `none` (sub-clause .a — Five-Plane Manifest). Modules whose `deployment_plane` is `edge` MUST NOT import any production class under `ascend.springai.{service,engine,middleware}..` AND MUST NOT invoke compute_control HTTP routes directly; edge→compute_control traffic flows exclusively through `ascend.springai.bus.spi.ingress.IngressGateway` whose wire schema is `docs/contracts/ingress-envelope.v1.yaml`; W1 enforcement is ArchUnit (`EdgeToComputeDirectLinkArchTest`) + gate rule `edge_no_direct_compute_link`; contract status `design_only` at W1, promoted to `runtime_enforced` when the agent-client SDK lands per ADR-0049 / W3+ (sub-clause .b — Edge↔Compute Ingress Routing, per ADR-0089).**

Enforced by [`rule-R-I.md`](docs/governance/rules/rule-R-I.md).

---
#### Rule R-J — Storage-Engine Tenant Isolation + Cancel Re-Authorization

**Tenant isolation is enforced at the storage engine: every Flyway migration creating a `tenant_id`-bearing table MUST enable Postgres Row-Level Security in the same migration (sub-clause .a; pre-rule migrations grandfathered in `gate/rls-baseline-grandfathered.txt` for W2 retrofit). At the HTTP edge, `POST /v1/runs/{runId}/cancel` MUST re-validate `(request.tenantId == Run.tenantId)` with HTTP 403 `tenant_mismatch` on miss; idempotent terminal→terminal same-status returns 200; illegal transitions return 409 `illegal_state_transition`; the cancel surface emits structured `WARN+` audit logs carrying `(runId, fromStatus, toStatus, actor, occurredAt)` MDC (sub-clause .b; resume/retry deferred to Rule R-J.b.d / W2 async orchestrator).**

Enforced by [`rule-R-J.md`](docs/governance/rules/rule-R-J.md).

---
#### Rule R-K — Skill Capacity Matrix

**`docs/governance/skill-capacity.yaml` MUST exist and declare, per skill, both `capacity_per_tenant` and `global_capacity` fields plus a `queue_strategy` (`suspend` or `fail`). The runtime `ResilienceContract.resolve(tenant, skill)` MUST consult this matrix; over-capacity resolution MUST return `SkillResolution.reject(SuspendReason.RateLimited)` rather than admit-or-fail. The actual `Run`/dependent-step suspension transition is deferred to Rule R-K.c (W2 scheduler admission). Chronos Hydration interlock with Rule R-H.**

Enforced by [`rule-R-K.md`](docs/governance/rules/rule-R-K.md).

---
#### Rule R-L — Sandbox Permission Subsumption

**`docs/governance/sandbox-policies.yaml` MUST exist with a `default_policy:` block declaring at least six required keys: `outbound_network`, `filesystem_read`, `filesystem_write`, `cpu_cap_millicores`, `memory_cap_megabytes`, `wall_clock_cap_seconds`. Enforcement-mode keys (e.g. `syscalls`) MAY be added beyond the required six. Per-skill rows MUST NOT widen the default policy beyond what the physical sandbox can enforce. Runtime refusal of over-wide logical grants by `SandboxExecutor` is deferred to Rule R-L.b (W2) per `docs/CLAUDE-deferred.md`.**

Enforced by [`rule-R-L.md`](docs/governance/rules/rule-R-L.md).

---

### W2.x Engine Contract Structural Wave (P-M)
#### Rule R-M — Engine Contract (envelope / matching / hooks / S2C / scope / historical)

**Every Run dispatch MUST go through `EngineRegistry.resolve(envelope)` against the `docs/contracts/engine-envelope.v1.yaml` schema; pattern-matching on `ExecutorDefinition` subtypes outside the registry is forbidden (sub-clause .a — Engine Envelope). A Run whose envelope declares `engine_type=X` MUST execute only via the `ExecutorAdapter` registered under X; mismatch raises `EngineMatchingException` and transitions the Run to FAILED with reason `engine_mismatch` (sub-clause .b — Strict Matching). Cross-cutting policies (model gateway, tool authz, memory governance, tenant policy, quota, observability, sandbox routing, checkpoint, failure handling) MUST be expressed as `RuntimeMiddleware` listening on canonical `HookPoint` events from `docs/contracts/engine-hooks.v1.yaml` (sub-clause .c — Hooks). Server-to-Client capability invocation goes through `S2cCallbackEnvelope` + `S2cCallbackTransport` SPI (under `ascend.springai.bus.spi.s2c`, owned by `agent-bus` per ADR-0088); the waiting Run suspends via `SuspendSignal.forClientCallback(...)` checked variant; client responses validated against `docs/contracts/s2c-callback.v1.yaml` (sub-clause .d — S2C). Every emitted `RunEvent` declares an `EvolutionExport` value (`IN_SCOPE | OUT_OF_SCOPE | OPT_IN`); out-of-scope events MUST NOT be persisted by the evolution plane (sub-clause .e — Evolution Scope). The deleted Java type name `S2cCallbackSignal` MUST appear only inside paragraphs marked historical via tokens listed in `gate/historical-marker-vocabulary.txt` (sub-clause .f — Historical-Only).**

Enforced by [`rule-R-M.md`](docs/governance/rules/rule-R-M.md).

---
#### Rule M-2 — Domain Contract Discipline (schema-first + design-only registration + DFX-stem truth)

**Domain contract discipline (sub-clause .a): every NEW domain enum or fixed-vocabulary taxonomy in `ARCHITECTURE.md` (root or per-module) on or after 2026-05-16 MUST cite a yaml schema under `docs/contracts/` or `docs/governance/` within ±5 lines; prose `<TYPE> | <TYPE>` enums outside fenced/yaml blocks are forbidden unless schema-referenced or grandfathered in `gate/schema-first-grandfathered.txt` (sunset_date required; advancing requires inline ADR). Every `docs/contracts/*.v1.yaml` whose `status: design_only` OR `runtime_enforced: false` MUST be listed by basename in `docs/contracts/contract-catalog.md` AND cite ≥1 `ADR-NNNN` whose file exists in `docs/adr/` (sub-clause .b). Every `docs/dfx/*.yaml` (excluding `docs/archive/`) MUST have a basename stem matching a `<module>` entry in root `pom.xml` (sub-clause .c).**

Enforced by [`rule-M-2.md`](docs/governance/rules/rule-M-2.md).

---

### Token-optimization wave (2026-05-17)
#### Rule G-3 — Kernel-Card-Implementation Coherence

**Kernel-Card-Implementation coherence is enforced across the CLAUDE.md / rule-card / CLAUDE-deferred triangle: each `#### Rule X` kernel paragraph in CLAUDE.md fits the per-card `kernel_cap` (sub-clause .a — Kernel Size Bounded); the kernel text byte-matches the card's `kernel:` scalar (sub-clause .b — Kernel-Card Match); every `#### Rule X` heading has a sibling `docs/governance/rules/rule-X.md`, every card has either a CLAUDE.md heading OR a `## Rule X.<letter>` reference in `docs/CLAUDE-deferred.md` (sub-clause .c — Every Active Rule Has a Card). Every `## Rule X.<letter>` sub-clause in CLAUDE-deferred.md MUST be acknowledged by a literal `Rule X.<letter>` in EITHER the CLAUDE.md kernel OR the rule card (sub-clause .d — Kernel-Deferred Coherence). Active kernel verbs implying shipped Run-state transition (`are SUSPENDED`, `transitions to FAILED`, `consumes the * capacity`, `is rejected, not failed`, `admits the caller`) MUST NOT appear when the matching obligation is explicitly deferred (sub-clause .e — Terminal-Verb vs Shipped-Decision). Rules listed in `gate/rule-100-disjunction-allowlist.txt` MUST carry explicit EITHER/OR connective wording in BOTH kernel AND card (sub-clause .f — Disjunction Truth).**

Enforced by [`rule-G-3.md`](docs/governance/rules/rule-G-3.md).

---
#### Rule G-4 — Always-Loaded Context Budget

**The always-loaded session-context budget is enforced two ways: every file listed in `gate/always-loaded-budget.txt` MUST be at or below its declared byte ceiling, validated by `gate/measure_always_loaded_tokens.sh` (sub-clause .a; a ceiling of `0` means kept on disk but excluded from the budget). `docs/CLAUDE-deferred.md` MUST NOT be auto-injected into session context — no `@docs/CLAUDE-deferred.md` include directive in `CLAUDE.md`, no `ALWAYS` / `ALWAYS-LOAD` marker on its row in `docs/governance/SESSION-START-CONTEXT.md` (sub-clause .b; plain prose pointers fine, only auto-load mechanisms forbidden).**

Enforced by [`rule-G-4.md`](docs/governance/rules/rule-G-4.md).

---

### Gate-script efficiency wave (2026-05-17)
#### Rule G-6 — Gate Machinery Integrity (duration + config)

**Gate machinery integrity is enforced on two surfaces: every gate run records per-rule duration in `gate/log/runs/<sha>_<ts>/per-rule.ndjson`; `gate/lib/update_benchmark_baseline.sh` maintains a rolling 5-run median at `gate/log/benchmarks/median.json`; the rule fails when any rule's current duration exceeds 2× baseline median AND exceeds 200ms absolute (sub-clause .a; bootstrap is vacuous until 5 successful runs exist). `gate/config.yaml` MUST validate against `gate/config.schema.yaml` — fails closed on missing required keys, type mismatch, out-of-range values, unknown keys (typo detection via `additionalProperties: false`), or enum violation (sub-clause .b; structural invariant: yaml → loader-validated env-vars → runtime-checked).**

Enforced by [`rule-G-6.md`](docs/governance/rules/rule-G-6.md).

---

### Linux-first dev environment (2026-05-18)
#### Rule G-7 — Linux-First Dev Environment

**All shell-driven operations (gates, builds, tests, generated artefacts, `git push`) MUST be verified on Linux — native, WSL2 (preferred), or WSL1 (fallback) — before merging to `main`. Git Bash for Windows is a debugging shim, not a verification environment. `docs/governance/dev-environment.md` is the canonical setup + verification guide. Measured 2026-05-18: WSL is 6–20× faster than Git Bash, AND surfaces platform-portability bugs that Win-only verification hides.**

Enforced by [`rule-G-7.md`](docs/governance/rules/rule-G-7.md).

---

### SPI metadata integrity wave (2026-05-18)

### Cross-constraint corpus-truth prevention wave (2026-05-18)
#### Rule M-1 — Skeleton Module Has No Production Java

**For every reactor module whose root `ARCHITECTURE.md` frontmatter `status:` field contains the token `skeleton`, the module's `src/main/java/**/*.java` tree MUST contain only `package-info.java` files OR placeholder SPI stub files whose first 30 lines name a `placeholder` keyword with an `ADR-NNNN` waiver. Modules with extracted production code MUST NOT carry a `skeleton` status.**

Enforced by [`rule-M-1.md`](docs/governance/rules/rule-M-1.md).

---

### Active-module path-truth prevention wave (2026-05-18)

### Root-architecture count + status-yaml prevention wave (2026-05-18)

### Gate self-consistency prevention wave (2026-05-18)
#### Rule G-5 — Gate Self-Consistency (parity / coverage / manifest / freshness)

**Gate self-consistency is enforced on four surfaces: canonical (`gate/check_architecture_sync.sh`) and parallel wrapper (`gate/check_parallel.sh`) MUST execute the same rule slug set, terminating at the `# === END OF RULES ===` marker with em-dash `—` separators (no double-dash) (sub-clause .a). `gate/test_architecture_sync_gate.sh` MUST fail closed when `passed != TOTAL`, derive TOTAL at runtime (not a bare literal), and declare a `test_rule_<N>_*` function for every prevention-wave rule (N ≥ 80) (sub-clause .b). `baseline_metrics.active_gate_checks` MUST equal the literal `# Rule N — slug` header count, matching what `parallel_summary: executed N rules` reports (sub-clause .c). Every header MUST have a matching `gate/rules/rule-NNN[a-z]?.sh` file keyed by unique rule id (sub-clause .d; `gate/rules/` is IDE-only generated, the canonical monolith is canonical).**

Enforced by [`rule-G-5.md`](docs/governance/rules/rule-G-5.md).

---

### Corpus-truth category-sweep prevention wave (2026-05-19)

### Kernel-implementation coherence prevention wave (2026-05-19)

### Cross-authority parity prevention wave (2026-05-20 rc14)
#### Rule G-8 — Cross-Authority Parity

**Canonical authority surfaces MUST agree with each other, not just be internally well-formed. (sub-clause .a — Graph baseline parity) `docs/governance/architecture-status.yaml#baseline_metrics.architecture_graph_nodes` AND `architecture_graph_edges` MUST equal the `node_count` AND `edge_count` declared in `docs/governance/architecture-graph.yaml` (live header from `python gate/build_architecture_graph.py --check --no-write`). (sub-clause .b — SPI path parity) every SPI package named in a CLAUDE.md `#### Rule` kernel OR a `docs/governance/rules/rule-*.md` card kernel MUST appear in exactly one `<module>/module-metadata.yaml#spi_packages` entry, exist on disk (`<module>/src/main/java/<package-path>/`), and match enforcer `asserts:` text in `docs/governance/enforcers.yaml`. (sub-clause .c — Module topology parity) the set of `<module>` entries in root `pom.xml`, `docs/governance/architecture-status.yaml#repository_counts.reactor_modules`, and the `<module>/module-metadata.yaml` files on disk MUST agree on the live module count; "each of the N modules" prose in active `.md` MUST use the same N. (sub-clause .d — Current-claim grammar) a same-line `post-ADR-NNNN` marker does NOT exempt a sentence containing present-tense verbs (`now reads`, `lives in`, `declares`, `includes`, `depends on`, `each of the [0-9]+ modules`) when that sentence names a deleted module from `gate/active-corpus-name-exemption-markers.txt`'s deleted-name set — exemption is reserved for explicitly historical prose (`formerly`, `until dissolved`, `pre-rc13`).**

Enforced by [`rule-G-8.md`](docs/governance/rules/rule-G-8.md).

---

## Deferred Rules

On-demand: [`docs/CLAUDE-deferred.md`](docs/CLAUDE-deferred.md). Currently deferred: Rules 7, 8, 13, 14, 15, 16, 17, 18, 19, 22, 23, 26, 27 + sub-clauses (Rules 11, 24, 29.c, 72 activated 2026-05-18 per Wave 4).
