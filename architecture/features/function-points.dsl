// architecture/features/function-points.dsl
//
// Authority: ADR-0147 (Structurizr Workspace Authority).
// AUTHORED ZONE. Engineers edit this file directly.
//
// W2 lands a curated seed set of L1 function points (the visible API verbs +
// orchestration steps documented in agent-service/ARCHITECTURE.md and the
// run lifecycle scenario diagrams). Subsequent additions come from ADRs.
//
// Each profile-tagged element MUST satisfy required-properties.yaml:
//   saa.id, saa.kind, saa.level, saa.view, saa.status, saa.owner, saa.sourceAdr.
//
// Relationship semantics:
//   capability -> functionPoint : "contains" (capability owns the FP)
//   module     -> functionPoint : "implements"
//   test/enforcer -> functionPoint : "verifies"
//
// Relationship declarations live in verification.dsl to keep this file
// focused on the function-point inventory itself.

fpCreateRun = element "Create Run" "FunctionPoint" "POST /v1/runs — admit a new Run with tenant + idempotency + posture guard" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-CREATE-RUN"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0040"
        "saa.requirement" "REQ-001"
        "saa.channel" "http"
        "saa.actor" "tenant-developer"
        "saa.trigger" "tenant-developer run-admission request at the run ingress"
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/platform/web/runs/RunController.java#create"
        "saa.test_refs" "com.huawei.ascend.service.platform.web.runs.RunHttpContractIT|com.huawei.ascend.service.runtime.runs.RunStateMachineTest"
        "saa.contract_op_refs" "contract-op/createrun"
    }
}

fpCancelRun = element "Cancel Run" "FunctionPoint" "POST /v1/runs/(runId)/cancel — re-validate tenant + DFA transition to CANCEL_REQUESTED" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-CANCEL-RUN"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0108"
        "saa.requirement" "REQ-001"
        "saa.channel" "http"
        "saa.actor" "tenant-developer"
        "saa.trigger" "tenant-developer run-cancellation request at the run-control ingress"
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/platform/web/runs/RunController.java#cancel"
        "saa.test_refs" "com.huawei.ascend.service.platform.web.runs.RunHttpContractIT|com.huawei.ascend.service.runtime.runs.RunStateMachineTest"
        "saa.contract_op_refs" "contract-op/cancelrun"
    }
}

fpGetRunStatus = element "Get Run Status" "FunctionPoint" "GET /v1/runs/(runId) — tenant-scoped polling endpoint for Run state + last error" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-GET-RUN-STATUS"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0040"
        "saa.requirement" "REQ-001"
        "saa.channel" "http"
        "saa.actor" "tenant-developer"
        "saa.trigger" "tenant-developer run-status query at the run-query ingress"
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/platform/web/runs/RunController.java#get"
        "saa.test_refs" "com.huawei.ascend.service.platform.web.runs.RunHttpContractIT"
        "saa.contract_op_refs" "contract-op/getrun"
    }
}

// FP-LIST-RUNS removed by Round-2 Wave A (2026-05-28 correction request P0-1):
// no GET /v1/runs handler exists on RunController, and openapi-v1.yaml has no
// listRuns operation. The shipped status + the four hard-evidence refs were
// hallucinated by the W5 seed mount. The FP is reintroduced only when the
// list endpoint actually ships.

fpIngressEnvelope = element "Ingress Envelope Routing" "FunctionPoint" "Route IngressEnvelope from edge-plane to compute_control via IngressGateway (Rule R-I.1)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-INGRESS-ENVELOPE"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-bus"
        "saa.sourceAdr" "ADR-0089"
        "saa.requirement" "REQ-002"
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
        "saa.code_entrypoint_refs" "agent-bus/src/main/java/com/huawei/ascend/bus/spi/ingress/IngressGateway.java#routeClientRequest"
        "saa.fact_refs" "code-symbol/com-huawei-ascend-bus-spi-ingress-ingressgateway"
        "saa.no_contract_rationale" "Internal edge-plane routing SPI: IngressGateway.routeClientRequest moves an IngressEnvelope from the edge plane into compute_control in-process; the envelope + response shape is the ingress-envelope.v1.yaml contract surface. There is no external wire operation to name on the gateway itself."
    }
}

