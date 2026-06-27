package com.huawei.ascend.examples.workmate.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class FilteredMcpServerClient implements McpServerClient {

    private final McpServerClient delegate;
    private final Set<String> allowlist;

    FilteredMcpServerClient(McpServerClient delegate, List<String> allowlist) {
        this.delegate = delegate;
        this.allowlist = allowlist == null || allowlist.isEmpty()
                ? Set.of()
                : allowlist.stream().filter(name -> name != null && !name.isBlank()).collect(Collectors.toSet());
    }

    @Override
    public String serverId() {
        return delegate.serverId();
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    @Override
    public List<McpSchema.Tool> listTools() {
        List<McpSchema.Tool> tools = delegate.listTools();
        if (allowlist.isEmpty()) {
            return tools;
        }
        return tools.stream().filter(tool -> allowlist.contains(tool.name())).toList();
    }

    @Override
    public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
        if (!allowlist.isEmpty() && !allowlist.contains(toolName)) {
            throw new McpToolNotAllowedException(serverId(), toolName, allowlist);
        }
        return delegate.callTool(toolName, arguments);
    }

    @Override
    public void close() {
        delegate.close();
    }

    int allowlistSize() {
        return allowlist.size();
    }
}
