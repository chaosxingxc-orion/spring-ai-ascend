package com.huawei.ascend.service.session.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Typed result for {@link ContextProjector#project(ContextProjectionRequest)}.
 *
 * <p>Closes IF-DRIFT-003 from PR #76's interface-drift review: the prior
 * {@code Map<String,Object>} return form lost schema and forced every consumer
 * to re-validate well-known keys.
 *
 * <p>The record is immutable; {@code messages} and {@code variables} are
 * defensively copied. Required identity fields ({@code tenantId},
 * {@code sessionId}) are non-null-validated.
 *
 * @param tenantId          the tenant scope this projection was computed for.
 *                          Equal to the request's tenantId.
 * @param sessionId         the Session this projection was computed for.
 * @param projectionPolicy  named strategy actually applied (when the request's
 *                          policy was null, this is the projector's default).
 * @param projectionWindow  number of messages retained from the tail of the Session;
 *                          0 means the policy did not use a window (e.g. summary_v1).
 * @param messages          projected message list (defensively copied); null becomes empty.
 * @param variables         projected variable map (defensively copied); null becomes empty.
 * @param tokenCount        estimated token count of the rendered projection; 0 means
 *                          the projector did not compute it (e.g. an in-memory stub).
 */
public record ProjectedContext(
        String tenantId,
        String sessionId,
        String projectionPolicy,
        int projectionWindow,
        List<Object> messages,
        Map<String, Object> variables,
        int tokenCount) {

    public ProjectedContext {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(projectionPolicy, "projectionPolicy is required (the projector MUST report which policy it actually applied)");
        messages = messages == null ? List.of() : List.copyOf(messages);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        if (projectionWindow < 0) {
            throw new IllegalArgumentException("projectionWindow must be >= 0, got " + projectionWindow);
        }
        if (tokenCount < 0) {
            throw new IllegalArgumentException("tokenCount must be >= 0, got " + tokenCount);
        }
    }
}
