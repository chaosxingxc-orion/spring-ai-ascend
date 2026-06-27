package com.huawei.ascend.examples.workmate.mcp;

public class McpServerNotFoundException extends RuntimeException {

    public McpServerNotFoundException(String serverId) {
        super("MCP server not registered: " + serverId);
    }
}
