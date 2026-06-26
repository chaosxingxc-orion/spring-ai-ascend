---
affects_level: L2
affects_view: development
proposal_status: draft
authors: ["masterchubb-ctrl", "Codex"]
related_adrs: ["ADR-0158", "ADR-0159", "ADR-0161"]
related_rules: ["D-1", "R-C", "R-I", "R-L", "R-M"]
affects_artefact:
  - docs/contracts/security-decision.v1.yaml
  - docs/contracts/contract-catalog.md
  - agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/spi
  - agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/a2a
  - agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/versatile
  - agent-runtime/src/main/java/com/huawei/ascend/runtime/app/RuntimeComponents.java
  - agent-runtime/src/main/java/com/huawei/ascend/runtime/app/RuntimeApp.java
  - agent-service/src/main/java/com/huawei/ascend/service/security
---

# Agent Security Decision Contract L2 Proposal

> **Date:** 2026-06-13
> **Status:** Draft
> **Parent proposal:** `2026-06-13-agent-security-decision-chain-proposal.en.md`
> **Scope:** neutral security decision contract and the runtime-to-policy-engine boundary.
> **Review order:** this L2 proposal should be reviewed after the L1 security decision chain direction is validated, as an implementation-boundary refinement under that L1 direction.

## 0. Latest Main Alignment And Unsuitable Parts (2026-06-18)

