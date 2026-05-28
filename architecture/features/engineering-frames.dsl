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

efIngressGateway = element "Ingress Gateway Frame" "EngineeringFrame" "Edge->compute_control ingress normalization and routing (IngressGateway, IngressEnvelope, IngressResponse)" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-INGRESS-GATEWAY"
        "saa.kind" "engineering_frame"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "shipped"
        "saa.owner" "agent-bus"
        "saa.sourceAdr" "ADR-0157"
        "saa.structuralAxis" "true"
    }
}
genModule_agent_bus -> efIngressGateway "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}
efIngressGateway -> fpIngressEnvelope "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}

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

// ---- agent-execution-engine (compute_control plane) ----

efEngineRegistry = element "Engine Registry Frame" "EngineeringFrame" "Engine contract surface — EngineRegistry strict matching, EngineEnvelope, ExecutorAdapter lifecycle, EngineHookSurface" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-ENGINE-REGISTRY"
        "saa.kind" "engineering_frame"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "shipped"
        "saa.owner" "agent-execution-engine"
        "saa.sourceAdr" "ADR-0157"
        "saa.structuralAxis" "true"
    }
}
genModule_agent_execution_engine -> efEngineRegistry "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}
efEngineRegistry -> fpEngineDispatch "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}

efOrchestrationSpi = element "Orchestration SPI Frame" "EngineeringFrame" "Orchestration SPI — Orchestrator, RunContext, SuspendSignal, Checkpointer, ExecutorDefinition, RunMode" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-ORCHESTRATION-SPI"
        "saa.kind" "engineering_frame"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "shipped"
        "saa.owner" "agent-execution-engine"
        "saa.sourceAdr" "ADR-0157"
        "saa.structuralAxis" "true"
    }
}
genModule_agent_execution_engine -> efOrchestrationSpi "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

// ---- agent-middleware (compute_control plane) ----

efHookSurface = element "Hook Surface Frame" "EngineeringFrame" "Runtime middleware hook dispatch — HookPoint, RuntimeMiddleware, HookDispatcher, HookContext, HookOutcome" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-HOOK-SURFACE"
        "saa.kind" "engineering_frame"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "shipped"
        "saa.owner" "agent-middleware"
        "saa.sourceAdr" "ADR-0157"
        "saa.structuralAxis" "true"
    }
}
genModule_agent_middleware -> efHookSurface "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}
efHookSurface -> fpHookDispatch "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}

efCapabilitySpi = element "Capability SPI Frame" "EngineeringFrame" "Cross-cutting capability SPI families — model gateway, skill/tool, memory, vector/retrieval/embedding, prompt/advisor" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-CAPABILITY-SPI"
        "saa.kind" "engineering_frame"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-middleware"
        "saa.sourceAdr" "ADR-0157"
        "saa.structuralAxis" "true"
    }
}
genModule_agent_middleware -> efCapabilitySpi "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

// ---- agent-client (edge plane, skeleton) ----

efClientIngressAdapter = element "Client Ingress Adapter Frame" "EngineeringFrame" "Edge SDK invocation + capability publication + cursor/SSE/webhook consumption (skeleton, W3+)" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-CLIENT-INGRESS-ADAPTER"
        "saa.kind" "engineering_frame"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-client"
        "saa.sourceAdr" "ADR-0157"
        "saa.structuralAxis" "true"
    }
}
genModule_agent_client -> efClientIngressAdapter "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

// ---- agent-evolve (evolution plane, skeleton) ----

efEvolutionExport = element "Evolution Export Frame" "EngineeringFrame" "RunEvent / trajectory export scope + online evaluation hooks (EvolutionExport discriminator, skeleton)" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-EVOLUTION-EXPORT"
        "saa.kind" "engineering_frame"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "agent-evolve"
        "saa.sourceAdr" "ADR-0157"
        "saa.structuralAxis" "true"
    }
}
genModule_agent_evolve -> efEvolutionExport "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}

// ---- spring-ai-ascend-graphmemory-starter (bus_state plane) ----

efGraphmemoryAutoconfig = element "Graph Memory Auto-Config Frame" "EngineeringFrame" "Spring Boot starter auto-configuration wiring GraphMemoryRepository SPI to backend adapter (disabled by default)" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-GRAPHMEMORY-AUTOCONFIG"
        "saa.kind" "engineering_frame"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "spring-ai-ascend-graphmemory-starter"
        "saa.sourceAdr" "ADR-0157"
        "saa.structuralAxis" "true"
    }
}
genModule_spring_ai_ascend_graphmemory_starter -> efGraphmemoryAutoconfig "module contains engineering frame" "SAA Relationship" {
    properties {
        "saa.rel" "contains"
    }
}
efGraphmemoryAutoconfig -> fpGraphMemoryStore "frame anchors function point" "SAA Relationship" {
    properties {
        "saa.rel" "anchors"
    }
}

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

