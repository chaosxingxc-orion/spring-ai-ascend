package ascend.springai.service.platform.web.runs;

import ascend.springai.service.runtime.runs.Run;
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
 * <p>Every component is annotated {@code @Schema(requiredMode = REQUIRED)} so
 * springdoc declares the OpenAPI {@code required:} list for live spec generation
 * matching the pinned snapshot in
 * {@code agent-service/src/test/resources/contracts/openapi-v1-pinned.yaml}.
 * Without the annotation, Spring Boot 4 + Spring AI 2.0.0-M5 + springdoc emits
 * Java record components as optional in the live spec, drifting from the pinned
 * contract (closed in rc9 per CI-2).
 */
public record RunResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID runId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String capabilityName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt
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