This refresh is aligned to `origin/main@61fae167` (merge PR #301). The contract must now represent bounded chained remote A2A after `REMOTE_RESUME`, not only a single outbound remote invocation.

Key adjustments:

- `SecurityEvaluationRequest` needs trusted metadata fields for A2A remote chains: `metadataTrustSource`, `requestMetadata`, `remoteInvocationChainId`, `remoteLegIndex`, and `maxRemoteLegs`.
- `A2aRemoteAgentTarget` needs remote task/context/tool correlation: `remoteTaskId`, `remoteContextId`, `toolCallId`, `remoteInvocationChainId`, `remoteLegIndex`, and `maxRemoteLegs`.
- `REMOTE_INVOCATION_LIMIT_EXCEEDED` must be a policy-visible reason/outcome, not only an orchestration error.
- Raw A2A metadata remains evidence. It is trusted only when sourced from runtime-owned or host-validated metadata, not from model text or remote self-assertion.

Unsuitable old assumptions:

- Do not model `A2A_REMOTE_AGENT_CALL` as one isolated action without parent-chain context.
- Do not let remote metadata become tenant/user/session policy truth unless the source is trusted.
- Do not treat max remote legs as only reliability configuration; it is also a least-agency budget.

## 1. Background

The parent proposal needs one unified executable decision point across tool, file, API, MCP, remote tool catalog, A2A remote agent, Versatile workflow, memory, sandbox, model, egress, and business actions. This L2 proposal defines that decision contract.

The current architecture requires `agent-runtime` to stay neutral. It may define and call a port, but it must not depend on `agent-service` implementation classes. Therefore, the core design is:

```text
agent-runtime owns the port interface and emits SecurityEvaluationRequest
agent-service or deployer policy client implements the port
SecurityDecision returns allow/deny/ask/sandbox/approval/audit obligations
```

## 2. Scope Statement

Primary scope:

- `affects_level: L2`
- `affects_view: development`

This proposal defines:

- `docs/contracts/security-decision.v1.yaml`;
- Java-level port shape for `SecurityDecisionPort`;
- `SecurityEvaluationRequest`, `CapabilityTarget`, `SecurityDecision`, `DecisionType`, and `ActionType`;
- how framework adapters map native actions to the neutral decision contract;
- runtime failure handling and fail-closed defaults.

This proposal does not define:

- capability permission YAML details, owned by the capability permission L2;
- approval state machine and audit event stream, owned by the approval/audit L2;
- sandbox provider execution details;
- external DLP/prompt-injection vendor integration.

### 2.1 Design Boundary

This document defines the neutral runtime-to-policy contract. It does not implement the policy engine or prove that current runtime paths already call the contract.

| Boundary question | In scope | Out of scope |
|---|---|---|
| What is the goal? | Define `SecurityDecisionPort`, `SecurityEvaluationRequest`, `CapabilityTarget`, `SecurityDecision`, `DecisionType`, and `ActionType`. | Define policy authoring, approval persistence, sandbox provider APIs, IAM/JWT, or vendor scanners. |
| Where does the contract live? | A neutral runtime-side port and DTO vocabulary that can be wired by application assembly. | Runtime importing `agent-service` implementation classes or provider SDKs. |
| What does the port decide? | Whether a concrete, scoped, pre-side-effect action is allowed, denied, routed, suspended, audited, or degraded. | Global user identity, long-lived authorization grants, UI decisions, or remote internal framework state. |
| What is trusted input? | Runtime-owned context plus trusted ingress identity and redacted capability evidence. | Model text, raw request headers, framework permission flags, or remote metadata as trusted policy facts. |
| What is the implementation status? | Proposed contract and verification gates. | A claim that mainline already enforces these decisions on every adapter path. |

### 2.2 This Design Does / Does Not

This design does:

- keep the security contract small, versioned, and independent of policy implementation;
- require adapters to evaluate before delegated side effects whenever enforcement is claimed;
- require high-risk actions to fail closed when policy is missing, timed out, or returns unknown decisions;
- classify framework-native allow/ask/deny/bypass signals as evidence to map into requests;
- require ArchUnit coverage that preserves the runtime/service dependency direction.

This design does not:

- define the deployer-readable permission matrix or profile presets;
- define approval lifecycle, audit retention, or durable store schema;
- authorize multi-tenant production behavior without a trusted identity source;
- sandbox a remote runtime or control its internal tools;
- convert `TrajectoryEvent` into a policy decision object.

## 3. Root Cause / Strongest Interpretation (Rule D-1)

1. **Observed failure / motivation:** without a neutral decision contract, each adapter could make local safety decisions, producing inconsistent behavior across OpenJiuwen, AgentScope, A2A, Versatile, SDK tools, memory, sandbox, and file/API/MCP paths.
2. **Execution path:** runtime receives an agent request, framework adapter is about to call a capability, adapter builds `SecurityEvaluationRequest`, runtime calls `SecurityDecisionPort`, policy returns `SecurityDecision`, adapter enforces before side effect.
3. **Root cause:** current runtime SPI focuses on execution, while policy, permission, approval, and audit semantics are not expressed as one reusable runtime contract.
4. **Evidence:** current `AgentRuntimeHandler` is framework-neutral execution SPI; `TrajectoryEvent` is telemetry; active architecture places runtime, service, and bus in separate modules.

## 4. Proposed Design

### 4.1 Boundary Principles

- `agent-runtime` defines and calls `SecurityDecisionPort`.
- `agent-service` or a deployer-provided policy client implements `SecurityDecisionPort`.
- `agent-runtime` must not import `agent-service` implementation classes.
- The contract is synchronous at the logical boundary, but implementation may use async adapters internally.
- High-risk actions fail closed when policy is unavailable.
- Adapters must enforce the decision before the side effect is delegated.

### 4.2 Port Implementation Shape

Deployment options:

| Option | Runtime dependency | Policy location | Suitable use |
|---|---|---|---|
| in-process no-op/dev policy | runtime-local implementation | test/dev only | unit and local development |
| in-process configured policy | runtime config module | lightweight deployments | research/single process |
| HTTP/gRPC policy client | runtime client only | `agent-service` | serviceized deployments |
| custom deployer plugin | runtime port implementation | deployer module | enterprise integration |

The port boundary remains the same for all options.

### 4.3 Runtime Assembly

```mermaid
flowchart LR
    App[RuntimeApp / RuntimeComponents] --> Handler[AgentRuntimeHandler]
    Handler --> Adapter[Framework Adapter]
    Adapter --> Guard[Capability Guard]
    Guard --> Port[SecurityDecisionPort]
    Port -. implemented by .-> Local[Local Policy Implementation]
    Port -. implemented by .-> Client[Service Policy Client]
    Client --> Service[agent-service Security Policy]
    Guard --> Action[Capability Side Effect]
    Guard --> Events[Trajectory + SecurityDecisionEvent + AuditRef]
```

Assembly rules:

- `RuntimeComponents` wires a `SecurityDecisionPort` instance.
- A default dev implementation may be provided, but it must be explicit in prod.
- Framework adapters should not know which implementation is behind the port.
- If the port is missing in research/prod, high-risk capability execution is denied.

### 4.4 Port Interface

```java
public interface SecurityDecisionPort {
    CompletionStage<SecurityDecision> evaluate(SecurityEvaluationRequest request);
}
```

The method returns `CompletionStage` to avoid blocking the runtime spine. Synchronous policy engines can complete immediately.

### 4.5 SecurityEvaluationRequest

```java
public record SecurityEvaluationRequest(
        String schemaVersion,
        String securityEvaluationRequestId,
        String tenantId,
        String userId,
        String sessionId,
        String taskId,
        String agentId,
        String sourceSurface,
        String traceId,
        String spanId,
        ActionType actionType,
        CapabilityTarget target,
        RiskTier riskTier,
        TrustTier trustTier,
        Set<DataClass> dataClasses,
        SideEffect sideEffect,
        EgressScope egressScope,
        PermissionScope requestedScope,
        Object redactedPreview,
        String inputHash,
        String idempotencyKey,
        String posture,
        String policyProfile,
        String delegationEnvelopeRef,
        String metadataTrustSource,
        String remoteInvocationChainId,
        Integer remoteLegIndex,
        Integer maxRemoteLegs,
        Map<String, Object> requestMetadata,
        Instant requestedAt) {
}
```

Rules:

- `securityEvaluationRequestId` uniquely identifies one security evaluation request; it is not an NLU classification object, not the agent task id, and not an idempotency key;
- raw sensitive data must not be placed in `redactedPreview`;
- `inputHash` identifies the sensitive input without exposing it;
- `traceId` / `spanId` correlate the call chain, `decisionId` identifies the policy result, and `securityEvaluationRequestId` identifies the input object submitted to the security decision chain;
- `sourceSurface` identifies the runtime surface such as `A2A_NORTHBOUND`, `HANDLER_WRAPPER`, `HANDLER_LIFECYCLE`, `FRAMEWORK_ADAPTER`, `MEMORY_ADAPTER`, `STATE_ADAPTER`, or `A2A_REMOTE_OUTBOUND`;
- `tenantId` and `userId` must come from trusted ingress, not from model/tool self-assertion;
- `policyProfile` carries the deployer-selected preset such as `strict_allowlist`, `review_unknown`, `scoped_allowlist`, `least_agency_scoped`, or `regulated_prod`;
- `delegationEnvelopeRef` points to the least-agency boundary used for this decision; it is not an NLU classification id;
- `policyProfile` is also the deployer-selected autonomous-delegation preset; R2+ research/prod requests without a valid envelope fail closed.
- `metadataTrustSource` identifies whether request metadata came from trusted ingress, host-validated runtime state, remote A2A metadata, or untrusted model/tool output;
- `remoteInvocationChainId`, `remoteLegIndex`, and `maxRemoteLegs` are required for bounded remote A2A chains and must fail closed when absent or outside policy for `A2A_REMOTE_AGENT_CALL`;
- `requestMetadata` carries small, redacted, policy-relevant metadata only; raw request metadata is not trusted or persisted by default.

### 4.6 CapabilityTarget

```java
public sealed interface CapabilityTarget permits
        ToolTarget,
        FileTarget,
        ApiTarget,
        McpTarget,
        A2aNorthboundTarget,
        A2aRemoteAgentTarget,
        RemoteToolCatalogTarget,
        VersatileWorkflowTarget,
        RuntimeControlTarget,
        SandboxTarget,
        MemoryTarget,
        AgentStateTarget,
        ModelTarget,
        BusinessActionTarget {
}
```

Examples:

```java
public record ApiTarget(
        String capability,
        URI endpoint,
        String method,
        String host,
        String path) implements CapabilityTarget {
}

public record FileTarget(
        String capability,
        String workspaceRef,
        Path relativePath,
        String operation) implements CapabilityTarget {
}

public record A2aRemoteAgentTarget(
        String remoteAgentId,
        String capability,
        URI endpoint,
        String remoteTaskId,
        String remoteContextId,
        String toolCallId,
        String remoteInvocationChainId,
        Integer remoteLegIndex,
        Integer maxRemoteLegs,
        Set<String> declaredSkills) implements CapabilityTarget {
}

public record RemoteToolCatalogTarget(
        URI cardUrl,
        String remoteAgentId,
        String generatedToolName,
        String cardHash,
        boolean openInputSchema,
        Set<String> declaredSkillNames) implements CapabilityTarget {
}

public record VersatileWorkflowTarget(
        String workflowId,
        URI resolvedEndpoint,
        Set<String> urlVariableKeys,
        Set<String> queryKeys,
        Set<String> headerKeys,
        Set<String> inputKeys,
        Set<String> resultExtractionRules) implements CapabilityTarget {
}

public record A2aNorthboundTarget(
        String method,
        String taskId,
        URI callbackUrl,
        boolean includeArtifacts) implements CapabilityTarget {
}

public record RuntimeControlTarget(
        String operation,
        String runtimeId,
        String taskId,
        boolean includeHealthDetail) implements CapabilityTarget {
}

public record AgentStateTarget(
        String checkpointerKind,
        String keyRef,
        String operation) implements CapabilityTarget {
}
```

The target object must describe the resource that policy needs, without leaking full payloads. For generated remote tools and Versatile workflow calls, the target must include enough catalog and parameter-shape metadata for policy to decide before the LLM-facing tool or REST call is admitted. Open schemas are allowed only when paired with explicit parameter policy and redacted preview hashing.

For bounded remote A2A, the target also identifies the concrete remote leg. `remoteInvocationChainId` groups all remote legs under the parent task, `remoteLegIndex` orders the leg, and `maxRemoteLegs` records the runtime budget that policy must not exceed. If the runtime emits `REMOTE_INVOCATION_LIMIT_EXCEEDED`, the decision/event/audit path records it as the reason code for denying or terminating the next leg.

### 4.7 SecurityDecision

```java
public record SecurityDecision(
        String schemaVersion,
        String decisionId,
        String securityEvaluationRequestId,
        DecisionType decisionType,
        String policyId,
        String policyVersion,
        String policyHash,
        String policyProfile,
        String delegationEnvelopeRef,
        String profileRule,
        List<DecisionObligation> obligations,
        String reasonCode,
        String humanMessage,
        String approvalRef,
        String auditRef,
        Instant expiresAt,
        Map<String, String> attributes) {
}
```

Decision obligations:

```text
REDACT_BEFORE_MODEL
ROUTE_TO_SANDBOX
REQUIRE_APPROVAL
REQUIRE_AUDIT_RECEIPT
LIMIT_EGRESS
LIMIT_FILE_SCOPE
LIMIT_MCP_SCOPE
LIMIT_A2A_SCOPE
RECORD_SECURITY_EVENT
DENY_LOCAL_FALLBACK
RECHECK_BEFORE_RESUME
```

### 4.8 DecisionType

```text
ALLOW
ALLOW_WITH_OBLIGATIONS
DENY
ASK_USER
SUSPEND_FOR_APPROVAL
ROUTE_TO_SANDBOX
REDACT_AND_RETRY
DEGRADE_TO_READ_ONLY
```

Semantics:

| DecisionType | Meaning |
|---|---|
| `ALLOW` | execute immediately |
| `ALLOW_WITH_OBLIGATIONS` | execute after applying obligations |
| `DENY` | return typed denial before side effect |
| `ASK_USER` | lightweight user confirmation, usually dev/research |
| `SUSPEND_FOR_APPROVAL` | park action until approval result |
| `ROUTE_TO_SANDBOX` | execute through sandbox strategy |
| `REDACT_AND_RETRY` | redact and re-evaluate/retry |
| `DEGRADE_TO_READ_ONLY` | allow safe read-only fallback only |

### 4.9 ActionType

```text
INGRESS
A2A_AGENT_CARD_READ
A2A_TASK_SEND
A2A_TASK_STREAM
A2A_TASK_READ
A2A_TASK_LIST
A2A_TASK_CANCEL
A2A_TASK_SUBSCRIBE
A2A_PUSH_CONFIG
RUNTIME_START
RUNTIME_STOP
RUNTIME_HEALTH_READ
RUNTIME_TASK_CANCEL
MODEL_CALL
TOOL_CALL
API_CALL
MCP_CALL
MEMORY_READ
MEMORY_WRITE
STATE_READ
STATE_WRITE
STATE_RELEASE
SANDBOX_ACQUIRE
SANDBOX_EXEC
REMOTE_TOOL_CATALOG_ADMIT
A2A_REMOTE_AGENT_CALL
VERSATILE_WORKFLOW_CALL
EXTERNAL_EGRESS
FILE_READ
FILE_WRITE
FILE_LIST
FILE_DELETE
CODE_EXEC
BUSINESS_ACTION
FALLBACK
```

### 4.10 Current Runtime Enforcement Points

| Enforcement point | Action type | Required behavior |
|---|---|---|
| A2A northbound | `INGRESS`, `A2A_*` | validate Agent Card, send/stream/get/list/cancel/subscribe, push config, tenant/header trust, posture |
| handler lifecycle wrapper | `RUNTIME_START`, `RUNTIME_STOP`, `RUNTIME_HEALTH_READ`, `RUNTIME_TASK_CANCEL` | enforce admin/tenant/task scope before lifecycle and cancel operations |
| OpenJiuwen adapter | model/tool callbacks | enforce only when pre-action blocking is possible |
| AgentScope adapter | tool/runtime/harness calls | create `SecurityEvaluationRequest` before delegated side effect |
| SDK tool executor | `TOOL_CALL`, `API_CALL`, `FILE_*` | enforce before tool call |
| MCP adapter | `MCP_CALL` | enforce server/tool/resource scope |
| remote tool catalog | `REMOTE_TOOL_CATALOG_ADMIT` | validate remote Agent Card endpoint, generated tool name, skill description source, input schema openness, and catalog drift before exposing the tool |
| remote A2A outbound | `A2A_REMOTE_AGENT_CALL` | enforce remote endpoint and capability label |
| Versatile adapter | `VERSATILE_WORKFLOW_CALL` | enforce URL template variables, structured query/header forwarding, body input keys, result extraction rules, timeout, and continuation namespace before REST call |
| memory adapter | `MEMORY_READ`, `MEMORY_WRITE` | enforce tenant/session/data scope |
| agent state adapter | `STATE_READ`, `STATE_WRITE`, `STATE_RELEASE` | enforce checkpointer tenant/session key scope |
| sandbox gateway | `SANDBOX_ACQUIRE`, `SANDBOX_EXEC` | enforce sandbox profile, fallback, audit |
| model caller | `MODEL_CALL`, `FALLBACK` | enforce model policy and fallback equivalence |

### 4.11 Framework Adapter Contract Boundary

AgentScope, OpenJiuwen, and similar frameworks should not be required to implement this repository's policy language. Their adapters are responsible for translating native framework events into `SecurityEvaluationRequest`.

| Framework path | Adapter responsibility | Decision owner |
|---|---|---|
| OpenJiuwen declared tool | attach capability metadata and call `SecurityDecisionPort` before execution | repository |
| OpenJiuwen native callback | if pre-action and blocking, map to request; otherwise telemetry only | repository when enforceable |
| AgentScope local wrapper | map call to request before side effect | repository |
| AgentScope remote runtime client | guard remote call before delegating | repository |
| A2A remote invocation | classify as `A2A_REMOTE_AGENT_CALL` and enforce endpoint/capability policy | repository |
| opaque framework-internal side effect | deny or require wrapper/proxy/sandbox in research/prod | repository |

The repository should not trust framework-level "safe" labels without mapping them to its own contract.

Additional framework-permission rules:

- AgentScope / OpenJiuwen / JiuwenSwarm native allow / ask / deny may enter `attributes.frameworkPermission` as evidence, but cannot replace `SecurityDecision`.
- Framework bypass, permission disabled, approval override, or always-allow state must be represented in `attributes.frameworkPermissionMode`; in research/prod it triggers envelope validation or fail-closed behavior.
- `requestedScope` must be checked as a subset of the `DelegationEnvelope` referenced by `delegationEnvelopeRef`; HITL approval cannot convert an out-of-envelope request into allow.

### 4.12 Relationship To Capability Permission L2

Capability permission policy determines whether a capability selector matches allowlist, scope, risk tier, posture, and profile. This proposal defines the runtime contract used to ask for the decision.

```text
capability-permissions.yaml
  -> policy engine
  -> SecurityDecisionPort.evaluate(SecurityEvaluationRequest)
  -> SecurityDecision
```

### 4.13 Relationship To Approval/Audit L2

When `SecurityDecision.decisionType == SUSPEND_FOR_APPROVAL`, the decision must include `approvalRef` and usually `auditRef`. Approval/audit L2 owns the lifecycle of those references.

When `DecisionObligation.REQUIRE_AUDIT_RECEIPT` appears, the action must not perform a high-risk side effect until audit reserve succeeds.

### 4.14 Versioning

Rules:

- all contract objects carry `schemaVersion`;
- additive fields are allowed only when old consumers can ignore them safely;
- enum additions require contract catalog update and adapter compatibility review;
- runtime should reject unknown `DecisionType` in prod;
- policy hash should be recorded for replay and audit.

### 4.15 Failure Handling

| Failure | Required behavior |
|---|---|
| port implementation missing | deny high-risk in research/prod |
| policy engine timeout | deny high-risk; dev may warn for low-risk |
| invalid decision payload | deny and emit security event |
| unknown decision type | deny in prod |
| missing audit ref when required | deny before side effect |
| sandbox route required but sandbox unavailable | deny or suspend; no local fallback unless explicit dev override |
| approval required but approval service unavailable | deny in research/prod |
| remote tool catalog target missing card hash / endpoint / schema openness flag | deny tool admission |
| Versatile workflow target contains unscoped header/query/input/result extraction keys | deny before REST call |
| continuation target lacks trusted waiting-target metadata | deny resume |

## 5. Alternatives

| Alternative | Why rejected |
|---|---|
| put policy logic directly inside each adapter | produces divergent semantics across OpenJiuwen, AgentScope, A2A, Versatile, and SDK tools |
| make `agent-runtime` depend on `agent-service` directly | violates runtime neutrality and module direction |
| use `TrajectoryEvent` as the decision object | trajectory is telemetry, not policy contract |
| return boolean allow/deny only | cannot represent approval, sandbox routing, redaction, audit, or fallback obligations |
| treat sandbox failure as local fallback | unsafe for high-risk actions |

## 6. Verification Plan

- [ ] `SecurityDecisionSchemaTest`: validates required fields, enum values, and schema version.
- [ ] `SecurityEvaluationRequestRedactionTest`: raw credentials and PII do not enter `redactedPreview`.
- [ ] `SecurityDecisionPortPurityArchTest`: `agent-runtime` does not import `agent-service` implementation classes.
- [ ] `RuntimeComponentsPolicyWiringTest`: runtime fails closed when policy port is missing in research/prod.
- [ ] `DelegationEnvelopeRefRequiredTest`: R2+ research/prod requests fail closed when `delegationEnvelopeRef` is missing or invalid.
- [ ] `DelegationEnvelopeSubsetDecisionTest`: `requestedScope` outside the envelope is denied even when native framework permission says allow.
- [ ] `FrameworkAdapterSecurityEvaluationRequestMappingTest`: OpenJiuwen, AgentScope, and A2A adapters create expected requests.
- [ ] `RuntimeMessageSecurityEvaluationMappingTest`: requests derived from `RuntimeMessage` / `AgentExecutionContext` carry `sourceSurface`, hash, trace, and tenant/session/task refs.
- [ ] `A2aNorthboundActionTypeMappingTest`: Agent Card, send/stream/get/list/cancel/subscribe, and push config map to explicit `ActionType` values.
- [ ] `RuntimeControlActionTypeMappingTest`: `AgentRuntimeHandler.start/stop/isHealthy/cancel` maps to runtime control action types and cannot bypass tenant/task/admin scope.
- [ ] `AgentStateActionTypeMappingTest`: InMemory/Redis/OpenJiuwen checkpointer read/write/release maps to state actions.
- [ ] `FrameworkPermissionEvidenceMappingTest`: AgentScope/OpenJiuwen/JiuwenSwarm allow/ask/deny/bypass/disabled/override is mapped as evidence, not final decision.
- [ ] `RemoteToolCatalogTargetMappingTest`: generated remote A2A tool specs include endpoint, card hash, open schema flag, and declared skill names before admission.
- [ ] `A2aRemoteChainSecurityDecisionTest`: bounded remote A2A requests include metadata trust source, chain id, leg index, max legs, tool call id, and remote task/context refs; requests beyond `maxRemoteLegs` produce `REMOTE_INVOCATION_LIMIT_EXCEEDED`.
- [ ] `VersatileWorkflowTargetMappingTest`: Versatile URL variables, query/header keys, input keys, and result extraction rules map to `VersatileWorkflowTarget`.
- [ ] `InputRequiredContinuationTargetTest`: remote continuation and approval resume requests carry distinct trusted target metadata.
- [ ] `OpaqueFrameworkSideEffectDenyTest`: opaque high-risk framework side effects are denied in research/prod.
- [ ] `PolicyTimeoutFailClosedTest`: high-risk actions fail closed when policy times out.
- [ ] `AuditRefRequiredTest`: R4/R5 side effects are blocked when required audit refs are missing.

## 7. Rollout

- **Wave 1:** add `security-decision.v1.yaml` to contracts as design-only.
- **Wave 2:** add Java records/interfaces behind experimental package.
- **Wave 3:** wire a dev local implementation and policy timeout behavior.
- **Wave 4:** connect `agent-service` or deployer policy client implementation.
- **Wave 5:** enforce adapter-level coverage for OpenJiuwen, AgentScope, A2A, Versatile, SDK tools, MCP, files, memory, and sandbox.

Freeze impact:

- update contract catalog after schema acceptance;
- add ArchUnit dependency rule for runtime/service boundary;
- update architecture docs after implementation package names are finalized.

## 8. Self-Audit

| Finding | Severity | Status | Mitigation |
|---|---|---|---|
| `CompletionStage` may require adapter changes | P1 | open | allow immediate completed futures for sync implementations |
| exact package name needs review | P1 | open | keep contract under neutral runtime SPI or adjacent security package |
| external policy latency may affect runtime | P2 | open | require timeout and fail-closed behavior |
| enum growth may break old adapters | P2 | open | version contract and reject unknown prod decisions |

## Authority

- Parent proposal: `2026-06-13-agent-security-decision-chain-proposal.en.md`.
- Capability policy L2: `2026-06-13-agent-capability-permission-policy-l2-proposal.en.md`.
- Current runtime SPI: `AgentRuntimeHandler`.
- Current architecture boundary: runtime owns execution/adapters; service owns serviceized policy and durable state.
- `origin/main@61fae167`: latest refreshed main baseline for bounded chained remote A2A semantics.
