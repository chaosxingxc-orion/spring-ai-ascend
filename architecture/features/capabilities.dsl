// architecture/features/capabilities.dsl
//
// Authority: ADR-0147 (Structurizr Workspace Authority).
// One-time programmatic mount from docs/governance/architecture-status.yaml#capabilities
// executed in Wave 2 of the migration. After this initial mount, the file is
// HAND-AUTHORED — engineers add new capabilities by editing here directly,
// and Wave 6 will deprecate the source YAML as the authority.
//
// The mount preserves: saa.id (from key), saa.kind (capability), saa.level
// (L1 unless explicit), saa.view (scenarios), saa.status (mapped from
// architecture-status.yaml#capabilities[].status), saa.owner (mapped from
// owner module if known, else 'architecture'), saa.sourceAdr (best-effort,
// falls back to ADR-0064 — Layer-0 governing principles — when no explicit
// l0_decision is recorded).
//
// Element count at mount: 152

cap_a2a_federation_strategic = element "a2a_federation_strategic" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-A2A-FEDERATION-STRATEGIC"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_a2a_protocol_boundary = element "a2a_protocol_boundary" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-A2A-PROTOCOL-BOUNDARY"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_active_corpus_truth_sweep = element "active_corpus_truth_sweep" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-ACTIVE-CORPUS-TRUTH-SWEEP"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_active_entrypoint_baseline_truth_gate = element "active_entrypoint_baseline_truth_gate" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-ACTIVE-ENTRYPOINT-BASELINE-TRUTH-GATE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_active_normative_doc_catalog = element "active_normative_doc_catalog" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-ACTIVE-NORMATIVE-DOC-CATALOG"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_adr_filename_title_drift = element "adr_filename_title_drift" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-ADR-FILENAME-TITLE-DRIFT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_adr_per_file = element "adr_per_file" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-ADR-PER-FILE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_agent_platform_facade = element "agent_platform_facade" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-AGENT-PLATFORM-FACADE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_agent_runtime_kernel = element "agent_runtime_kernel" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-AGENT-RUNTIME-KERNEL"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_agent_runtime_smoke_test = element "agent_runtime_smoke_test" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-AGENT-RUNTIME-SMOKE-TEST"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_agent_subject_identity = element "agent_subject_identity" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-AGENT-SUBJECT-IDENTITY"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_agent_swarm_cohesive_execution_invariant = element "agent_swarm_cohesive_execution_invariant" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-AGENT-SWARM-COHESIVE-EXECUTION-INVARIANT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_app_posture_required_in_prod = element "app_posture_required_in_prod" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-APP-POSTURE-REQUIRED-IN-PROD"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_architecture_sync_gate = element "architecture_sync_gate" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-ARCHITECTURE-SYNC-GATE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_audit_tamper_evidence = element "audit_tamper_evidence" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-AUDIT-TAMPER-EVIDENCE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_authority_transfer_boundary_taxonomy = element "authority_transfer_boundary_taxonomy" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-AUTHORITY-TRANSFER-BOUNDARY-TAXONOMY"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_bom_mcp_sdk_version_pin = element "bom_mcp_sdk_version_pin" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-BOM-MCP-SDK-VERSION-PIN"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "spring-ai-ascend-dependencies"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_business_platform_decoupling = element "business_platform_decoupling" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-BUSINESS-PLATFORM-DECOUPLING"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-bus"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_c_s_dynamic_hydration_protocol = element "c_s_dynamic_hydration_protocol" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-C-S-DYNAMIC-HYDRATION-PROTOCOL"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_call_tree_budget_propagation = element "call_tree_budget_propagation" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-CALL-TREE-BUDGET-PROPAGATION"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_capability_registry_spi = element "capability_registry_spi" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-CAPABILITY-REGISTRY-SPI"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_causal_payload_envelope = element "causal_payload_envelope" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-CAUSAL-PAYLOAD-ENVELOPE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_checkpoint_eviction_policy = element "checkpoint_eviction_policy" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-CHECKPOINT-EVICTION-POLICY"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_checkpoint_ownership_boundary = element "checkpoint_ownership_boundary" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-CHECKPOINT-OWNERSHIP-BOUNDARY"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_claude_md_token_budget = element "claude_md_token_budget" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-CLAUDE-MD-TOKEN-BUDGET"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_client_sdk_observability_contract = element "client_sdk_observability_contract" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-CLIENT-SDK-OBSERVABILITY-CONTRACT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "agent-client"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_cognition_action_separation = element "cognition_action_separation" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-COGNITION-ACTION-SEPARATION"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_competitive_baselines = element "competitive_baselines" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-COMPETITIVE-BASELINES"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_contract_surface_truth_generalization = element "contract_surface_truth_generalization" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-CONTRACT-SURFACE-TRUTH-GENERALIZATION"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_cross_workflow_handoff_contract = element "cross_workflow_handoff_contract" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-CROSS-WORKFLOW-HANDOFF-CONTRACT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_doctor_script = element "doctor_script" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-DOCTOR-SCRIPT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_dual_deployment_modes = element "dual_deployment_modes" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-DUAL-DEPLOYMENT-MODES"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_dual_track_router = element "dual_track_router" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-DUAL-TRACK-ROUTER"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_eval_harness_contract = element "eval_harness_contract" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-EVAL-HARNESS-CONTRACT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_executor_definition_serialization = element "executor_definition_serialization" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-EXECUTOR-DEFINITION-SERIALIZATION"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_file_descriptor_bound = element "file_descriptor_bound" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-FILE-DESCRIPTOR-BOUND"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_four_layer_state_model = element "four_layer_state_model" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-FOUR-LAYER-STATE-MODEL"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_gate_config_well_formed = element "gate_config_well_formed" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-GATE-CONFIG-WELL-FORMED"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_graph_dsl_conformance = element "graph_dsl_conformance" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-GRAPH-DSL-CONFORMANCE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_graph_tenant_isolation = element "graph_tenant_isolation" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-GRAPH-TENANT-ISOLATION"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_helm_chart_skeleton = element "helm_chart_skeleton" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-HELM-CHART-SKELETON"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}


