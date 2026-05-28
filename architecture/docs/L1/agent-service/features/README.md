---
level: L1
view: scenarios
status: shipped
authority: "ADR-0151 (L1 Feature Registry canonical schema) + ADR-0152 (uniform L1 + W3 catalog rendering)"
---

<!-- DO NOT HAND-EDIT. Rendered from architecture/features/features.dsl by gate/lib/render_features_catalog.py. Re-emit via that script; render-idempotency is enforced by Rule G-13.b. -->

# `agent-service` — L1 Feature Catalog (9-section)

This catalog is the **rendered** human-readable view of the
`agent-service`-owned features registered in
[`architecture/features/features.dsl`](../../../../features/features.dsl).
The structured source is the DSL; this Markdown is byte-identical
on re-emit. The 9 sections follow the user-supplied L1 Feature
Catalog template (ADR-0151).

## 1. Feature Metadata

| Feature ID | Name | Status | Capability Domain |
|---|---|---|---|
| `FEAT-AGENT-SERVICE-ACCESS-LAYER` | Agent Service Access Layer | `shipped` | `agent-service-access-layer` |
| `FEAT-AGENT-SERVICE-ENGINE-DISPATCH-EXECUTION` | Agent Service Engine Dispatch Execution | `shipped` | `agent-service-engine-dispatch` |
| `FEAT-AGENT-SERVICE-INTERNAL-EVENT-QUEUE` | Agent Service Internal Event Queue | `shipped` | `agent-service-event-queue` |
| `FEAT-AGENT-SERVICE-SESSION-TASK-MANAGER` | Agent Service Session Task Manager | `shipped` | `agent-service-session-task-manager` |
| `FEAT-AGENT-SERVICE-TASK-CENTRIC-CONTROL` | Agent Service Task Centric Control | `shipped` | `agent-service-task-control` |
| `FEAT-AGENT-SERVICE-TRANSLATION-TOOL-INTERCEPT` | Agent Service Translation Tool Intercept | `shipped` | `agent-service-translation-tool` |
| `FEAT-ENGINE-DISPATCH-AND-HOOKS` | Engine Dispatch and Hooks | `shipped` | `engine-contract` |
| `FEAT-IDEMPOTENCY-AND-REPLAY` | Idempotency and Replay | `shipped` | `idempotency-protocol` |
| `FEAT-POSTURE-BOOTSTRAP` | Posture Bootstrap | `shipped` | `posture-bootstrap` |
| `FEAT-RUN-LIFECYCLE-CONTROL` | Run Lifecycle Control | `shipped` | `runtime-run-lifecycle` |
| `FEAT-SUSPEND-RESUME-CONTROL` | Suspend and Resume Control | `shipped` | `run-suspension-orchestration` |
| `FEAT-TENANT-ISOLATION` | Tenant Isolation | `shipped` | `tenant-isolation` |

## 2. Architecture Binding

### `FEAT-AGENT-SERVICE-ACCESS-LAYER`

**Development paths:**
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/api`

**Source ADR:** `ADR-0138|ADR-0155`

### `FEAT-AGENT-SERVICE-ENGINE-DISPATCH-EXECUTION`

**Development paths:**
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/engine`
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/executor/spi`

**Source ADR:** `ADR-0138|ADR-0155`

### `FEAT-AGENT-SERVICE-INTERNAL-EVENT-QUEUE`

**Development paths:**
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/events`

**Source ADR:** `ADR-0138|ADR-0155`

### `FEAT-AGENT-SERVICE-SESSION-TASK-MANAGER`

**Development paths:**
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/session`

**Source ADR:** `ADR-0138|ADR-0155`

### `FEAT-AGENT-SERVICE-TASK-CENTRIC-CONTROL`

**Development paths:**
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/runs`

**Source ADR:** `ADR-0138|ADR-0155`

### `FEAT-AGENT-SERVICE-TRANSLATION-TOOL-INTERCEPT`

**Development paths:**
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/translation`
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/intercept/spi`

**Source ADR:** `ADR-0138|ADR-0155`

### `FEAT-ENGINE-DISPATCH-AND-HOOKS`

