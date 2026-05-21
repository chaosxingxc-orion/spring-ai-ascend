package com.huawei.ascend.service.platform.web.runs;

import com.huawei.ascend.service.runtime.runs.Run;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for {@code POST /v1/runs}, {@code GET /v1/runs/{id}},
 * {@code POST /v1/runs/{id}/cancel} (plan §6).
 *
 * <p>Field set is intentionally narrow at L1 — runId, status, capabilityName,
 * createdAt, updatedAt. Future waves add cost, budget, suspend reason, etc.
 *
 * <p>The class-level {@code @Schema(requiredProperties = ...)} declares the
 * OpenAPI {@code required:} list for live spec generation matching the pinned
 * snapshot in
 * {@code agent-service/src/test/resources/contracts/openapi-v1-pinned.yaml}.
 * Without it, Spring Boot 4 + springdoc 3.0.3 emits Java record components as
 * optional in the live spec, drifting from the pinned contract (closed
 * per CI-2). Class-level {@code requiredProperties} is more reliable than
 * per-component {@code @Schema(requiredMode = REQUIRED)} on this springdoc
 * version with Java records.
 */
@Schema(requiredProperties = {"runId", "status", "capabilityName", "createdAt", "updatedAt"})
public record RunResponse(
        UUID runId,
        String status,
        String capabilityName,
        Instant createdAt,
        Instant updatedAt
) {

    public static RunResponse from(Run run) {
        return new RunResponse(
                run.runId(),
                run.status().name(),
                run.capabilityName(),
                run.createdAt(),
                run.updatedAt());
    }
}
