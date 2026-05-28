// architecture/features/features.dsl
//
// Authority: ADR-0152 (L1 Feature Registry Canonical Schema, W1).
// AUTHORED ZONE. Engineers add new features by editing here directly.
//
// Each FEAT- element MUST satisfy required-properties.yaml#SAA_Feature:
//   common  : saa.id, saa.kind, saa.level, saa.view, saa.status
//   tag     : saa.owner, saa.sourceAdr, saa.capabilityDomain, saa.synopsis,
//             saa.aiBoundary.canModifyCode, saa.aiBoundary.canModifyContracts,
//             saa.aiBoundary.allowedStatusTransitions,
//             saa.aiBoundary.requiresHumanReviewAt,
//             saa.aiBoundary.sandboxPolicyRef
//   optional: saa.devPaths, saa.goals, saa.nonGoals,
//             saa.verificationTestFqns, saa.verificationCommands
//
// Lifecycle states (Rule G-14):
//   proposed -> accepted -> design_only -> ready_for_impl
//            -> implemented_unverified -> test_verified -> shipped
//            -> deprecated -> removed
//
// Pipe (|) is the list separator for properties that encode multiple values.

featRunLifecycleControl = element "Run Lifecycle Control" "Feature" "Public Run lifecycle API + state machine" "SAA Feature" {
    properties {
        "saa.id" "FEAT-RUN-LIFECYCLE-CONTROL"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0020"
        "saa.capabilityDomain" "runtime-run-lifecycle"
        "saa.synopsis" "Owns the public Run lifecycle surface — POST /v1/runs admission with tenant + idempotency + posture guard, POST /v1/runs/{id}/cancel re-validation and DFA transition to CANCEL_REQUESTED, GET /v1/runs/{id} tenant-scoped polling, GET /v1/runs paginated listing, and the CAS-based RunRepository.updateIfNotTerminal atomic transition that backs all of them. Run state changes are protected by the DFA in RunStateMachine; every persisted Run carries tenantId enforced by NOT NULL + RLS. Public endpoint behavior described by openapi-v1.yaml."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/runs|agent-service/src/main/java/com/huawei/ascend/service/runtime/state"
        "saa.goals" "Public Run lifecycle API with strict tenant isolation|Atomic CAS-based state transitions with no lost updates|Idempotent admission via IdempotencyStore"
        "saa.nonGoals" "Long-poll or websocket subscriptions (use cursor flow)|Cross-tenant aggregation"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.runs.RunsControllerIT|com.huawei.ascend.service.runtime.runs.RunStateMachineTest"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify|bash gate/check_architecture_sync.sh"
    }
}

featEdgeComputeIngress = element "Edge to Compute Ingress" "Feature" "Edge-plane to compute_control ingress routing via IngressGateway" "SAA Feature" {
    properties {
        "saa.id" "FEAT-EDGE-COMPUTE-INGRESS"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "process"
        "saa.status" "design_only"
        "saa.owner" "agent-bus"
        "saa.sourceAdr" "ADR-0049"
        "saa.capabilityDomain" "edge-compute-routing"
        "saa.synopsis" "Owns the edge-plane to compute_control ingress channel: every edge-originated request reaches the runtime exclusively through IngressGateway SPI, with the wire envelope governed by ingress-envelope.v1.yaml. Rule R-I.1 forbids edge modules from directly importing service/engine/middleware production code; the gateway is the single hop. W1 enforcement is ArchUnit-only at design_only status; full runtime adapter ships when the agent-client SDK lands (post-W3 per ADR-0049)."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "design_only->ready_for_impl|ready_for_impl->implemented_unverified|implemented_unverified->test_verified|test_verified->shipped"
        "saa.aiBoundary.requiresHumanReviewAt" "test_verified|shipped|deprecated"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-bus/src/main/java/com/huawei/ascend/bus/spi/ingress"
        "saa.goals" "Single ingress hop from edge to compute_control|Wire envelope governed by versioned contract"
        "saa.nonGoals" "Provider-specific transport adapters (SDK concern)"
        "saa.verificationTestFqns" "com.huawei.ascend.bus.spi.ingress.EdgeToComputeDirectLinkArchTest"
        "saa.verificationCommands" "./mvnw -pl agent-bus -am verify|bash gate/check_architecture_sync.sh"
    }
}