**Development paths:**
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/engine`
- `agent-execution-engine/src/main/java`

**Source ADR:** `ADR-0088`

### `FEAT-IDEMPOTENCY-AND-REPLAY`

**Development paths:**
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/idempotency`

**Source ADR:** `ADR-0027`

### `FEAT-POSTURE-BOOTSTRAP`

**Development paths:**
- `agent-service/src/main/java/com/huawei/ascend/service/platform/posture`

**Source ADR:** `ADR-0055`

### `FEAT-RUN-LIFECYCLE-CONTROL`

**Development paths:**
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/runs`
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/state`

**Source ADR:** `ADR-0020`

### `FEAT-SUSPEND-RESUME-CONTROL`

**Development paths:**
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/suspend`

**Source ADR:** `ADR-0058`

### `FEAT-TENANT-ISOLATION`

**Development paths:**
- `agent-service/src/main/java/com/huawei/ascend/service/platform/tenant`
- `agent-service/src/main/resources/db/migration`

**Source ADR:** `ADR-0030`

## 3. Functional Decomposition

This module's features and their function-point membership are wired
by `contains` relationships in
[`architecture/features/features.dsl`](../../../../features/features.dsl).
Walk the workspace projection from each feature ID to traverse the
function-point inventory.

- `FEAT-AGENT-SERVICE-ACCESS-LAYER` contains the function points listed
  under `feat... -> fp... "contains"` relationships in features.dsl.
- `FEAT-AGENT-SERVICE-ENGINE-DISPATCH-EXECUTION` contains the function points listed
  under `feat... -> fp... "contains"` relationships in features.dsl.
- `FEAT-AGENT-SERVICE-INTERNAL-EVENT-QUEUE` contains the function points listed
  under `feat... -> fp... "contains"` relationships in features.dsl.
- `FEAT-AGENT-SERVICE-SESSION-TASK-MANAGER` contains the function points listed
  under `feat... -> fp... "contains"` relationships in features.dsl.
- `FEAT-AGENT-SERVICE-TASK-CENTRIC-CONTROL` contains the function points listed
  under `feat... -> fp... "contains"` relationships in features.dsl.
- `FEAT-AGENT-SERVICE-TRANSLATION-TOOL-INTERCEPT` contains the function points listed
  under `feat... -> fp... "contains"` relationships in features.dsl.
- `FEAT-ENGINE-DISPATCH-AND-HOOKS` contains the function points listed
  under `feat... -> fp... "contains"` relationships in features.dsl.
- `FEAT-IDEMPOTENCY-AND-REPLAY` contains the function points listed
  under `feat... -> fp... "contains"` relationships in features.dsl.
- `FEAT-POSTURE-BOOTSTRAP` contains the function points listed
  under `feat... -> fp... "contains"` relationships in features.dsl.
- `FEAT-RUN-LIFECYCLE-CONTROL` contains the function points listed
  under `feat... -> fp... "contains"` relationships in features.dsl.
- `FEAT-SUSPEND-RESUME-CONTROL` contains the function points listed
  under `feat... -> fp... "contains"` relationships in features.dsl.
- `FEAT-TENANT-ISOLATION` contains the function points listed
  under `feat... -> fp... "contains"` relationships in features.dsl.

## 4. Contract Surface

Runtime promise surfaces touched by this module's features. For the
full catalog, see
[`docs/contracts/contract-catalog.md`](../../../../../docs/contracts/contract-catalog.md).

## 5. Runtime Behavior

### `FEAT-AGENT-SERVICE-ACCESS-LAYER`

Layer 1 of the agent-service per-layer architecture (ADR-0138). Owns protocol convergence (HTTP / gRPC / WebSocket), tenant + auth binding (JWT.tenant cross-check, IdempotencyHeaderFilter), client capability publication via OpenAPI surface, and ingress for cursor / cancel / resume / S2C callback. Does NOT own Run aggregate state, Task control state, Session context state, engine dispatch, or model/tool translation. The deep-dive feature inventory (AS-L1-F01..F08) lives at architecture/docs/L1/agent-service/features/access-layer.md. + ADR-0155 v1.2 absorption adds F48-F65 design-only items.

### `FEAT-AGENT-SERVICE-ENGINE-DISPATCH-EXECUTION`

Layer 4 of the agent-service per-layer architecture (ADR-0138). Owns engine-adapter dispatch (EngineRegistry.resolve(envelope) → typed ExecutorAdapter) and the executor invocation pathway that drives the Run state machine. The deep-dive inventory lives at architecture/docs/L1/agent-service/features/engine-dispatch-execution.md. Cross-cutting policies expressed as RuntimeMiddleware hooks (see FEAT-ENGINE-DISPATCH-AND-HOOKS for the cross-module Engine Contract feature). + ADR-0155 v1.2 absorption adds F48-F65 design-only items.

### `FEAT-AGENT-SERVICE-INTERNAL-EVENT-QUEUE`

Layer 5 of the agent-service per-layer architecture (ADR-0138). Owns in-process event queue infrastructure used by the runtime to decouple emit-side from consume-side concerns. The deep-dive lives at architecture/docs/L1/agent-service/features/internal-event-queue.md. This is the runtime's internal eventing primitive; cross-service eventing flows through agent-bus three-track channels (control/data/rhythm). + ADR-0155 v1.2 absorption adds F48-F65 design-only items.

### `FEAT-AGENT-SERVICE-SESSION-TASK-MANAGER`

Layer 3 of the agent-service per-layer architecture (ADR-0138). Owns Session and Task aggregate lifecycles — the entities above Run that group runs into user-visible interactions. Task state machine governs admission / suspension / completion at the user-interaction level; Session state machine groups tasks into a conversation context. The deep-dive lives at architecture/docs/L1/agent-service/features/session-task-manager.md. + ADR-0155 v1.2 absorption adds F48-F65 design-only items.

### `FEAT-AGENT-SERVICE-TASK-CENTRIC-CONTROL`

Layer 2 of the agent-service per-layer architecture (ADR-0138). Owns task-centric control: cursor flow + cancel re-authorization + resume/replay against Run state. This is the layer that translates client-side task semantics into runtime Run lifecycle operations. The deep-dive lives at architecture/docs/L1/agent-service/features/task-centric-control.md. + ADR-0155 v1.2 absorption adds F48-F65 design-only items.

### `FEAT-AGENT-SERVICE-TRANSLATION-TOOL-INTERCEPT`

Layer 6 of the agent-service per-layer architecture (ADR-0138). Owns model/tool translation hooks: ModelGateway, tool authz boundary, prompt shaping, response normalisation. Intercepts model invocations to enforce platform policy before they reach provider SDKs. The deep-dive lives at architecture/docs/L1/agent-service/features/translation-tool-intercept.md. + ADR-0155 v1.2 absorption adds F48-F65 design-only items.

### `FEAT-ENGINE-DISPATCH-AND-HOOKS`

Owns the engine boundary: every Run dispatch goes through EngineRegistry.resolve(envelope) against engine-envelope.v1.yaml; pattern-matching on ExecutorDefinition subtypes outside the registry is forbidden (Rule R-M.a). Cross-cutting policies (model gateway, tool authz, memory governance, tenant policy, quota, observability, sandbox routing, checkpoint, failure handling) are expressed as RuntimeMiddleware listening on canonical HookPoint events from engine-hooks.v1.yaml. The hook contract is the extension surface for new policies without modifying executors.

### `FEAT-IDEMPOTENCY-AND-REPLAY`

Owns idempotency at the public API boundary: IdempotencyHeaderFilter extracts the Idempotency-Key header and consults IdempotencyStore (Postgres-backed, NOT NULL + UNIQUE on (tenantId, key)) to either claim a fresh slot or replay the stored response. The store carries the response envelope so replay is byte-identical without re-executing the Run. Tenant isolation is enforced at the storage engine (Rule R-J).

### `FEAT-POSTURE-BOOTSTRAP`

Owns posture-aware startup: PostureBootGuard validates @RequiredConfig-annotated configuration properties before the runtime accepts traffic. In research and prod postures the boot fails closed on missing config; in dev posture it logs and allows. Posture is determined by spring.profiles.active; the guard runs in @Order(0) so misconfiguration surfaces before any framework wiring. Default posture is dev when unset, per the explicit fail-loud-on-prod-misconfig discipline of Rule D-6.

### `FEAT-RUN-LIFECYCLE-CONTROL`

Owns the public Run lifecycle surface — POST /v1/runs admission with tenant + idempotency + posture guard, POST /v1/runs/{id}/cancel re-validation and DFA transition to CANCEL_REQUESTED, GET /v1/runs/{id} tenant-scoped polling, GET /v1/runs paginated listing, and the CAS-based RunRepository.updateIfNotTerminal atomic transition that backs all of them. Run state changes are protected by the DFA in RunStateMachine; every persisted Run carries tenantId enforced by NOT NULL + RLS. Public endpoint behavior described by openapi-v1.yaml.

### `FEAT-SUSPEND-RESUME-CONTROL`

Owns Run-level suspension and resume: SuspendSignal sealed-type variants (forClientCallback, forChildRun, forRateLimit, forCheckpoint) cause Run to transition to SUSPENDED with a typed SuspendReason; ResumeDispatcher transitions back to RUNNING when the suspension condition resolves. Child-run spawn is a SuspendSignal variant — parent suspends until child reaches terminal state. The full state machine is encoded in RunStateMachine and validated by every persisted transition.

### `FEAT-TENANT-ISOLATION`

Owns tenant isolation across the surface: every tenant-scoped HTTP request cross-checks JWT.tenant claim against the IngressEnvelope.tenantId (Rule R-J); every tenant-bearing Flyway migration enables Postgres Row-Level Security on the same migration (Rule R-J.a); cross-tenant access at the cancel endpoint collapses to 404 not_found at W0 (deferred 403 widening per ADR-0108). The tenant contract is enforced at three layers: HTTP edge, repository layer, and storage engine.

## 6. DFX Requirements

DFX dimensions for `agent-service` are declared in
[`docs/dfx/agent-service.yaml`](../../../../../docs/dfx/agent-service.yaml).
Per-feature DFX deltas (if any) are tracked alongside the FEAT-
element in `architecture/features/features.dsl`.

## 7. AI Execution Boundary

Machine-readable AI boundary per feature (5 saa.aiBoundary.* sub-keys).
AI agents acting on this module MUST consult these before auto-modifying:

| Feature | Can modify code | Can modify contracts | Allowed transitions | Requires human review at | Sandbox policy |
|---|---|---|---|---|---|
| `FEAT-AGENT-SERVICE-ACCESS-LAYER` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-AGENT-SERVICE-ENGINE-DISPATCH-EXECUTION` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-AGENT-SERVICE-INTERNAL-EVENT-QUEUE` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-AGENT-SERVICE-SESSION-TASK-MANAGER` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-AGENT-SERVICE-TASK-CENTRIC-CONTROL` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-AGENT-SERVICE-TRANSLATION-TOOL-INTERCEPT` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-ENGINE-DISPATCH-AND-HOOKS` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-IDEMPOTENCY-AND-REPLAY` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-POSTURE-BOOTSTRAP` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-RUN-LIFECYCLE-CONTROL` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-SUSPEND-RESUME-CONTROL` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-TENANT-ISOLATION` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |

