package ascend.springai.service.platform.web.runs;

import ascend.springai.service.runtime.runs.Run;

import java.time.Instant;
import java.util.UUID;

/**
 * Response body for {@code POST /v1/runs}, {@code GET /v1/runs/{id}},
 * {@code POST /v1/runs/{id}/cancel} (plan §6).
 *
 * <p>Field set is intentionally narrow at L1 — runId, status, capabilityName,
 * createdAt, updatedAt. Future waves add cost, budget, suspend reason, etc.
 */
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