featServerClientCallback = element "Server to Client Callback" "Feature" "S2C callback envelope + transport for capability invocation" "SAA Feature" {
    properties {
        "saa.id" "FEAT-SERVER-CLIENT-CALLBACK"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-bus"
        "saa.sourceAdr" "ADR-0088"
        "saa.capabilityDomain" "s2c-callback-protocol"
        "saa.synopsis" "Owns the server-to-client callback path: when a Run needs a client-side capability invocation, the runtime emits S2cCallbackEnvelope through S2cCallbackTransport SPI; the calling Run suspends via SuspendSignal.forClientCallback and the client response (validated against s2c-callback.v1.yaml) resumes the Run. The transport SPI lives in agent-bus to keep service free of edge-direction transport concerns; the envelope schema is the runtime promise surface."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-bus/src/main/java/com/huawei/ascend/bus/spi/s2c|agent-service/src/main/java/com/huawei/ascend/service/runtime/s2c"
        "saa.goals" "Asynchronous capability invocation without holding client connection|Wire envelope versioned and validated"
        "saa.nonGoals" "Long-poll fallback (cursor flow handles polling)"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.s2c.S2cCallbackIT"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

featSuspendResumeControl = element "Suspend and Resume Control" "Feature" "SuspendSignal lifecycle and ResumeDispatcher orchestration" "SAA Feature" {
    properties {
        "saa.id" "FEAT-SUSPEND-RESUME-CONTROL"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "process"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0058"
        "saa.capabilityDomain" "run-suspension-orchestration"
        "saa.synopsis" "Owns Run-level suspension and resume: SuspendSignal sealed-type variants (forClientCallback, forChildRun, forRateLimit, forCheckpoint) cause Run to transition to SUSPENDED with a typed SuspendReason; ResumeDispatcher transitions back to RUNNING when the suspension condition resolves. Child-run spawn is a SuspendSignal variant — parent suspends until child reaches terminal state. The full state machine is encoded in RunStateMachine and validated by every persisted transition."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/suspend"
        "saa.goals" "Typed suspension reasons|Resume without restart|Parent/child Run choreography"
        "saa.nonGoals" "Thread.sleep blocking suspension (Rule R-H forbids it)"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.suspend.SuspendResumeIT|com.huawei.ascend.service.runtime.suspend.ChildRunSpawnIT"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

featIdempotencyAndReplay = element "Idempotency and Replay" "Feature" "Idempotency claim + replay on duplicate request" "SAA Feature" {
    properties {
        "saa.id" "FEAT-IDEMPOTENCY-AND-REPLAY"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0027"
        "saa.capabilityDomain" "idempotency-protocol"
        "saa.synopsis" "Owns idempotency at the public API boundary: IdempotencyHeaderFilter extracts the Idempotency-Key header and consults IdempotencyStore (Postgres-backed, NOT NULL + UNIQUE on (tenantId, key)) to either claim a fresh slot or replay the stored response. The store carries the response envelope so replay is byte-identical without re-executing the Run. Tenant isolation is enforced at the storage engine (Rule R-J)."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/idempotency"
        "saa.goals" "Idempotent admission with byte-identical replay|Tenant-scoped key namespace"
        "saa.nonGoals" "Cross-tenant key sharing"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.idempotency.IdempotencyHeaderFilterIT|com.huawei.ascend.service.runtime.idempotency.IdempotencyStoreTest"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

featTenantIsolation = element "Tenant Isolation" "Feature" "Tenant claim cross-check + storage-engine RLS" "SAA Feature" {
    properties {
        "saa.id" "FEAT-TENANT-ISOLATION"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0030"
        "saa.capabilityDomain" "tenant-isolation"
        "saa.synopsis" "Owns tenant isolation across the surface: every tenant-scoped HTTP request cross-checks JWT.tenant claim against the IngressEnvelope.tenantId (Rule R-J); every tenant-bearing Flyway migration enables Postgres Row-Level Security on the same migration (Rule R-J.a); cross-tenant access at the cancel endpoint collapses to 404 not_found at W0 (deferred 403 widening per ADR-0108). The tenant contract is enforced at three layers: HTTP edge, repository layer, and storage engine."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/platform/tenant|agent-service/src/main/resources/db/migration"
        "saa.goals" "Defense in depth at HTTP / repo / storage layers"
        "saa.nonGoals" "Cross-tenant aggregation (handled by separate analytics surface)"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.runs.CancelRunCrossTenantIT|com.huawei.ascend.service.platform.tenant.TenantClaimFilterTest"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

featPostureBootstrap = element "Posture Bootstrap" "Feature" "Required-config posture validation at startup" "SAA Feature" {
    properties {
        "saa.id" "FEAT-POSTURE-BOOTSTRAP"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "physical"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0055"
        "saa.capabilityDomain" "posture-bootstrap"
        "saa.synopsis" "Owns posture-aware startup: PostureBootGuard validates @RequiredConfig-annotated configuration properties before the runtime accepts traffic. In research and prod postures the boot fails closed on missing config; in dev posture it logs and allows. Posture is determined by spring.profiles.active; the guard runs in @Order(0) so misconfiguration surfaces before any framework wiring. Default posture is dev when unset, per the explicit fail-loud-on-prod-misconfig discipline of Rule D-6."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/platform/posture"
        "saa.goals" "Fail-closed posture in research/prod|Visible misconfig at startup"
        "saa.nonGoals" "Hot-reload of @RequiredConfig values"
        "saa.verificationTestFqns" "com.huawei.ascend.service.platform.posture.PostureBootGuardIT"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

featGraphMemory = element "Graph Memory" "Feature" "Tenant-scoped graph memory store with auto-wiring" "SAA Feature" {
    properties {
        "saa.id" "FEAT-GRAPH-MEMORY"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "graphmemory-starter"
        "saa.sourceAdr" "ADR-0064"
        "saa.capabilityDomain" "graph-memory"
        "saa.synopsis" "Owns graph-shaped tenant memory: GraphMemoryRepository provides tenant-scoped CRUD over graph nodes and edges, with semantic-fact extraction for agent-side reasoning. The starter auto-wires the repository when configured; the storage backend is pluggable through GraphMemoryStore SPI. W1 ships the SPI surface as design_only; the production backend (vector index + relational graph) lands in a subsequent wave once the SPI is stable across two consumers."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "design_only->ready_for_impl|ready_for_impl->implemented_unverified|implemented_unverified->test_verified|test_verified->shipped"
        "saa.aiBoundary.requiresHumanReviewAt" "test_verified|shipped|deprecated"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "graphmemory-starter/src/main/java"
        "saa.goals" "Tenant-scoped graph reasoning surface|Pluggable backend"
        "saa.nonGoals" "Cross-tenant graph queries|Single-backend lock-in"
        "saa.verificationTestFqns" "com.huawei.ascend.graphmemory.GraphMemoryStoreTest"
        "saa.verificationCommands" "./mvnw -pl graphmemory-starter -am verify"
    }
}

featEngineDispatchAndHooks = element "Engine Dispatch and Hooks" "Feature" "Typed engine envelope dispatch + middleware hook events" "SAA Feature" {
    properties {
        "saa.id" "FEAT-ENGINE-DISPATCH-AND-HOOKS"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "process"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0088"
        "saa.capabilityDomain" "engine-contract"
        "saa.synopsis" "Owns the engine boundary: every Run dispatch goes through EngineRegistry.resolve(envelope) against engine-envelope.v1.yaml; pattern-matching on ExecutorDefinition subtypes outside the registry is forbidden (Rule R-M.a). Cross-cutting policies (model gateway, tool authz, memory governance, tenant policy, quota, observability, sandbox routing, checkpoint, failure handling) are expressed as RuntimeMiddleware listening on canonical HookPoint events from engine-hooks.v1.yaml. The hook contract is the extension surface for new policies without modifying executors."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/engine|agent-execution-engine/src/main/java"
        "saa.goals" "Typed engine dispatch + hook-based middleware|Versioned contracts at the boundary"
        "saa.nonGoals" "Per-executor instanceof checks outside the registry"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.engine.EngineRegistryIT|com.huawei.ascend.service.runtime.engine.HookDispatchTest"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify|./mvnw -pl agent-execution-engine -am verify"
    }
}

// Relationship declarations: feature -> capability (contained_by), feature -> functionPoint (contains).
// Capability identifiers (`cap_*`) are defined in features/capabilities.dsl;
// function-point identifiers (`fp*`) are defined in features/function-points.dsl.
// !include order in workspace.dsl ensures both producers come before this consumer.

featRunLifecycleControl -> fpCreateRun "Run lifecycle contains create" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}
featRunLifecycleControl -> fpCancelRun "Run lifecycle contains cancel" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}
featRunLifecycleControl -> fpGetRunStatus "Run lifecycle contains status polling" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}
featRunLifecycleControl -> fpListRuns "Run lifecycle contains listing" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}
featRunLifecycleControl -> fpRunStateTransition "Run lifecycle contains CAS state transition" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

featEdgeComputeIngress -> fpIngressEnvelope "ingress feature contains envelope routing" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

featServerClientCallback -> fpS2cCallback "S2C callback feature contains the envelope" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

featSuspendResumeControl -> fpSuspendResume "suspend/resume feature contains the SuspendSignal lifecycle" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}
featSuspendResumeControl -> fpChildRunSpawn "suspend/resume feature contains child-run choreography" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

featIdempotencyAndReplay -> fpIdempotencyClaim "idempotency feature contains the claim and replay" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

featTenantIsolation -> fpTenantCrossCheck "tenant isolation feature contains the cross-check" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

featPostureBootstrap -> fpPostureBootGuard "posture bootstrap feature contains the boot guard" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

featGraphMemory -> fpGraphMemoryStore "graph memory feature contains the store" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

featEngineDispatchAndHooks -> fpEngineDispatch "engine feature contains dispatch" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}
featEngineDispatchAndHooks -> fpHookDispatch "engine feature contains hook dispatch" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

// =============================================================================
// W4 — agent-service deep-dive feature catalog (rc55 per-layer features).
// =============================================================================
// These FEAT- elements map 1:1 to the deep-dive markdown files under
// architecture/docs/L1/agent-service/features/. They are a DIFFERENT
// abstraction level than the cross-cutting FEAT- elements above (FEAT-RUN-LIFECYCLE-CONTROL
// etc.) — these are agent-service-internal architectural layers per ADR-0138.

featAgentServiceAccessLayer = element "Agent Service Access Layer" "Feature" "Protocol convergence + tenant/auth/idempotency + client capability publication" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AGENT-SERVICE-ACCESS-LAYER"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "development"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0138"
        "saa.capabilityDomain" "agent-service-access-layer"
        "saa.synopsis" "Layer 1 of the agent-service per-layer architecture (ADR-0138). Owns protocol convergence (HTTP / gRPC / WebSocket), tenant + auth binding (JWT.tenant cross-check, IdempotencyHeaderFilter), client capability publication via OpenAPI surface, and ingress for cursor / cancel / resume / S2C callback. Does NOT own Run aggregate state, Task control state, Session context state, engine dispatch, or model/tool translation. The deep-dive feature inventory (AS-L1-F01..F08) lives at architecture/docs/L1/agent-service/features/access-layer.md."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/api"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.api.*IT"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

featAgentServiceEngineDispatchExecution = element "Agent Service Engine Dispatch Execution" "Feature" "Engine adapter + executor dispatch (Layer 4)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AGENT-SERVICE-ENGINE-DISPATCH-EXECUTION"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "development"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0138"
        "saa.capabilityDomain" "agent-service-engine-dispatch"
        "saa.synopsis" "Layer 4 of the agent-service per-layer architecture (ADR-0138). Owns engine-adapter dispatch (EngineRegistry.resolve(envelope) → typed ExecutorAdapter) and the executor invocation pathway that drives the Run state machine. The deep-dive inventory lives at architecture/docs/L1/agent-service/features/engine-dispatch-execution.md. Cross-cutting policies expressed as RuntimeMiddleware hooks (see FEAT-ENGINE-DISPATCH-AND-HOOKS for the cross-module Engine Contract feature)."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/engine"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.engine.*IT"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

featAgentServiceInternalEventQueue = element "Agent Service Internal Event Queue" "Feature" "Internal event queue infrastructure (Layer 5)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AGENT-SERVICE-INTERNAL-EVENT-QUEUE"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "development"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0138"
        "saa.capabilityDomain" "agent-service-event-queue"
        "saa.synopsis" "Layer 5 of the agent-service per-layer architecture (ADR-0138). Owns in-process event queue infrastructure used by the runtime to decouple emit-side from consume-side concerns. The deep-dive lives at architecture/docs/L1/agent-service/features/internal-event-queue.md. This is the runtime's internal eventing primitive; cross-service eventing flows through agent-bus three-track channels (control/data/rhythm)."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/events"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.events.*Test"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

featAgentServiceSessionTaskManager = element "Agent Service Session Task Manager" "Feature" "Session + Task lifecycle (Layer 3)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AGENT-SERVICE-SESSION-TASK-MANAGER"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "development"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0138"
        "saa.capabilityDomain" "agent-service-session-task-manager"
        "saa.synopsis" "Layer 3 of the agent-service per-layer architecture (ADR-0138). Owns Session and Task aggregate lifecycles — the entities above Run that group runs into user-visible interactions. Task state machine governs admission / suspension / completion at the user-interaction level; Session state machine groups tasks into a conversation context. The deep-dive lives at architecture/docs/L1/agent-service/features/session-task-manager.md."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/session"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.session.*IT"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

featAgentServiceTaskCentricControl = element "Agent Service Task Centric Control" "Feature" "Task-centric control plane (Layer 2)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AGENT-SERVICE-TASK-CENTRIC-CONTROL"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "development"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0138"
        "saa.capabilityDomain" "agent-service-task-control"
        "saa.synopsis" "Layer 2 of the agent-service per-layer architecture (ADR-0138). Owns task-centric control: cursor flow + cancel re-authorization + resume/replay against Run state. This is the layer that translates client-side task semantics into runtime Run lifecycle operations. The deep-dive lives at architecture/docs/L1/agent-service/features/task-centric-control.md."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/runs"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.runs.*IT"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

featAgentServiceTranslationToolIntercept = element "Agent Service Translation Tool Intercept" "Feature" "Model/tool translation + intercept hooks (Layer 6)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AGENT-SERVICE-TRANSLATION-TOOL-INTERCEPT"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "development"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0138"
        "saa.capabilityDomain" "agent-service-translation-tool"
        "saa.synopsis" "Layer 6 of the agent-service per-layer architecture (ADR-0138). Owns model/tool translation hooks: ModelGateway, tool authz boundary, prompt shaping, response normalisation. Intercepts model invocations to enforce platform policy before they reach provider SDKs. The deep-dive lives at architecture/docs/L1/agent-service/features/translation-tool-intercept.md."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/translation"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.translation.*IT"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

featAgentServiceAccessLayer = element "AgentService Access Layer" "Feature" "L1 Access Layer module per PR 92 v1.2 (M1)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AS-ACCESS-LAYER"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.capabilityDomain" "runtime-access-layer"
        "saa.synopsis" "M1 Access Layer per PR 92 v1.2: A2A Server + MQ ingress + AccessIntent normalisation + ingress governance chain + sync/SSE/MQ-reply/push-notification projections. Does not hold execution state."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "design_only->ready_for_impl|design_only->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "ready_for_impl|test_verified|deprecated"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/platform/web|agent-service/src/main/java/com/huawei/ascend/service/dispatcher"
        "saa.goals" "Protocol convergence (A2A + MQ -> AccessIntent)|Ingress governance (authn + tenant + idempotency + deadline + trace)|Out-projection (sync + SSE + MQ-reply + push notification)"
        "saa.nonGoals" "Run state ownership|Execution dispatch|Prompt construction"
    }
}

featAgentServiceSessionTaskManager = element "AgentService Session Task Manager" "Feature" "L1 STM module per PR 92 v1.2 (M2)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AS-SESSION-TASK-MANAGER"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.capabilityDomain" "runtime-state"
        "saa.synopsis" "M2 Session Task Manager per PR 92 v1.2: Run state machine (sole machine), Session append-only context, Task stable-ID multi-attempt model, Checkpoint with side-effect annotation, ConfigSnapshot, IdempotencyRecord, RunEventLog with monotonic cursor."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "design_only->ready_for_impl|design_only->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "ready_for_impl|test_verified|deprecated"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/runs|agent-service/src/main/java/com/huawei/ascend/service/session|agent-service/src/main/java/com/huawei/ascend/service/task"
        "saa.goals" "Run state machine sole authority|Session append-only context|Task stable-ID multi-attempt"
        "saa.nonGoals" "Execution|Prompt construction|Resource interception"
    }
}

