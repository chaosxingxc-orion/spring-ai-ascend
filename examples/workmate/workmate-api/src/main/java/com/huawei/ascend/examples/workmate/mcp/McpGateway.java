package com.huawei.ascend.examples.workmate.mcp;

import com.huawei.ascend.examples.workmate.config.WorkmateMcpProperties;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class McpGateway {

    private final WorkmateMcpProperties properties;
    private final ConcurrentHashMap<String, McpServerClient> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, McpServerSummary> summaries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> lastErrors = new ConcurrentHashMap<>();

    public McpGateway(WorkmateMcpProperties properties) {
        this.properties = properties;
    }

    public void register(McpServerClient client) {
        clients.put(client.serverId(), client);
        lastErrors.remove(client.serverId());
        refreshSummary(client);
    }

    public List<McpServerSummary> listServers() {
        return clients.values().stream().map(this::toSummary).toList();
    }

    public McpServerSummary serverSummary(String serverId) {
        McpServerSummary cached = summaries.get(serverId);
        if (cached != null) {
            return cached;
        }
        McpServerClient client = clients.get(serverId);
        if (client == null) {
            return new McpServerSummary(serverId, false, 0, 0, false, lastErrors.get(serverId));
        }
        return refreshSummary(client);
    }

    public List<McpToolDescriptor> listTools() {
        List<McpToolDescriptor> tools = new ArrayList<>();
        for (McpServerClient client : clients.values()) {
            List<McpSchema.Tool> raw = client.listTools();
            List<McpSchema.Tool> valid = McpToolSchemaValidator.filterValid(client.serverId(), raw);
            for (McpSchema.Tool tool : valid) {
                tools.add(new McpToolDescriptor(
                        client.serverId(),
                        tool.name(),
                        tool.description(),
                        McpToolIdNaming.openJiuwenToolId(client.serverId(), tool.name())));
            }
        }
        return tools;
    }

    public McpSchema.CallToolResult callTool(String serverId, String toolName, Map<String, Object> arguments) {
        McpServerClient client = clients.get(serverId);
        if (client == null) {
            throw new McpServerNotFoundException(serverId);
        }
        return client.callTool(toolName, arguments);
    }

    public Optional<McpServerClient> findClient(String serverId) {
        return Optional.ofNullable(clients.get(serverId));
    }

    public void unregister(String serverId) {
        McpServerClient client = clients.remove(serverId);
        if (client != null) {
            try {
                client.close();
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
        summaries.put(
                serverId,
                new McpServerSummary(serverId, false, 0, 0, false, lastErrors.get(serverId)));
    }

    public void registerClient(McpServerClient client) {
        unregister(client.serverId());
        clients.put(client.serverId(), client);
        lastErrors.remove(client.serverId());
        refreshSummary(client);
    }

    public void recordConnectionError(String serverId, String message) {
        if (message != null && !message.isBlank()) {
            lastErrors.put(serverId, message);
        }
        McpServerSummary prior = summaries.get(serverId);
        summaries.put(
                serverId,
                new McpServerSummary(
                        serverId,
                        false,
                        prior != null ? prior.toolCount() : 0,
                        prior != null ? prior.invalidSchemaCount() : 0,
                        prior != null && prior.toolsLimitWarning(),
                        message));
    }

    public void clearConnectionError(String serverId) {
        lastErrors.remove(serverId);
    }

    public boolean hasServers() {
        return !clients.isEmpty();
    }

    public void closeAll() {
        clients.values().forEach(client -> {
            try {
                client.close();
            } catch (RuntimeException ignored) {
                // best effort
            }
        });
        clients.clear();
        summaries.clear();
        lastErrors.clear();
    }

    private McpServerSummary refreshSummary(McpServerClient client) {
        McpServerSummary summary = toSummary(client);
        summaries.put(client.serverId(), summary);
        return summary;
    }

    private McpServerSummary toSummary(McpServerClient client) {
        List<McpSchema.Tool> raw = client.listTools();
        int invalidSchemaCount = McpToolSchemaValidator.countInvalid(client.serverId(), raw);
        int toolCount = McpToolSchemaValidator.filterValid(client.serverId(), raw).size();
        boolean toolsLimitWarning = toolCount >= properties.toolsLimitWarning();
        return new McpServerSummary(
                client.serverId(),
                client.isConnected(),
                toolCount,
                invalidSchemaCount,
                toolsLimitWarning,
                lastErrors.get(client.serverId()));
    }

    public record McpServerSummary(
            String serverId,
            boolean connected,
            int toolCount,
            int invalidSchemaCount,
            boolean toolsLimitWarning,
            String lastError) {
    }

    public record McpToolDescriptor(
            String serverId,
            String toolName,
            String description,
            String openJiuwenToolId) {
    }
}