fpS2cCallback = element "S2C Callback" "FunctionPoint" "Server-to-client callback envelope via S2cCallbackTransport — Run suspends, client receives capability invocation, response resumes" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-S2C-CALLBACK"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-bus"
        "saa.sourceAdr" "ADR-0088"
        "saa.requirement" "REQ-003"
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
        "saa.code_entrypoint_refs" "agent-bus/src/main/java/com/huawei/ascend/bus/spi/s2c/S2cCallbackTransport.java#dispatch"
        "saa.fact_refs" "code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbacktransport"
        "saa.test_refs" "com.huawei.ascend.service.runtime.s2c.S2cCallbackRoundTripIT|com.huawei.ascend.service.runtime.s2c.S2cCallbackEnvelopeValidationTest"
        "saa.no_contract_rationale" "Internal server-to-client callback SPI: S2cCallbackTransport.dispatch carries a capability-invocation envelope to a suspended Run's client and resumes on the response; it is consumed in-process behind the SPI. The callback envelope + response shape is the s2c-callback contract surface. There is no external wire operation to name."
    }
}

fpRunStateTransition = element "Run State Transition" "FunctionPoint" "Atomic DFA-validated Run status transition at the RunRepository single-owner boundary — the sole sanctioned state-advance path (Rule R-C.2.b)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-RUN-STATE-TRANSITION"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0142"
        "saa.requirement" "REQ-001"
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
        "saa.no_contract_rationale" "Internal single-owner persistence boundary: the atomic DFA-validated compare-and-set Run-status advance (Rule R-C.2.b) is consumed in-process; no external wire operation."
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/runtime/runs/spi/RunRepository.java#updateIfNotTerminal"
        "saa.fact_refs" "code-symbol/com-huawei-ascend-service-runtime-runs-spi-runrepository"
        "saa.test_refs" "com.huawei.ascend.service.runtime.architecture.RunRepositoryAtomicContractTest|com.huawei.ascend.service.runtime.orchestration.RunStatusTransitionIT|com.huawei.ascend.service.runtime.runs.RunStateMachineTest"
    }
}

fpSuspendResume = element "Suspend Resume" "FunctionPoint" "Guarded Run suspend/resume control at the SuspendSignal + SuspendReason boundary (ADR-0137 + REQ-004)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-SUSPEND-RESUME"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0137"
        "saa.requirement" "REQ-004"
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
        "saa.code_entrypoint_refs" "agent-bus/src/main/java/com/huawei/ascend/bus/spi/engine/SuspendSignal.java#forClientCallback"
        "saa.fact_refs" "code-symbol/com-huawei-ascend-bus-spi-engine-suspendsignal"
        "saa.test_refs" "com.huawei.ascend.bus.spi.engine.SuspendSignalTest|com.huawei.ascend.service.runtime.orchestration.SuspendResumeWireReadinessSpikeTest"
        "saa.no_contract_rationale" "Internal in-process control flow: a SuspendSignal (raised via SuspendSignal.forClientCallback) drives the RUNNING->SUSPENDED->RUNNING transition keyed by SuspendReason; the signal + reason payload is carried on the engine-envelope.v1.yaml contract surface and consumed in-JVM. There is no external wire operation to name."
    }
}

fpChildRunSpawn = element "Child Run Spawn" "FunctionPoint" "SuspendSignal child-Run variant — parent suspends, child Run executes, parent resumes on child terminal" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-CHILD-RUN-SPAWN"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0145"
        "saa.requirement" "REQ-004"
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
        "saa.no_contract_rationale" "Internal SuspendSignal child-Run variant: the parent suspends in-process and the child Run is materialised through the single-owner RunRepository (parent->child linkage by parentRunId); there is no external wire surface to describe. The carrier shape is the engine-envelope.v1.yaml contract."
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/runtime/orchestration/inmemory/InMemoryRunRegistry.java#findByParentRunId"
        "saa.fact_refs" "code-symbol/com-huawei-ascend-service-runtime-orchestration-inmemory-inmemoryrunregistry"
        "saa.test_refs" "com.huawei.ascend.service.runtime.orchestration.NestedDualModeIT"
    }
}

fpIdempotencyClaim = element "Idempotency Claim" "FunctionPoint" "Idempotency-key claim + replay at the IdempotencyStore boundary (Rule 56 + ADR-0027)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-IDEMPOTENCY-CLAIM"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0027"
        "saa.requirement" "REQ-005"
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
        "saa.no_contract_rationale" "Internal boot-installed OncePerRequestFilter: IdempotencyHeaderFilter claims/replays an Idempotency-Key at the IdempotencyStore boundary before the request reaches the handler; the claim+replay record shape is internal to the filter, with no external wire operation to name."
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/platform/idempotency/IdempotencyHeaderFilter.java#doFilterInternal"
        "saa.fact_refs" "code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencyheaderfilter"
        "saa.test_refs" "com.huawei.ascend.service.platform.idempotency.IdempotencyHeaderFilterTest|com.huawei.ascend.service.platform.idempotency.IdempotencyHeaderFilterIT"
    }
}