cap_hybrid_rag_bm25 = element "hybrid_rag_bm25" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-HYBRID-RAG-BM25"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_idempotency_409_body_shape = element "idempotency_409_body_shape" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-IDEMPOTENCY-409-BODY-SHAPE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_idempotency_header_filter = element "idempotency_header_filter" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-IDEMPOTENCY-HEADER-FILTER"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_idempotency_record_entity = element "idempotency_record_entity" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-IDEMPOTENCY-RECORD-ENTITY"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_idempotency_retention_and_erasure = element "idempotency_retention_and_erasure" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-IDEMPOTENCY-RETENTION-AND-ERASURE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_idempotency_scope_w0 = element "idempotency_scope_w0" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-IDEMPOTENCY-SCOPE-W0"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_idempotency_store = element "idempotency_store" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-IDEMPOTENCY-STORE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_idempotency_store_promotion_to_interface = element "idempotency_store_promotion_to_interface" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-IDEMPOTENCY-STORE-PROMOTION-TO-INTERFACE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "deferred"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_in_flight_runs_pool_cap = element "in_flight_runs_pool_cap" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-IN-FLIGHT-RUNS-POOL-CAP"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_independent_module_evolution = element "independent_module_evolution" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-INDEPENDENT-MODULE-EVOLUTION"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-evolve"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_inmemory_orchestrator = element "inmemory_orchestrator" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-INMEMORY-ORCHESTRATOR"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "spring-ai-ascend-graphmemory-starter"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_iterative_agent_loop_typed_cursor = element "iterative_agent_loop_typed_cursor" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-ITERATIVE-AGENT-LOOP-TYPED-CURSOR"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_japicmp_configured = element "japicmp_configured" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-JAPICMP-CONFIGURED"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_l0_architecture = element "l0_architecture" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-L0-ARCHITECTURE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_layered_spi_taxonomy = element "layered_spi_taxonomy" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-LAYERED-SPI-TAXONOMY"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_linux_first_dev_environment = element "linux_first_dev_environment" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-LINUX-FIRST-DEV-ENVIRONMENT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_logbook_mdc_tenant_id = element "logbook_mdc_tenant_id" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-LOGBOOK-MDC-TENANT-ID"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_logical_call_handle_principle = element "logical_call_handle_principle" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-LOGICAL-CALL-HANDLE-PRINCIPLE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_logical_identity_equivalence = element "logical_identity_equivalence" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-LOGICAL-IDENTITY-EQUIVALENCE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_long_connection_containment_invariant = element "long_connection_containment_invariant" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-LONG-CONNECTION-CONTAINMENT-INVARIANT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_mdc_correlation_carriers = element "mdc_correlation_carriers" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-MDC-CORRELATION-CARRIERS"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_memory_knowledge_ownership_boundary = element "memory_knowledge_ownership_boundary" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-MEMORY-KNOWLEDGE-OWNERSHIP-BOUNDARY"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "spring-ai-ascend-graphmemory-starter"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_memory_knowledge_taxonomy = element "memory_knowledge_taxonomy" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-MEMORY-KNOWLEDGE-TAXONOMY"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "spring-ai-ascend-graphmemory-starter"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_message_vs_task_plane_separation = element "message_vs_task_plane_separation" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-MESSAGE-VS-TASK-PLANE-SEPARATION"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_micrometer_mandatory_tenant_tag = element "micrometer_mandatory_tenant_tag" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-MICROMETER-MANDATORY-TENANT-TAG"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_module_dependency_direction_w0 = element "module_dependency_direction_w0" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-MODULE-DEPENDENCY-DIRECTION-W0"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_multi_backend_checkpointer = element "multi_backend_checkpointer" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-MULTI-BACKEND-CHECKPOINTER"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_nested_dual_mode_reference = element "nested_dual_mode_reference" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-NESTED-DUAL-MODE-REFERENCE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_openapi_response_schema_check = element "openapi_response_schema_check" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-OPENAPI-RESPONSE-SCHEMA-CHECK"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_openapi_v1_snapshot = element "openapi_v1_snapshot" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-OPENAPI-V1-SNAPSHOT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_ops_runbooks_skeleton = element "ops_runbooks_skeleton" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-OPS-RUNBOOKS-SKELETON"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_orchestration_spi = element "orchestration_spi" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-ORCHESTRATION-SPI"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_orchestrator_cancellation_handshake = element "orchestrator_cancellation_handshake" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-ORCHESTRATOR-CANCELLATION-HANDSHAKE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_otel_trace_propagation_across_suspend = element "otel_trace_propagation_across_suspend" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-OTEL-TRACE-PROPAGATION-ACROSS-SUSPEND"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "deferred"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_otlp_per_tenant_exporter_binding = element "otlp_per_tenant_exporter_binding" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-OTLP-PER-TENANT-EXPORTER-BINDING"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_outbox_replay_safety_policy = element "outbox_replay_safety_policy" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-OUTBOX-REPLAY-SAFETY-POLICY"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_parallel_child_dispatch = element "parallel_child_dispatch" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-PARALLEL-CHILD-DISPATCH"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_payload_codec_spi = element "payload_codec_spi" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-PAYLOAD-CODEC-SPI"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_payload_fingerprint_precommit = element "payload_fingerprint_precommit" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-PAYLOAD-FINGERPRINT-PRECOMMIT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_payload_migration_adapter = element "payload_migration_adapter" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-PAYLOAD-MIGRATION-ADAPTER"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_payload_store_spi = element "payload_store_spi" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-PAYLOAD-STORE-SPI"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_perf_evidence_path = element "perf_evidence_path" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-PERF-EVIDENCE-PATH"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_perf_overhead_budget = element "perf_overhead_budget" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-PERF-OVERHEAD-BUDGET"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_plan_projection_contract = element "plan_projection_contract" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-PLAN-PROJECTION-CONTRACT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0032 (planner contract minimal); ADR-0052 (SkillResourceMatrix); docs/logs/reviews/2026-05-18-l0-rc4-cross-constraint-architecture-review.en.md#P1-3 amendment"
    }
}