## 8. Verification Matrix

Tests + commands that verify each feature. AI agents MUST run these
commands after auto-modifying the feature's owning code.

### `FEAT-AGENT-SERVICE-ACCESS-LAYER`

**Verification test FQNs:**
- `com.huawei.ascend.service.runtime.api.*IT`

**Verification commands:**
- `./mvnw -pl agent-service -am verify`

### `FEAT-AGENT-SERVICE-ENGINE-DISPATCH-EXECUTION`

**Verification test FQNs:**
- `com.huawei.ascend.service.runtime.engine.*IT`

**Verification commands:**
- `./mvnw -pl agent-service -am verify`

### `FEAT-AGENT-SERVICE-INTERNAL-EVENT-QUEUE`

**Verification test FQNs:**
- `com.huawei.ascend.service.runtime.events.*Test`

**Verification commands:**
- `./mvnw -pl agent-service -am verify`

### `FEAT-AGENT-SERVICE-SESSION-TASK-MANAGER`

**Verification test FQNs:**
- `com.huawei.ascend.service.runtime.session.*IT`

**Verification commands:**
- `./mvnw -pl agent-service -am verify`

### `FEAT-AGENT-SERVICE-TASK-CENTRIC-CONTROL`

**Verification test FQNs:**
- `com.huawei.ascend.service.runtime.runs.*IT`

