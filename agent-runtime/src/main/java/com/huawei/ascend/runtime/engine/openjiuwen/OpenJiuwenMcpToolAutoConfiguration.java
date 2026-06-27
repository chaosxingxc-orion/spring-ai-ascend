package com.huawei.ascend.runtime.engine.openjiuwen;

import com.huawei.ascend.runtime.engine.mcp.McpAutoConfiguration;
import com.huawei.ascend.runtime.engine.spi.McpProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Auto-wires runtime-neutral MCP providers into OpenJiuwen handlers. */
@AutoConfiguration(after = McpAutoConfiguration.class)
@ConditionalOnClass(name = "com.openjiuwen.core.singleagent.BaseAgent")
@ConditionalOnBean(McpProvider.class)
public class OpenJiuwenMcpToolAutoConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(OpenJiuwenMcpToolAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public OpenJiuwenMcpToolInstaller openJiuwenMcpToolInstaller(
            McpProvider mcpProvider,
            ObjectProvider<OpenJiuwenAgentRuntimeHandler> handlers,
            ObjectProvider<OpenJiuwenDeepAgentRuntimeHandler> deepHandlers) {
        OpenJiuwenMcpToolInstaller installer = new OpenJiuwenMcpToolInstaller(mcpProvider);
        int count = 0;
        for (OpenJiuwenAgentRuntimeHandler handler : handlers.orderedStream().toList()) {
            handler.setMcpToolInstaller(installer);
            count++;
            LOG.info("installed MCP tool installer into openjiuwen handler agentId={}", handler.agentId());
        }
        for (OpenJiuwenDeepAgentRuntimeHandler handler : deepHandlers.orderedStream().toList()) {
            handler.setMcpToolInstaller(installer);
            count++;
            LOG.info("installed MCP tool installer into openjiuwen deepagent handler agentId={}", handler.agentId());
        }
        if (count == 0) {
            LOG.warn("MCP tool installer created but no OpenJiuwen runtime handler beans found");
        }
        return installer;
    }
}
