package com.huawei.ascend.examples.workmate.mcp;

import com.huawei.ascend.examples.workmate.approval.ApprovalGate;
import com.huawei.ascend.examples.workmate.connector.ConnectorIds;
import com.huawei.ascend.examples.workmate.approval.ToolRiskPolicy;
import com.huawei.ascend.examples.workmate.capability.CapabilityUsageService;
import com.huawei.ascend.examples.workmate.tools.ToolExecutionContext;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class McpOpenJiuwenToolFactory {

    private final McpGateway gateway;
    private final CapabilityUsageService capabilityUsageService;

    public McpOpenJiuwenToolFactory(McpGateway gateway, CapabilityUsageService capabilityUsageService) {
        this.gateway = gateway;
        this.capabilityUsageService = capabilityUsageService;
    }

    public McpToolSet create(String agentTag) {
        return create(agentTag, null, null);
    }

    public McpToolSet create(String agentTag, ToolExecutionContext executionContext) {
        return create(agentTag, executionContext, null);
    }

    public McpToolSet create(String agentTag, ToolExecutionContext executionContext, Collection<String> enabledServerIds) {
        if (!gateway.hasServers()) {
            return new McpToolSet(List.of(), agentTag);
        }
        Set<String> allowed = enabledServerIds == null
                ? Set.of()
                : ConnectorIds.normalize(enabledServerIds).stream()
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (allowed.isEmpty()) {
            return new McpToolSet(List.of(), agentTag);
        }
        List<Tool> tools = new ArrayList<>();
        for (McpGateway.McpToolDescriptor descriptor : gateway.listTools()) {
            if (!allowed.contains(descriptor.serverId())) {
                continue;
            }
            gateway.findClient(descriptor.serverId()).ifPresent(client -> {
                McpSchema.Tool mcpTool = client.listTools().stream()
                        .filter(tool -> tool.name().equals(descriptor.toolName()))
                        .findFirst()
                        .orElseThrow();
                Tool tool = new McpProxyTool(descriptor, mcpTool, executionContext);
                safeRemove(tool.getCard().getId(), agentTag);
                Runner.resourceMgr().addTool(tool, agentTag);
                tools.add(tool);
            });
        }
        return new McpToolSet(List.copyOf(tools), agentTag);
    }

    public void unregister(McpToolSet toolSet) {
        for (Tool tool : toolSet.tools()) {
            safeRemove(tool.getCard().getId(), toolSet.agentTag());
        }
    }

    private static void safeRemove(String toolId, String agentTag) {
        try {
            Runner.resourceMgr().removeTool(toolId, agentTag, TagMatchStrategy.ALL, true);
        } catch (RuntimeException ignored) {
            // first registration
        }
    }

    public record McpToolSet(List<Tool> tools, String agentTag) {
    }

    private final class McpProxyTool extends LocalFunction {

        McpProxyTool(
                McpGateway.McpToolDescriptor descriptor,
                McpSchema.Tool mcpTool,
                ToolExecutionContext executionContext) {
            super(
                    ToolCard.builder()
                            .id(descriptor.openJiuwenToolId())
                            .name(descriptor.openJiuwenToolId())
                            .description("MCP proxy [" + descriptor.serverId() + "] "
                                    + (mcpTool.description() != null ? mcpTool.description() : mcpTool.name()))
                            .inputParams(McpToolIdNaming.toInputParams(mcpTool.inputSchema()))
                            .build(),
                    inputs -> executeMcpTool(descriptor, executionContext, inputs));
        }
    }

    private Map<String, Object> executeMcpTool(
            McpGateway.McpToolDescriptor descriptor,
            ToolExecutionContext executionContext,
            Map<String, Object> inputs) {
        String toolId = descriptor.openJiuwenToolId();
        ToolRiskPolicy.RiskAssessment policy = ToolRiskPolicy.assess(toolId, inputs);
        if (policy.policyBlocked()) {
            return Map.of("success", false, "error", "Blocked by security policy: " + policy.summary());
        }
        if (executionContext != null && executionContext.approvalGate() != null) {
            ApprovalGate gate = executionContext.approvalGate();
            ApprovalGate.Verdict verdict =
                    gate.await(executionContext.sessionId(), executionContext.taskId(), toolId, inputs);
            if (verdict == ApprovalGate.Verdict.DENIED || verdict == ApprovalGate.Verdict.TIMED_OUT) {
                ToolRiskPolicy.RiskAssessment risk = ToolRiskPolicy.assess(toolId, inputs);
                String message = risk.policyBlocked()
                        ? "Blocked by security policy: " + risk.summary()
                        : "User denied tool execution: " + risk.summary();
                return Map.of("success", false, "error", message);
            }
        }
        Map<String, Object> result =
                McpCallResultMapper.toToolResult(gateway.callTool(descriptor.serverId(), descriptor.toolName(), inputs));
        capabilityUsageService.recordUsage("mcp", descriptor.serverId());
        return result;
    }
}