**Verification commands:**
- `./mvnw -pl agent-service -am verify`

### `FEAT-AGENT-SERVICE-TRANSLATION-TOOL-INTERCEPT`

**Verification test FQNs:**
- `com.huawei.ascend.service.runtime.translation.*IT`

**Verification commands:**
- `./mvnw -pl agent-service -am verify`

### `FEAT-ENGINE-DISPATCH-AND-HOOKS`

**Verification test FQNs:**
- `com.huawei.ascend.service.runtime.engine.EngineRegistryIT`
- `com.huawei.ascend.service.runtime.engine.HookDispatchTest`

**Verification commands:**
- `./mvnw -pl agent-service -am verify`
- `./mvnw -pl agent-execution-engine -am verify`

### `FEAT-IDEMPOTENCY-AND-REPLAY`

**Verification test FQNs:**
- `com.huawei.ascend.service.runtime.idempotency.IdempotencyHeaderFilterIT`
- `com.huawei.ascend.service.runtime.idempotency.IdempotencyStoreTest`

**Verification commands:**
- `./mvnw -pl agent-service -am verify`

### `FEAT-POSTURE-BOOTSTRAP`

**Verification test FQNs:**
- `com.huawei.ascend.service.platform.posture.PostureBootGuardIT`

**Verification commands:**
- `./mvnw -pl agent-service -am verify`

