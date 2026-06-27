package com.huawei.ascend.examples.workmate.mcp;

import java.util.Set;

public class McpToolNotAllowedException extends RuntimeException {

    private final String serverId;
    private final String toolName;
    private final Set<String> allowlist;

    public McpToolNotAllowedException(String serverId, String toolName, Set<String> allowlist) {
        super("Tool '" + toolName + "' is not in allowlist for MCP server '" + serverId + "'");
        this.serverId = serverId;
        this.toolName = toolName;
        this.allowlist = allowlist;
    }

    public String serverId() {
        return serverId;
    }

    public String toolName() {
        return toolName;
    }

    public Set<String> allowlist() {
        return allowlist;
    }
}
