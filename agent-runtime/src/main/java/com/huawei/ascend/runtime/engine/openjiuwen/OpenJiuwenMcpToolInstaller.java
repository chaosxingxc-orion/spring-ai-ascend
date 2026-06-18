package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.McpProvider;
import com.huawei.ascend.runtime.engine.spi.McpToolResult;
import com.huawei.ascend.runtime.engine.spi.McpToolSpec;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.BaseAgent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Installs MCP tools into an OpenJiuwen agent instance. */
public final class OpenJiuwenMcpToolInstaller {
    private static final Logger LOG = LoggerFactory.getLogger(OpenJiuwenMcpToolInstaller.class);

    private final McpProvider mcpProvider;

    public OpenJiuwenMcpToolInstaller(McpProvider mcpProvider) {
        this.mcpProvider = Objects.requireNonNull(mcpProvider, "mcpProvider");
    }

    public void install(BaseAgent agent, AgentExecutionContext context) {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(context, "context");
        List<McpToolSpec> specs;
        try {
            specs = mcpProvider.listTools(context);
        } catch (RuntimeException error) {
            LOG.warn("MCP tool discovery failed for openjiuwen agent={} errorClass={} message={}",
                    agent.getCard().getId(), error.getClass().getSimpleName(), errorMessage(error));
            return;
        }
        if (specs == null || specs.isEmpty()) {
            LOG.info("no MCP tools to install into openjiuwen agent={}", agent.getCard().getId());
            return;
        }
        Map<String, Integer> nameCounts = nameCounts(specs);
        int installed = 0;
        for (McpToolSpec spec : specs) {
            if (spec == null || spec.name().isBlank() || spec.serverId().isBlank()) {
                continue;
            }
            String frameworkToolName = frameworkToolName(spec, nameCounts);
            Tool tool = new RuntimeMcpTool(toCard(spec, frameworkToolName), mcpProvider, context, spec);
            Runner.resourceMgr().addTool(tool, agent.getCard().getId(), true);
            if (agent.getAbilityManager().get(frameworkToolName) == null) {
                agent.getAbilityManager().add(tool.getCard());
            }
            installed++;
            LOG.info("installed MCP tool into openjiuwen agent={} serverId={} toolName={} frameworkToolName={}",
                    agent.getCard().getId(), spec.serverId(), spec.name(), frameworkToolName);
        }
        LOG.info("installed {} MCP tool(s) into openjiuwen agent={}", installed, agent.getCard().getId());
    }

    private static ToolCard toCard(McpToolSpec spec, String frameworkToolName) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("runtime.mcp.serverId", spec.serverId());
        properties.put("runtime.mcp.toolName", spec.name());
        if (!spec.title().isBlank()) {
            properties.put("runtime.mcp.title", spec.title());
        }
        properties.putAll(spec.metadata());
        return ToolCard.builder()
                .id(frameworkToolName)
                .name(frameworkToolName)
                .description(spec.description().isBlank() ? spec.name() : spec.description())
                .inputParams(inputSchema(spec))
                .properties(properties)
                .build();
    }

    private static Map<String, Object> inputSchema(McpToolSpec spec) {
        if (!spec.inputSchema().isEmpty()) {
            return spec.inputSchema();
        }
        return Map.of("type", "object", "properties", Map.of());
    }

    private static Map<String, Integer> nameCounts(List<McpToolSpec> specs) {
        Map<String, Integer> counts = new HashMap<>();
        for (McpToolSpec spec : specs) {
            if (spec != null && !spec.name().isBlank()) {
                counts.merge(spec.name(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private static String frameworkToolName(McpToolSpec spec, Map<String, Integer> nameCounts) {
        String name = sanitize(spec.name());
        if (nameCounts.getOrDefault(spec.name(), 0) <= 1) {
            return name;
        }
        return "mcp_" + sanitize(spec.serverId()) + "_" + name;
    }

    private static String sanitize(String value) {
        String sanitized = value == null ? "" : value.replaceAll("[^A-Za-z0-9_]", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        if (sanitized.isBlank()) {
            return "mcp_tool";
        }
        if (!Character.isLetter(sanitized.charAt(0)) && sanitized.charAt(0) != '_') {
            return "mcp_" + sanitized;
        }
        return sanitized;
    }

    private static String errorMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getName() : message;
    }

    private static Map<String, Object> toMap(McpToolResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("content", result.content());
        if (result.structuredContent() != null) {
            map.put("structuredContent", result.structuredContent());
        }
        map.put("isError", result.isError());
        if (!result.errorCode().isBlank()) {
            map.put("errorCode", result.errorCode());
        }
        if (!result.message().isBlank()) {
            map.put("message", result.message());
        }
        if (!result.meta().isEmpty()) {
            map.put("_meta", result.meta());
        }
        if (!result.metadata().isEmpty()) {
            map.put("metadata", result.metadata());
        }
        return map;
    }

    private static final class RuntimeMcpTool extends Tool {
        private final McpProvider mcpProvider;
        private final AgentExecutionContext context;
        private final McpToolSpec spec;

        private RuntimeMcpTool(ToolCard card, McpProvider mcpProvider, AgentExecutionContext context,
                McpToolSpec spec) {
            super(card);
            this.mcpProvider = mcpProvider;
            this.context = context;
            this.spec = spec;
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            McpToolResult result = mcpProvider.callTool(
                    context,
                    spec.serverId(),
                    spec.name(),
                    inputs == null ? Map.of() : inputs);
            return toMap(result);
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.of(invoke(inputs, kwargs)).iterator();
        }
    }
}
