package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.List;
import java.util.Map;

/**
 * Minimal runtime-neutral MCP tool SPI.
 *
 * <p>The SPI deliberately models the MCP tool surface, not any concrete MCP SDK
 * or agent framework type. Framework adapters decide how discovered tools are
 * installed into their native tool registry.
 */
public interface McpProvider {

    /** Return MCP tools visible for the current execution context. */
    List<McpToolSpec> listTools(AgentExecutionContext context);

    /** Call one MCP tool by original server id and MCP tool name. */
    McpToolResult callTool(AgentExecutionContext context, String serverId, String name,
            Map<String, Object> arguments);
}
