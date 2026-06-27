package com.huawei.ascend.examples.workmate.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;

/** Abstraction over MCP sync client for tests and gateway. */
public interface McpServerClient extends AutoCloseable {

    String serverId();

    boolean isConnected();

    List<McpSchema.Tool> listTools();

    McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments);

    @Override
    void close();
}
