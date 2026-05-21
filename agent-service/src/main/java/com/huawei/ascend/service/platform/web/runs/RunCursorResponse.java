package com.huawei.ascend.service.platform.web.runs;

import com.huawei.ascend.service.runtime.runs.Run;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Cursor Flow envelope returned by {@code POST /v1/runs} per Rule 36 / Layer-0 principle
 * P-F (Cursor Flow & Asynchronous Client Boundary). The client receives this immediately
 * (HTTP 202) and polls {@code cursor_url} for state.
 *
 * <p>Field set matches the {@code TaskCursor} schema in
 * {@code docs/contracts/openapi-v1.yaml#components.schemas.TaskCursor}: runId, status,
 * cursor_url. The status field is always {@code "PENDING"} at cursor issuance
 * (Rule 20 — RunStateMachine DFA).
 *
 * <p>JSON field naming uses snake_case for {@code cursor_url} to match the OpenAPI
 * contract while keeping the Java field name camelCase (Rule 28 — Code-as-Contract).
 */
public record RunCursorResponse(
        UUID runId,
        String status,
        @JsonProperty("cursor_url") String cursorUrl
) {

    public static RunCursorResponse from(Run run, String baseUrl) {
        return new RunCursorResponse(
                run.runId(),
                run.status().name(),
                baseUrl + "/v1/runs/" + run.runId());
    }
}
