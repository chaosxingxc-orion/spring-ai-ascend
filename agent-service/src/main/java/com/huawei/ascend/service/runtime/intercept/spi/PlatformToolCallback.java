package com.huawei.ascend.service.runtime.intercept.spi;

/**
 * Tool-call interception entry point.
 *
 * <p>Receives a TOOL_REQUEST (schema: {@code docs/contracts/intercept-request.v1.yaml}),
 * normalises the tool schema, validates inputs, applies the TTI-08 policy
 * chain, executes via the registered ToolProvider, and returns a
 * normalised ToolResult.</p>
 *
 * <p>Authority: ADR-0155. Status: design_only.</p>
 */
public interface PlatformToolCallback {

    /**
     * Execute a tool call subject to platform policy.
     *
     * @param toolRef canonical tool identifier resolved via TTI-10.
     * @param inputJson JSON-encoded tool input matching the registered schema.
     * @return tool result (schema: {@code docs/contracts/tool-result.v1.yaml}).
     */
    Object invoke(String toolRef, String inputJson);
}