fpTenantCrossCheck = element "Tenant Cross Check" "FunctionPoint" "JWT.tenant claim cross-check vs IngressEnvelope.tenantId at every tenant-scoped surface (Rule R-J)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-TENANT-CROSS-CHECK"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0040"
        "saa.requirement" "REQ-006"
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/platform/tenant/JwtTenantClaimCrossCheck.java#doFilterInternal"
        "saa.fact_refs" "code-symbol/com-huawei-ascend-service-platform-tenant-jwttenantclaimcrosscheck"
        "saa.test_refs" "com.huawei.ascend.service.platform.tenant.JwtTenantClaimCrossCheckTest|com.huawei.ascend.service.platform.tenant.TenantContextFilterIT"
        "saa.no_contract_rationale" "Internal admission filter: JwtTenantClaimCrossCheck.doFilterInternal cross-checks the validated JWT tenant_id claim against the X-Tenant-Id header before the request reaches the handler (rejects mismatch / missing-claim with 403); it is a boot-installed OncePerRequestFilter with no external wire operation to name."
    }
}

fpPostureBootGuard = element "Posture Boot Guard" "FunctionPoint" "PostureBootGuard validates @RequiredConfig at startup; research/prod fail-closed on missing config" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-POSTURE-BOOT-GUARD"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0058"
        "saa.requirement" "REQ-007"
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/platform/posture/PostureBootGuard.java#onApplicationEvent"
        "saa.fact_refs" "code-symbol/com-huawei-ascend-service-platform-posture-posturebootguard"
        "saa.test_refs" "com.huawei.ascend.service.platform.posture.PostureBootGuardIT|com.huawei.ascend.service.platform.posture.PostureBindingIT"
        "saa.no_contract_rationale" "Boot-time fail-closed guard: PostureBootGuard.onApplicationEvent runs the @RequiredConfig matrix on ApplicationReadyEvent and aborts startup under research/prod when required config is absent; it is a boot lifecycle listener, not a request surface, so there is no external wire operation to name."
    }
}

fpGraphMemoryStore = element "Graph Memory Store" "FunctionPoint" "GraphMemoryRepository tenant-scoped CRUD + semantic facts (auto-wired by starter)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-GRAPH-MEMORY-STORE"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "spring-ai-ascend-graphmemory-starter"
        "saa.sourceAdr" "ADR-0081"
        "saa.requirement" "REQ-008"
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
        "saa.no_contract_rationale" "Internal SPI auto-wired by the graphmemory starter: GraphMemoryRepository tenant-scoped CRUD is consumed in-process; the persisted record + query shape is the memory-store.v1.yaml contract surface. There is no external wire operation to name."
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/runtime/memory/spi/GraphMemoryRepository.java#query"
        "saa.fact_refs" "code-symbol/com-huawei-ascend-service-runtime-memory-spi-graphmemoryrepository"
        "saa.test_refs" "com.huawei.ascend.service.runtime.graphmemory.GraphMemoryAutoConfigurationTest|com.huawei.ascend.service.runtime.memory.spi.MemorySpiArchTest"
    }
}

fpEngineDispatch = element "Engine Dispatch" "FunctionPoint" "EngineRegistry.resolve(envelope) -> typed ExecutorAdapter dispatch via engine-envelope.v1.yaml (Rule R-M.a)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-ENGINE-DISPATCH"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-execution-engine"
        "saa.sourceAdr" "ADR-0140"
        "saa.requirement" "REQ-009"
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
        "saa.no_contract_rationale" "Internal registry resolve: EngineRegistry.resolve(envelope) selects a typed ExecutorAdapter inside the process; the envelope shape it dispatches over is the engine-envelope.v1.yaml contract surface. There is no external wire operation to name."
        "saa.code_entrypoint_refs" "agent-execution-engine/src/main/java/com/huawei/ascend/engine/runtime/EngineRegistry.java#resolve"
        "saa.fact_refs" "code-symbol/com-huawei-ascend-engine-runtime-engineregistry"
        "saa.test_refs" "com.huawei.ascend.engine.runtime.EngineRegistryResolveTest|com.huawei.ascend.engine.runtime.EnginePayloadDispatchOnlyViaRegistryTest"
    }
}