featAgentServiceInternalEventQueue = element "AgentService Internal Event Queue" "Feature" "L1 IEQ module per PR 92 v1.2 (M3)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AS-INTERNAL-EVENT-QUEUE"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.capabilityDomain" "runtime-event-queue"
        "saa.synopsis" "M3 Internal Event Queue per PR 92 v1.2: three-channel topology (Control / Data / Egress) with independent bounded buffer + worker pool + back-pressure. In-memory v1; SPI hooks for persistence."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "design_only->ready_for_impl|design_only->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "ready_for_impl|test_verified|deprecated"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/eventqueue"
        "saa.goals" "Three-channel physical isolation|Bounded buffer with explicit rejection|Per-Run causality via STM-09 cursor"
        "saa.nonGoals" "Cross-channel ordering guarantees|State machine arbitration"
    }
}

featAgentServiceTaskCentricControl = element "AgentService Task-Centric Control" "Feature" "L1 TCC module per PR 92 v1.2 (M4)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AS-TASK-CENTRIC-CONTROL"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.capabilityDomain" "runtime-control"
        "saa.synopsis" "M4 Task-Centric Control per PR 92 v1.2: per-Run virtual-actor serialisation, sole STM-03 driver, CANCEL_RACE deterministic arbitration, RESUME_ACCEPTED handler, retry/checkpoint policy."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "design_only->ready_for_impl|design_only->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "ready_for_impl|test_verified|deprecated"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/control"
        "saa.goals" "Sole STM-03 driver|per-Run serial decisions|CANCEL_RACE_RESOLVED determinism|RESUME_ACCEPTED handler"
        "saa.nonGoals" "Prompt construction|Resource invocation|Session-context writes (only via STM)"
    }
}

