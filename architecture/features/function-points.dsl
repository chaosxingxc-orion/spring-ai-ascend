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

// FP-CREATE-RUN / FP-CANCEL-RUN / FP-GET-RUN-STATUS were downgraded shipped -> design_only
// per ADR-0159: the northbound HTTP Run API (RunController + the service.platform web layer)
// was removed when agent-service was re-founded as a serviceization façade skeleton. The HTTP
// Run verbs are a design target again; the entrypoint re-lands in a later implementation phase
// (runtime access or the serviceization façade). The code/test/contract refs below are retained
// as design intent, consistent with the other design_only function points.
fpCreateRun = element "Create Run" "FunctionPoint" "POST /v1/runs — admit a new Run with tenant + idempotency + posture guard" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-CREATE-RUN"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0020"
        "saa.channel" "http"
        "saa.actor" "tenant-developer"
        "saa.trigger" "HTTP POST /v1/runs"
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
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0108"
        "saa.channel" "http"
        "saa.actor" "tenant-developer"
        "saa.trigger" "HTTP POST /v1/runs/(runId)/cancel"
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
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0020"
        "saa.channel" "http"
        "saa.actor" "tenant-developer"
        "saa.trigger" "HTTP GET /v1/runs/(runId)"
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
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
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
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
    }
}

fpRunStateTransition = element "Run State Transition" "FunctionPoint" "RunRepository.updateIfNotTerminal CAS-based atomic Run status transition (Rule R-C.2.b)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-RUN-STATE-TRANSITION"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0118"
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
    }
}

fpSuspendResume = element "Suspend Resume" "FunctionPoint" "SuspendSignal throw, Run -> SUSPENDED, ResumeDispatcher -> RUNNING transition (sealed SuspendReason variants)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-SUSPEND-RESUME"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0137"
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
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
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
    }
}

fpIdempotencyClaim = element "Idempotency Claim" "FunctionPoint" "IdempotencyHeaderFilter -> IdempotencyStore PG-backed claim + replay (W0 schema; Rule 56 + ADR-0027)" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-IDEMPOTENCY-CLAIM"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0027"
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
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
        "saa.sourceAdr" "ADR-0056"
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
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
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
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
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
    }
}

fpEngineDispatch = element "Engine Dispatch" "FunctionPoint" "EngineDispatcher routes an EngineCommandEvent to the AgentRuntimeHandler registered for its agentId (via AgentRuntimeHandlerRegistry); single control authority; an unknown agentId converges to a terminal AGENT_ID_INVALID" "SAA FunctionPoint" {
    properties {
        "saa.id" "FP-ENGINE-DISPATCH"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-runtime"
        "saa.sourceAdr" "ADR-0140"
        "saa.channel" "internal"
        "saa.actor" "platform-runtime"
        "saa.trigger" "internal-orchestration-event"
    }
}

// Function-point ownership + verification relationships:
//   capability -> function_point  (contains)
//   module     -> function_point  (implements)
//   test       -> function_point  (verifies)
//
// Wave 2 wires the implements relationships here. Verification edges live
// in verification.dsl as they touch the test inventory.

agentService -> fpCreateRun "implements POST /v1/runs handler" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentService -> fpCancelRun "implements POST /v1/runs/(runId)/cancel handler" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentService -> fpGetRunStatus "implements GET /v1/runs/(runId) handler" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
// agentService -> fpListRuns relationship removed alongside the
// FP-LIST-RUNS element (Round-2 Wave A, 2026-05-28 correction P0-1).
agentBus -> fpIngressEnvelope "implements IngressGateway routing" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentBus -> fpS2cCallback "implements S2cCallbackTransport SPI binding" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentService -> fpRunStateTransition "implements RunRepository.updateIfNotTerminal" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentService -> fpSuspendResume "implements SuspendSignal + ResumeDispatcher" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentService -> fpChildRunSpawn "implements child-Run spawn path" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentService -> fpIdempotencyClaim "implements IdempotencyHeaderFilter + IdempotencyStore" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentService -> fpTenantCrossCheck "implements tenant cross-check filter" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentService -> fpPostureBootGuard "implements PostureBootGuard" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
graphMemoryStarter -> fpGraphMemoryStore "auto-wires GraphMemoryRepository implementations" "SAA Relationship" {
    properties {

        "saa.rel" "implements"

    }
}
agentRuntime -> fpEngineDispatch "implements EngineDispatcher + AgentRuntimeHandlerRegistry" "SAA Relationship" {
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
        "saa.trigger" "A2A JSON-RPC POST message/send"
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
        "saa.trigger" "A2A JSON-RPC POST tasks/cancel"
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
        "saa.trigger" "A2A JSON-RPC POST tasks/resubscribe"
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