### `FEAT-RUN-LIFECYCLE-CONTROL`

**Verification test FQNs:**
- `com.huawei.ascend.service.platform.web.runs.RunHttpContractIT`
- `com.huawei.ascend.service.runtime.runs.RunStateMachineTest`

**Verification commands:**
- `./mvnw -pl agent-service -am verify`
- `bash gate/check_architecture_sync.sh`

### `FEAT-SUSPEND-RESUME-CONTROL`

**Verification test FQNs:**
- `com.huawei.ascend.engine.orchestration.spi.SuspendSignalTest`
- `com.huawei.ascend.engine.orchestration.spi.SuspendSignalLibraryTest`

**Verification commands:**
- `./mvnw -pl agent-service -am verify`

### `FEAT-TENANT-ISOLATION`

**Verification test FQNs:**
- `com.huawei.ascend.service.platform.security.TenantIsolationIT`
- `com.huawei.ascend.service.platform.tenant.TenantContextFilterIT`
- `com.huawei.ascend.service.platform.tenant.JwtTenantClaimCrossCheckTest`

**Verification commands:**
- `./mvnw -pl agent-service -am verify`

## 9. Lifecycle / Governance

Feature lifecycle state machine (Rule G-14):

```
proposed -> accepted -> design_only -> ready_for_impl
                                    -> implemented_unverified
                                    -> test_verified -> shipped
                                    -> deprecated -> removed
```

Current state per feature:

- `FEAT-AGENT-SERVICE-ACCESS-LAYER` — `shipped`
- `FEAT-AGENT-SERVICE-ENGINE-DISPATCH-EXECUTION` — `shipped`
- `FEAT-AGENT-SERVICE-INTERNAL-EVENT-QUEUE` — `shipped`
- `FEAT-AGENT-SERVICE-SESSION-TASK-MANAGER` — `shipped`
- `FEAT-AGENT-SERVICE-TASK-CENTRIC-CONTROL` — `shipped`
- `FEAT-AGENT-SERVICE-TRANSLATION-TOOL-INTERCEPT` — `shipped`
- `FEAT-ENGINE-DISPATCH-AND-HOOKS` — `shipped`
- `FEAT-IDEMPOTENCY-AND-REPLAY` — `shipped`
- `FEAT-POSTURE-BOOTSTRAP` — `shipped`
- `FEAT-RUN-LIFECYCLE-CONTROL` — `shipped`
- `FEAT-SUSPEND-RESUME-CONTROL` — `shipped`
- `FEAT-TENANT-ISOLATION` — `shipped`

Status transitions are governed by Rule G-14 (advisory at W1, blocking
at W5 after soak). Forward-only by default; backward transitions
require an ADR `extends:` or `relates_to:` the feature's source ADR.

