---
level: L1
view: scenarios
status: shipped
authority: "ADR-0151 (L1 Feature Registry canonical schema) + ADR-0152 (uniform L1 + W3 catalog rendering)"
---

<!-- DO NOT HAND-EDIT. Rendered from architecture/features/features.dsl by gate/lib/render_features_catalog.py. Re-emit via that script; render-idempotency is enforced by Rule G-13.b. -->

# `agent-service` — L1 Feature Catalog (9-section)

> **STATUS — agent-runtime pure rebuild (ADR-0159):** `agent-service` is now a serviceization-facade **skeleton**; its former runtime is consolidated into **`agent-runtime`**. Wherever this document references `EngineRegistry` / `ExecutorAdapter` / `EngineEnvelope` / `EngineMatchingException` / `EngineHookSurface` / `HookPoint` / `RuntimeMiddleware` / `HookDispatcher` / `resolve(envelope)`, that engine/hook design is **RETIRED / design_only / historical** — no such Java type exists. The current shipped dispatch lives in `agent-runtime`: `EngineDispatcher` -> `AgentRuntimeHandler` (routed by `agentId` via `AgentRuntimeHandlerRegistry`; unknown `agentId` -> terminal `AGENT_ID_INVALID`; single `control` write authority; egress gated by `control`), verified by `EngineDispatcherTest` / `EngineClosedLoopIntegrationTest`. See Rule R-M and `architecture/docs/L1/agent-runtime/`.

This catalog is the **rendered** human-readable view of the
`agent-service`-owned features registered in
[`architecture/features/features.dsl`](../../../../features/features.dsl).
The structured source is the DSL; this Markdown is byte-identical
on re-emit. The 9 sections follow the user-supplied L1 Feature
Catalog template (ADR-0151).

## 1. Feature Metadata

| Feature ID | Name | Status | Capability Domain |
|---|---|---|---|
| `FEAT-ENGINE-DISPATCH-AND-HOOKS` | Engine Dispatch and Hooks | `shipped` | `engine-contract` |
| `FEAT-IDEMPOTENCY-AND-REPLAY` | Idempotency and Replay | `shipped` | `idempotency-protocol` |
| `FEAT-POSTURE-BOOTSTRAP` | Posture Bootstrap | `shipped` | `posture-bootstrap` |
| `FEAT-RUN-LIFECYCLE-CONTROL` | Run Lifecycle Control | `shipped` | `runtime-run-lifecycle` |
| `FEAT-SUSPEND-RESUME-CONTROL` | Suspend and Resume Control | `shipped` | `run-suspension-orchestration` |
| `FEAT-TENANT-ISOLATION` | Tenant Isolation | `shipped` | `tenant-isolation` |

## 2. Architecture Binding

### `FEAT-ENGINE-DISPATCH-AND-HOOKS`

**Development paths:**
- `agent-service/src/main/java/com/huawei/ascend/service/runtime/engine`
- `agent-runtime/src/main/java`

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

### `FEAT-ENGINE-DISPATCH-AND-HOOKS`

Owns the engine dispatch boundary. **Current state (ADR-0159 pure rebuild):** the engine lives in `agent-runtime` — `EngineDispatcher` routes each accepted `EngineCommandEvent` to the `AgentRuntimeHandler` registered for its `agentId` (via `AgentRuntimeHandlerRegistry`); an unknown `agentId` converges to a terminal `AGENT_ID_INVALID`; the engine reports to a single `TaskControlClient` and `control` gates caller-facing egress. The pre-rebuild envelope-matching + `RuntimeMiddleware`/`HookPoint` hook design (`EngineRegistry.resolve(envelope)` against `engine-envelope.v1.yaml`; hooks from `engine-hooks.v1.yaml`) is RETIRED / `design_only` — no such Java type exists.

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
| `FEAT-ENGINE-DISPATCH-AND-HOOKS` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-IDEMPOTENCY-AND-REPLAY` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-POSTURE-BOOTSTRAP` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-RUN-LIFECYCLE-CONTROL` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-SUSPEND-RESUME-CONTROL` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |
| `FEAT-TENANT-ISOLATION` | `true` | `false` | `shipped->deprecated` | `deprecated, removed` | `docs/governance/sandbox-policies.yaml#default_policy` |

## 8. Verification Matrix

Tests + commands that verify each feature. AI agents MUST run these
commands after auto-modifying the feature's owning code.

### `FEAT-ENGINE-DISPATCH-AND-HOOKS`

**Verification test FQNs:**
- `com.huawei.ascend.runtime.engine.EngineDispatcherTest`
- `com.huawei.ascend.runtime.engine.EngineClosedLoopIntegrationTest`

**Verification commands:**
- `./mvnw -pl agent-runtime -am verify`
- `./mvnw -pl agent-runtime -am verify`

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
- `com.huawei.ascend.bus.spi.engine.SuspendSignalTest`
- `com.huawei.ascend.bus.spi.engine.SuspendSignalLibraryTest`

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

- `FEAT-ENGINE-DISPATCH-AND-HOOKS` — `shipped`
- `FEAT-IDEMPOTENCY-AND-REPLAY` — `shipped`
- `FEAT-POSTURE-BOOTSTRAP` — `shipped`
- `FEAT-RUN-LIFECYCLE-CONTROL` — `shipped`
- `FEAT-SUSPEND-RESUME-CONTROL` — `shipped`
- `FEAT-TENANT-ISOLATION` — `shipped`

Status transitions are governed by Rule G-14 (advisory at W1, blocking
at W5 after soak). Forward-only by default; backward transitions
require an ADR `extends:` or `relates_to:` the feature's source ADR.

