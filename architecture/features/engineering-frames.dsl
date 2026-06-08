// Engineering Frames — the structural axis: Module -> EngineeringFrame -> FunctionPoint.
//
// Authority: ADR-0157 (EngineeringFrame Ontology). AUTHORED zone (not generated).
// Included LAST in workspace.dsl's model block so edges to genModule_* (modules.dsl),
// fp* (function-points.dsl) and feat* (features.dsl) all resolve against
// already-declared elements.
//
// An EngineeringFrame is a durable module-internal responsibility slice that anchors
// function points. Frames are CLAIM-AGNOSTIC — product claims bind to the value axis
// (Feature), so frames declare no saa.productClaim and are outside the G-16/G-17 scans.
// Relationships: Module --contains--> EngineeringFrame --anchors--> FunctionPoint;
// Feature --traverses--> EngineeringFrame.
//
// agent-service frames are re-tagged from the ADR-0138 Layer features in features.dsl.

// ---- agent-bus (bus_state plane) ----


efS2cTransport = element "S2C Transport Frame" "EngineeringFrame" "Server-to-client callback transport — S2cCallbackTransport, S2cCallbackEnvelope, capability invocation bound to suspend/resume" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-S2C-TRANSPORT"
        "saa.kind" "engineering_frame"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "shipped"
        "saa.owner" "agent-bus"
        "saa.sourceAdr" "ADR-0157"
        "saa.structuralAxis" "true"
    }
}
genModule_agent_bus -> efS2cTransport "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}
efS2cTransport -> fpS2cCallback "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}

efChannelIsolation = element "Channel Isolation Frame" "EngineeringFrame" "Three-track physical channel isolation — control / data / rhythm traffic separation (Rule R-E)" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-CHANNEL-ISOLATION"
        "saa.kind" "engineering_frame"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-bus"
        "saa.sourceAdr" "ADR-0157"
        "saa.structuralAxis" "true"
    }
}
genModule_agent_bus -> efChannelIsolation "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

efEnginePort = element "Engine Port Frame" "EngineeringFrame" "Neutral transport-agnostic Service<->Engine boundary — EnginePort, ExecutionContext, ExecuteRequest, AgentEvent stream, DefinitionRef, EngineDescriptor; in-process / internal-RPC / A2A realizations selected by deployment form" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-ENGINE-PORT"
        "saa.kind" "engineering_frame"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-bus"
        "saa.sourceAdr" "ADR-0158"
        "saa.structuralAxis" "true"
    }
}
genModule_agent_bus -> efEnginePort "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

// EF-ORCHESTRATION-SPI re-homed from agent-runtime to agent-bus per ADR-0158
// (the neutral execution contract lives in bus.spi.engine, owned by no single engine).
efOrchestrationSpi = element "Orchestration SPI Frame" "EngineeringFrame" "Neutral execution model — Orchestrator, ExecutionContext, RunContext, SuspendSignal, Checkpointer, ExecutorDefinition, RunMode (bus.spi.engine)" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-ORCHESTRATION-SPI"
        "saa.kind" "engineering_frame"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-bus"
        "saa.sourceAdr" "ADR-0157|ADR-0158"
        "saa.structuralAxis" "true"
    }
}
genModule_agent_bus -> efOrchestrationSpi "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

// ---- agent-runtime (compute_control plane) ----

efAgentRuntimeDispatch = element "AgentRuntime Engine Dispatch Frame" "EngineeringFrame" "Engine dispatch surface — EngineDispatcher routes EngineCommandEvent to the registered AgentRuntimeHandler (engine.spi) via AgentRuntimeHandlerRegistry; single control authority through TaskControlClient" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-AGENTRUNTIME-DISPATCH"
        "saa.kind" "engineering_frame"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "shipped"
        "saa.owner" "agent-runtime"
        "saa.sourceAdr" "ADR-0157"
        "saa.structuralAxis" "true"
    }
}
genModule_agent_runtime -> efAgentRuntimeDispatch "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}
efAgentRuntimeDispatch -> fpEngineDispatch "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}

// ---- agent-client (edge plane, skeleton) ----


// ---- agent-evolve (evolution plane, skeleton) ----


// ---- graphmemory auto-config ----
// spring-ai-ascend-graphmemory-starter is NOT a governed reactor module (absent
// from the root pom + has no module-metadata.yaml), so it emits no genModule_*
// node. This structural frame is therefore homed under agent-service, which owns
// the GraphMemoryRepository SPI the starter auto-wires; saa.owner below preserves
// the starter as the frame's logical origin.


// ---- agent-service (compute_control plane) — frames re-tagged from ADR-0138 Layer
//      features per ADR-0157; element declarations live in features/features.dsl. ----

genModule_agent_service -> efAccessAdmission "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}
efAccessAdmission -> fpCreateRun "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}
efAccessAdmission -> fpGetRunStatus "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}
efAccessAdmission -> fpIdempotencyClaim "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}
efAccessAdmission -> fpTenantCrossCheck "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}
efAccessAdmission -> fpPostureBootGuard "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}
efAccessAdmission -> fpA2aMessageSend "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}
efAccessAdmission -> fpA2aTasksCancel "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}
efAccessAdmission -> fpA2aTasksResubscribe "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}
efAccessAdmission -> fpMqInbound "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}

genModule_agent_service -> efTaskControl "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}
efTaskControl -> fpCancelRun "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}
efTaskControl -> fpSuspendResume "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}
efTaskControl -> fpChildRunSpawn "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}

genModule_agent_service -> efSessionTaskState "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}
efSessionTaskState -> fpRunStateTransition "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}

genModule_agent_service -> efEngineDispatch "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

genModule_agent_service -> efInternalEventQueue "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

genModule_agent_service -> efTranslationIntercept "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

// ---- value axis x structural axis: Feature --traverses--> EngineeringFrame ----
// A value demand (Feature) is read as a route across the structural map. ADR-0157 §2.
// feat* are declared in features.dsl (included earlier); ef* span this file + features.dsl.

featRunLifecycleControl -> efAccessAdmission "feature traverses engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "traverses"
    }
}
featRunLifecycleControl -> efTaskControl "feature traverses engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "traverses"
    }
}
featRunLifecycleControl -> efSessionTaskState "feature traverses engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "traverses"
    }
}
featSuspendResumeControl -> efTaskControl "feature traverses engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "traverses"
    }
}
featSuspendResumeControl -> efOrchestrationSpi "feature traverses engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "traverses"
    }
}
featIdempotencyAndReplay -> efAccessAdmission "feature traverses engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "traverses"
    }
}
featTenantIsolation -> efAccessAdmission "feature traverses engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "traverses"
    }
}
featPostureBootstrap -> efAccessAdmission "feature traverses engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "traverses"
    }
}
featServerClientCallback -> efS2cTransport "feature traverses engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "traverses"
    }
}
featEngineDispatchAndHooks -> efEngineDispatch "feature traverses engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "traverses"
    }
}
featEngineDispatchAndHooks -> efAgentRuntimeDispatch "feature traverses engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "traverses"
    }
}
featEngineDispatchAndHooks -> efTranslationIntercept "feature traverses engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "traverses"
    }
}
featEngineDispatchAndHooks -> efEnginePort "feature traverses engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "traverses"
    }
}