fpHookDispatch = element "Hook Dispatch" "FunctionPoint" "RuntimeMiddleware listens on canonical HookPoint events (engine-hooks.v1.yaml; Rule R-M.c)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-HOOK-DISPATCH"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-middleware"
        "saa.sourceAdr" "ADR-0073"
        "saa.requirement" "REQ-009"
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
        "saa.no_contract_rationale" "Internal hook dispatch: HookDispatcher.fire(context) drives RuntimeMiddleware listeners on canonical HookPoint events in-process; the HookPoint catalog + outcome shape is the engine-hooks.v1.yaml contract surface. There is no external wire operation to name."
        "saa.code_entrypoint_refs" "agent-middleware/src/main/java/com/huawei/ascend/middleware/HookDispatcher.java#fire"
        "saa.fact_refs" "code-symbol/com-huawei-ascend-middleware-hookdispatcher"
        "saa.test_refs" "com.huawei.ascend.middleware.HookDispatcherFireOrderTest|com.huawei.ascend.engine.runtime.RuntimeMiddlewareInterceptsHooksIT"
    }
}

// Function-point ownership + verification relationships:
//   capability -> function_point  (contains)
//   module     -> function_point  (implements)
//   test       -> function_point  (verifies)
//
// Wave 2 wires the implements relationships here. Verification edges live
// in verification.dsl as they touch the test inventory.

agentService -> fpCreateRun "owning module implements function point" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentService -> fpCancelRun "owning module implements function point" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentService -> fpGetRunStatus "owning module implements function point" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
// agentService -> fpListRuns relationship removed alongside the
// FP-LIST-RUNS element (Round-2 Wave A, 2026-05-28 correction P0-1).
agentBus -> fpIngressEnvelope "owning module implements function point" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentBus -> fpS2cCallback "owning module implements function point" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentService -> fpRunStateTransition "owning module implements function point" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentService -> fpSuspendResume "owning module implements function point" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentService -> fpChildRunSpawn "owning module implements function point" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentService -> fpIdempotencyClaim "owning module implements function point" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentService -> fpTenantCrossCheck "owning module implements function point" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentService -> fpPostureBootGuard "owning module implements function point" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
graphMemoryStarter -> fpGraphMemoryStore "owning module implements function point" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentExecutionEngine -> fpEngineDispatch "owning module implements function point" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentMiddleware -> fpHookDispatch "owning module implements function point" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}

fpA2aMessageSend = element "A2A message/send" "FunctionPoint" "A2A JSON-RPC message/send entry (M1 AL-01 ingress)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-A2A-MESSAGE-SEND"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.channel" "http"
        "saa.actor" "tenant-developer-or-peer-agent"
        "saa.trigger" "tenant-developer or peer-agent message submission at the A2A ingress"
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/platform/web/a2a/A2aMessageController.java#send"
        "saa.contract_op_refs" "access-intent.v1.yaml#operation=SUBMIT"
    }
}

fpA2aTasksCancel = element "A2A tasks/cancel" "FunctionPoint" "A2A tasks/cancel entry (M1 AL-08 control ingress)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-A2A-TASKS-CANCEL"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.channel" "http"
        "saa.actor" "tenant-developer-or-peer-agent"
        "saa.trigger" "tenant-developer or peer-agent task-cancellation request at the A2A ingress"
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/platform/web/a2a/A2aTasksController.java#cancel"
        "saa.contract_op_refs" "access-intent.v1.yaml#operation=CANCEL"
    }
}

fpA2aTasksResubscribe = element "A2A tasks/resubscribe" "FunctionPoint" "A2A tasks/resubscribe stream entry (M1 AL-06 cursor flow)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-A2A-TASKS-RESUBSCRIBE"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.channel" "http"
        "saa.actor" "tenant-developer-or-peer-agent"
        "saa.trigger" "tenant-developer or peer-agent task-resubscription stream request at the A2A ingress"
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/platform/web/a2a/A2aStreamController.java#resubscribe"
        "saa.contract_op_refs" "access-intent.v1.yaml#operation=SUBSCRIBE"
    }
}

fpMqInbound = element "MQ inbound consume" "FunctionPoint" "Outside broker to AL-02 inbound consumer (M1 v1.2)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-MQ-INBOUND"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0155"
        "saa.channel" "spi"
        "saa.actor" "external-mq-broker"
        "saa.trigger" "Broker delivery (RocketMQ / Kafka SPI)"
        "saa.code_entrypoint_refs" "agent-service/src/main/java/com/huawei/ascend/service/dispatcher/mq/MqInboundConsumer.java#onMessage"
        "saa.contract_op_refs" "access-intent.v1.yaml#operation=SUBMIT"
    }
}