featAgentServiceEngineDispatchExecution = element "AgentService Engine Dispatch Execution" "Feature" "L1 EDE module per PR 92 v1.2 (M5)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AS-ENGINE-DISPATCH-EXECUTION"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.capabilityDomain" "runtime-engine-dispatch"
        "saa.synopsis" "M5 Engine Dispatch Execution per PR 92 v1.2: ExecutorAdapter SPI for Native / Third-party / Remote forms, Engine instance cache, InjectionMode declaration, structured ErrorClass emission (14 values)."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "design_only->ready_for_impl|design_only->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "ready_for_impl|test_verified|deprecated"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/executor/spi|agent-service/src/main/java/com/huawei/ascend/service/engine"
        "saa.goals" "ExecutorAdapter SPI three-form unification|InjectionMode wiring declaration|14-value ErrorClass taxonomy|Engine instance cache"
        "saa.nonGoals" "Run state writes|Prompt construction|Direct vendor SDK calls"
    }
}

featAgentServiceTranslationToolIntercept = element "AgentService Translation Tool Intercept" "Feature" "L1 TTI module per PR 92 v1.2 (M6)" "SAA Feature" {
    properties {
        "saa.id" "FEAT-AS-TRANSLATION-TOOL-INTERCEPT"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.capabilityDomain" "runtime-resource-intercept"
        "saa.synopsis" "M6 Translation Tool Intercept per PR 92 v1.2: messages-in-flight boundary aspect (does NOT construct prompts; v1.2 reversal). Platform intercept SPI surface (4 SPIs). 5 resource-call kinds: model / tool / memory / RAG / client-hosted skill. Remote agent internal calls outside jurisdiction."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "design_only->ready_for_impl|design_only->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "ready_for_impl|test_verified|deprecated"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/intercept/spi|agent-service/src/main/java/com/huawei/ascend/service/runtime/intercept"
        "saa.goals" "Messages-in-flight boundary treatment|Platform intercept SPI surface|HITL interrupt gateway|A2A outbound audit (remote boundary)"
        "saa.nonGoals" "Prompt construction (Agent owns this per v1.2 reversal)|Session-context ownership"
    }
}