cap_planner_as_tool_pattern = element "planner_as_tool_pattern" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-PLANNER-AS-TOOL-PATTERN"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_posture_module_bootstrap = element "posture_module_bootstrap" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-POSTURE-MODULE-BOOTSTRAP"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_posture_single_construction_path = element "posture_single_construction_path" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-POSTURE-SINGLE-CONSTRUCTION-PATH"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_reference_adapter_posture_gate_diagnostic = element "reference_adapter_posture_gate_diagnostic" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-REFERENCE-ADAPTER-POSTURE-GATE-DIAGNOSTIC"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_release_note_baseline_truth_gate = element "release_note_baseline_truth_gate" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-RELEASE-NOTE-BASELINE-TRUTH-GATE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_release_note_shipped_surface_truth_gate = element "release_note_shipped_surface_truth_gate" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-RELEASE-NOTE-SHIPPED-SURFACE-TRUTH-GATE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_repository_paging_contract = element "repository_paging_contract" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-REPOSITORY-PAGING-CONTRACT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_resilience_contract = element "resilience_contract" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-RESILIENCE-CONTRACT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_resume_reauthorization_check = element "resume_reauthorization_check" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-RESUME-REAUTHORIZATION-CHECK"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_rls_policy_sql = element "rls_policy_sql" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-RLS-POLICY-SQL"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_run_dispatcher_spi = element "run_dispatcher_spi" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-RUN-DISPATCHER-SPI"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_run_entity = element "run_entity" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-RUN-ENTITY"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_run_find_root_runs = element "run_find_root_runs" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-RUN-FIND-ROOT-RUNS"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_run_lifecycle_spi = element "run_lifecycle_spi" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-RUN-LIFECYCLE-SPI"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_run_optimistic_lock = element "run_optimistic_lock" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-RUN-OPTIMISTIC-LOCK"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_run_state_change_audit_log = element "run_state_change_audit_log" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-RUN-STATE-CHANGE-AUDIT-LOG"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_run_status_transition_validator = element "run_status_transition_validator" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-RUN-STATUS-TRANSITION-VALIDATOR"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_run_trace_session_columns = element "run_trace_session_columns" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-RUN-TRACE-SESSION-COLUMNS"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_runtime_hook_spi = element "runtime_hook_spi" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-RUNTIME-HOOK-SPI"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_sandbox_executor_spi = element "sandbox_executor_spi" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SANDBOX-EXECUTOR-SPI"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_sandbox_w2_w3_startup_gate = element "sandbox_w2_w3_startup_gate" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SANDBOX-W2-W3-STARTUP-GATE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_scope_based_run_hierarchy = element "scope_based_run_hierarchy" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SCOPE-BASED-RUN-HIERARCHY"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_sdk_spi_starters = element "sdk_spi_starters" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SDK-SPI-STARTERS"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_secret_lifecycle_yaml = element "secret_lifecycle_yaml" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SECRET-LIFECYCLE-YAML"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_semantic_ontology_tags = element "semantic_ontology_tags" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SEMANTIC-ONTOLOGY-TAGS"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_sender_identity_proof = element "sender_identity_proof" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SENDER-IDENTITY-PROOF"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_serializable_resume_payload = element "serializable_resume_payload" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SERIALIZABLE-RESUME-PAYLOAD"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_service_layer_microservice_commitment = element "service_layer_microservice_commitment" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SERVICE-LAYER-MICROSERVICE-COMMITMENT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_shadow_tool_interceptor = element "shadow_tool_interceptor" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SHADOW-TOOL-INTERCEPTOR"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "draft"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_shipped_row_tests_evidence = element "shipped_row_tests_evidence" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SHIPPED-ROW-TESTS-EVIDENCE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_skill_capacity_matrix = element "skill_capacity_matrix" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SKILL-CAPACITY-MATRIX"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_skill_resource_matrix = element "skill_resource_matrix" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SKILL-RESOURCE-MATRIX"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_skill_spi_lifecycle = element "skill_spi_lifecycle" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SKILL-SPI-LIFECYCLE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_skill_spi_resource_tiers = element "skill_spi_resource_tiers" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SKILL-SPI-RESOURCE-TIERS"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_skill_topology_scheduler_and_capability_bidding = element "skill_topology_scheduler_and_capability_bidding" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SKILL-TOPOLOGY-SCHEDULER-AND-CAPABILITY-BIDDING"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_socket_per_tenant_cap = element "socket_per_tenant_cap" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SOCKET-PER-TENANT-CAP"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_spawn_envelope_acyclicity = element "spawn_envelope_acyclicity" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SPAWN-ENVELOPE-ACYCLICITY"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_spawn_envelope_contract = element "spawn_envelope_contract" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SPAWN-ENVELOPE-CONTRACT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_spi_compatibility_freeze = element "spi_compatibility_freeze" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SPI-COMPATIBILITY-FREEZE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_spi_contract_precision_and_memory_metadata_reconciliation = element "spi_contract_precision_and_memory_metadata_reconciliation" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SPI-CONTRACT-PRECISION-AND-MEMORY-METADATA-RECONCILIATION"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "spring-ai-ascend-graphmemory-starter"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_spi_dfx_tck_codesign = element "spi_dfx_tck_codesign" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SPI-DFX-TCK-CODESIGN"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_spring_ai_ascend_dependencies_bom = element "spring_ai_ascend_dependencies_bom" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SPRING-AI-ASCEND-DEPENDENCIES-BOM"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "spring-ai-ascend-dependencies"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_spring_ai_ascend_graphmemory_starter = element "spring_ai_ascend_graphmemory_starter" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SPRING-AI-ASCEND-GRAPHMEMORY-STARTER"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "spring-ai-ascend-graphmemory-starter"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_streamed_handoff_mode = element "streamed_handoff_mode" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-STREAMED-HANDOFF-MODE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_suspend_deadline_watchdog = element "suspend_deadline_watchdog" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SUSPEND-DEADLINE-WATCHDOG"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_suspend_instead_of_hold_principle = element "suspend_instead_of_hold_principle" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SUSPEND-INSTEAD-OF-HOLD-PRINCIPLE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_suspend_reason_taxonomy = element "suspend_reason_taxonomy" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SUSPEND-REASON-TAXONOMY"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_suspension_write_atomicity_contract = element "suspension_write_atomicity_contract" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SUSPENSION-WRITE-ATOMICITY-CONTRACT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_system_boundary_prose_convention = element "system_boundary_prose_convention" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-SYSTEM-BOUNDARY-PROSE-CONVENTION"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_telemetry_vertical = element "telemetry_vertical" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-TELEMETRY-VERTICAL"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_tenant_context_filter = element "tenant_context_filter" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-TENANT-CONTEXT-FILTER"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_tenant_propagation_purity = element "tenant_propagation_purity" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-TENANT-PROPAGATION-PURITY"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_tenant_reauth_widening = element "tenant_reauth_widening" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-TENANT-REAUTH-WIDENING"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_three_track_channel_isolation = element "three_track_channel_isolation" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-THREE-TRACK-CHANNEL-ISOLATION"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_three_track_terminal_drain_ordering = element "three_track_terminal_drain_ordering" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-THREE-TRACK-TERMINAL-DRAIN-ORDERING"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_trace_context_spi = element "trace_context_spi" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-TRACE-CONTEXT-SPI"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_trace_replay_dev_surface = element "trace_replay_dev_surface" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-TRACE-REPLAY-DEV-SURFACE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_traceparent_propagation = element "traceparent_propagation" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-TRACEPARENT-PROPAGATION"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_untrusted_skill_sandbox_mandatory = element "untrusted_skill_sandbox_mandatory" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-UNTRUSTED-SKILL-SANDBOX-MANDATORY"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_value_based_yield_primitive = element "value_based_yield_primitive" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-VALUE-BASED-YIELD-PRIMITIVE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_w1_http_contract_reconciliation = element "w1_http_contract_reconciliation" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-W1-HTTP-CONTRACT-RECONCILIATION"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_wave_authority_consolidation = element "wave_authority_consolidation" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-WAVE-AUTHORITY-CONSOLIDATION"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "accepted"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_whitepaper_alignment_matrix = element "whitepaper_alignment_matrix" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-WHITEPAPER-ALIGNMENT-MATRIX"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_whitepaper_alignment_self_audit = element "whitepaper_alignment_self_audit" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-WHITEPAPER-ALIGNMENT-SELF-AUDIT"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "architecture"
        "saa.sourceAdr" "ADR-0064"
    }
}

cap_workflow_intermediary_bus_and_three_track_cross_service = element "workflow_intermediary_bus_and_three_track_cross_service" "Capability" "L1 capability mounted from architecture-status.yaml" "SAA Capability" {
    properties {
        "saa.id" "CAP-WORKFLOW-INTERMEDIARY-BUS-AND-THREE-TRACK-CROSS-SERVICE"
        "saa.kind" "capability"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-bus"
        "saa.sourceAdr" "ADR-0064"
    }
}

