package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.RemoteAgentCatalogPort;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.BaseAgent;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OpenJiuwenRemoteToolInstaller {
    private static final Logger LOG = LoggerFactory.getLogger(OpenJiuwenRemoteToolInstaller.class);

    private final Supplier<List<RemoteAgentCatalogPort.RemoteAgentToolSpec>> toolSpecs;

    public OpenJiuwenRemoteToolInstaller(Supplier<List<RemoteAgentCatalogPort.RemoteAgentToolSpec>> toolSpecs) {
        this.toolSpecs = Objects.requireNonNull(toolSpecs, "toolSpecs");
    }

    public void install(BaseAgent agent, AgentExecutionContext context) {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(context, "context");
        List<RemoteAgentCatalogPort.RemoteAgentToolSpec> specs = toolSpecs.get();
        if (specs == null || specs.isEmpty()) {
            return;
        }
        for (RemoteAgentCatalogPort.RemoteAgentToolSpec spec : specs) {
            Tool tool = new PlaceholderRemoteAgentTool(toCard(spec));
            Runner.resourceMgr().addTool(tool, agent.getCard().getId(), true);
            if (agent.getAbilityManager().get(spec.toolName()) == null) {
                agent.getAbilityManager().add(tool.getCard());
            }
        }
        agent.registerRail(new OpenJiuwenRemoteAgentInterruptRail(context, specs));
        LOG.info("installed {} remote A2A tool(s) into openjiuwen agent={}", specs.size(), agent.getCard().getId());
    }

    private static ToolCard toCard(RemoteAgentCatalogPort.RemoteAgentToolSpec spec) {
        return ToolCard.builder()
                .id(spec.toolName())
                .name(spec.toolName())
                .description(spec.description())
                .inputParams(spec.inputSchema())
                .properties(Map.of("runtime.remoteAgentId", spec.remoteAgentId()))
                .build();
    }

    private static final class PlaceholderRemoteAgentTool extends Tool {
        private PlaceholderRemoteAgentTool(ToolCard card) {
            super(card);
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return Map.of(
                    "error", "REMOTE_AGENT_TOOL_NOT_INTERRUPTED",
                    "message", "Remote A2A tools must be intercepted by the runtime interrupt rail.");
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.of(invoke(inputs, kwargs)).iterator();
        }
    }
}
