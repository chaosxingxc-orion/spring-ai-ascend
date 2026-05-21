package com.huawei.ascend.service.engine.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service-to-Engine invocation request.
 *
 * <p>Wire contract:
 * {@code docs/contracts/agent-invoke-request.v1.yaml} (status:
 * design_only; runtime impl in rc24).
 *
 * <p>Service is the Read-Modify-Write closure boundary; Engine is the
 * Pure-Function compute boundary. See ADR-0100 §decision.
 *
 * <p>This is an SPI surface placeholder. Fields are documented to mirror
 * the YAML contract; concrete record fields land alongside the
 * reference impl.
 *
 * @param runId           the Run this invocation belongs to.
 * @param taskId          the Task this Run materializes (decoupled from sessionId.
 * @param sessionId       the Session whose context this invocation reads.
 * @param tenantId        mandatory per Rule R-C.c.
 * @param sessionContext  projected SessionContext from ContextProjector SPI.
 * @param injectedSkills  pre-resolved skill IDs (capacity already approved per Rule R-K).
 * @param taskMetadata    task control state at invocation time.
 * @param traceId         W3C trace-id propagated from inbound request.
 */
public record AgentInvokeRequest(
        String runId,
        String taskId,
        String sessionId,
        String tenantId,
        Map<String, Object> sessionContext,
        List<String> injectedSkills,
        Map<String, Object> taskMetadata,
        String traceId) {

    // enforce non-null invariant matching the
    // agent-invoke-request.v1.yaml#fields[].required: true contract.
    // Without these guards, downstream Map.of(...) calls explode with NPE
    // and the engine cannot produce a well-formed StateDelta.
    public AgentInvokeRequest {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sessionContext, "sessionContext");
        Objects.requireNonNull(injectedSkills, "injectedSkills");
        Objects.requireNonNull(taskMetadata, "taskMetadata");
        Objects.requireNonNull(traceId, "traceId");
    }
}
