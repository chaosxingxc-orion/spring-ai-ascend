package com.huawei.ascend.middleware.spi;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Carrier for hook event metadata. Passed to every registered
 * {@link RuntimeMiddleware} when the {@link HookPoint} fires.
 *
 * <p>{@code attributes} carries hook-point-specific data — e.g. the model id
 * for {@link HookPoint#BEFORE_LLM_INVOCATION}, the tool name for
 * {@link HookPoint#BEFORE_TOOL_INVOCATION}, the exception for
 * {@link HookPoint#ON_ERROR}. Keys are stable strings; values are opaque.
 * Phase 2 keeps this loose; W2 hardens it via per-hook context records once
 * the engine-side hooks (LLM/tool/memory) ship.
 *
 * <p>Pure Java — no Spring imports per architecture §4.7
 * (orchestration.spi imports only java.*).
 *
 * <p>Authority: ADR-0073.
 */
public record HookContext(
        HookPoint point,
        UUID runId,
        String tenantId,
        Map<String, Object> attributes
) {
    public HookContext {
        Objects.requireNonNull(point, "point is required");
        Objects.requireNonNull(runId, "runId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static HookContext of(HookPoint point, UUID runId, String tenantId) {
        return new HookContext(point, runId, tenantId, Map.of());
    }
}
