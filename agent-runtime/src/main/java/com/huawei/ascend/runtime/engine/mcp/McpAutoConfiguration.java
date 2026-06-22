package com.huawei.ascend.runtime.engine.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenMcpToolInstaller;
import com.huawei.ascend.runtime.engine.spi.McpProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Auto-configuration for runtime-managed MCP tools. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "agent-runtime.mcp.servers.0", name = "url")
@EnableConfigurationProperties(McpProperties.class)
public class McpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public McpProvider httpMcpProvider(McpProperties properties, ObjectMapper objectMapper) {
        return new HttpMcpProvider(properties, objectMapper);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.openjiuwen.core.singleagent.BaseAgent")
    @ConditionalOnBean(McpProvider.class)
    static class OpenJiuwenMcpToolConfiguration {
        private static final Logger LOG = LoggerFactory.getLogger(OpenJiuwenMcpToolConfiguration.class);

        @Bean
        @ConditionalOnMissingBean
        public OpenJiuwenMcpToolInstaller openJiuwenMcpToolInstaller(
                McpProvider mcpProvider,
                ObjectProvider<OpenJiuwenAgentRuntimeHandler> handlers) {
            OpenJiuwenMcpToolInstaller installer = new OpenJiuwenMcpToolInstaller(mcpProvider);
            int count = 0;
            for (OpenJiuwenAgentRuntimeHandler handler : handlers.orderedStream().toList()) {
                handler.setMcpToolInstaller(installer);
                count++;
                LOG.info("installed MCP tool installer into openjiuwen handler agentId={}", handler.agentId());
            }
            if (count == 0) {
                LOG.warn("MCP tool installer created but no OpenJiuwenAgentRuntimeHandler beans found");
            }
            return installer;
        }
    }
}
