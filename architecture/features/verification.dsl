// architecture/features/verification.dsl
//
// Authority: ADR-0147 (Structurizr Workspace Authority).
// AUTHORED ZONE.
//
// SAA Test elements + `verifies` relationships covering the L1 function-point
// inventory at W2. Each entry names a Java test FQN (`saa.sourceFile`); the
// gate (W3+) cross-checks that the .java file exists.
//
// New tests land here as part of the same PR that adds the function point.
// W6 will not deprecate this file — it remains hand-authored after the YAML
// sunset.

testRunControllerCreateIT = element "RunControllerCreateIT" "IntegrationTest" "POST /v1/runs end-to-end happy path + idempotency replay" "SAA Test" {
    properties {
        "saa.id" "TEST-RUNCONTROLLER-CREATE-IT"
        "saa.kind" "integration_test"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceFile" "agent-service/src/test/java/com/huawei/ascend/service/platform/web/runs/RunHttpContractIT.java"
    }
}

testRunControllerCancelIT = element "RunControllerCancelIT" "IntegrationTest" "POST /v1/runs/(runId)/cancel — tenant re-auth + DFA transition" "SAA Test" {
    properties {
        "saa.id" "TEST-RUNCONTROLLER-CANCEL-IT"
        "saa.kind" "integration_test"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceFile" "agent-service/src/test/java/com/huawei/ascend/service/platform/web/runs/RunHttpContractIT.java"
    }
}

testRunStateMachineTest = element "RunStateMachineTest" "UnitTest" "RunStatus DFA — every legal/illegal transition asserted" "SAA Test" {
    properties {
        "saa.id" "TEST-RUNSTATEMACHINE"
        "saa.kind" "unit_test"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceFile" "agent-service/src/test/java/com/huawei/ascend/service/runtime/runs/RunStateMachineTest.java"
    }
}

testIdempotencyStoreIT = element "IdempotencyStoreIT" "IntegrationTest" "Idempotency claim + replay against PostgreSQL via Testcontainers" "SAA Test" {
    properties {
        "saa.id" "TEST-IDEMPOTENCYSTORE-IT"
        "saa.kind" "integration_test"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceFile" "agent-service/src/test/java/com/huawei/ascend/service/platform/idempotency/IdempotencyStoreIT.java"
    }
}

testTenantContextFilterIT = element "TenantContextFilterIT" "IntegrationTest" "JWT tenant claim cross-check vs IngressEnvelope tenantId" "SAA Test" {
    properties {
        "saa.id" "TEST-TENANTCONTEXTFILTER-IT"
        "saa.kind" "integration_test"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceFile" "agent-service/src/test/java/com/huawei/ascend/service/platform/tenant/TenantContextFilterIT.java"
    }
}

testEngineRegistryTest = element "EngineRegistryTest" "UnitTest" "EngineRegistry.resolve(envelope) — typed dispatch + EngineMatchingException on mismatch" "SAA Test" {
    properties {
        "saa.id" "TEST-ENGINEREGISTRY"
        "saa.kind" "unit_test"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-execution-engine"
        "saa.sourceFile" "agent-service/src/test/java/com/huawei/ascend/engine/runtime/EngineRegistryResolveTest.java"
    }
}

testPostureBootGuardTest = element "PostureBootGuardTest" "UnitTest" "PostureBootGuard fail-closed under research/prod on missing @RequiredConfig" "SAA Test" {
    properties {
        "saa.id" "TEST-POSTUREBOOTGUARD"
        "saa.kind" "unit_test"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceFile" "agent-service/src/test/java/com/huawei/ascend/service/platform/posture/PostureBootGuardIT.java"
    }
}

// Verification edges
testRunControllerCreateIT -> fpCreateRun "verifies create-Run handler" "SAA Relationship" {
    properties {

        "saa.rel" "verifies"

    }
}
testRunControllerCancelIT -> fpCancelRun "verifies cancel-Run handler" "SAA Relationship" {
    properties {

        "saa.rel" "verifies"

    }
}
testRunStateMachineTest -> fpRunStateTransition "verifies DFA transitions" "SAA Relationship" {
    properties {

        "saa.rel" "verifies"

    }
}
testIdempotencyStoreIT -> fpIdempotencyClaim "verifies idempotency claim + replay" "SAA Relationship" {
    properties {

        "saa.rel" "verifies"

    }
}
testTenantContextFilterIT -> fpTenantCrossCheck "verifies tenant cross-check" "SAA Relationship" {
    properties {

        "saa.rel" "verifies"

    }
}
testEngineRegistryTest -> fpEngineDispatch "verifies engine dispatch" "SAA Relationship" {
    properties {

        "saa.rel" "verifies"

    }
}
testPostureBootGuardTest -> fpPostureBootGuard "verifies posture boot guard" "SAA Relationship" {
    properties {

        "saa.rel" "verifies"

    }
}
