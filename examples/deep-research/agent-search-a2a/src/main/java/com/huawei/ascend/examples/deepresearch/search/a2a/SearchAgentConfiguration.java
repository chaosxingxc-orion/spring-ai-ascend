package com.huawei.ascend.examples.deepresearch.search.a2a;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.mcp.HttpMcpProvider;
import com.huawei.ascend.runtime.engine.mcp.McpProperties;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.McpProvider;
import com.huawei.ascend.runtime.engine.spi.SkillHubProvider;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the YAML assembly path (selected via {@code search-agent.yaml-classpath},
 * which the {@code stub} Spring profile overrides) into a single
 * {@link SearchAgentHandler} bean. agent-runtime discovers the handler through
 * the {@link AgentRuntimeHandler} SPI and serves it on the port declared in
 * {@code application.yaml}.
 *
 * <p>Also publishes a filesystem-backed {@link SkillHubProvider} so the runtime
 * SkillHub auto-configuration activates (its condition is
 * {@code @ConditionalOnBean(SkillHubProvider.class)}). All directories under
 * {@code search-agent.skillhub.root} containing a {@code SKILL.md} file are
 * exposed as skills and installed into the ReActAgent on every execution.
 *
 * <p>An {@link McpProvider} bean is declared explicitly here as a workaround
 * for a Spring ordering bug in agent-runtime's {@code McpAutoConfiguration}:
 * its nested {@code OpenJiuwenMcpToolConfiguration} declares
 * {@code @ConditionalOnBean(McpProvider.class)} against the sibling
 * {@code @Bean httpMcpProvider} method in the same outer class, but
 * {@code @ConditionalOnBean} is evaluated at parse time before the outer
 * class's {@code @Bean} methods register, so the nested config never sees
 * the provider and the MCP tool installer is never attached. Filed upstream;
 * remove this bean once {@code OpenJiuwenMcpToolConfiguration} is hoisted
 * into its own auto-configuration ordered after {@code McpAutoConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(McpProperties.class)
public class SearchAgentConfiguration {

    @Bean
    Path searchAgentYamlPath(@Value("${search-agent.yaml-classpath}") String yamlClasspath) {
        return ClasspathYamlExtractor.extract(yamlClasspath);
    }

    @Bean
    AgentRuntimeHandler searchAgentHandler(Path searchAgentYamlPath) {
        return new SearchAgentHandler(searchAgentYamlPath);
    }

    @Bean
    SkillHubProvider searchAgentSkillHubProvider(
            @Value("${search-agent.skillhub.root:skills}") String skillRoot) {
        return new LocalDirectorySkillHubProvider(Path.of(skillRoot));
    }

    /**
     * Workaround for agent-runtime's MCP auto-config requiring a Jackson 2
     * {@code com.fasterxml.jackson.databind.ObjectMapper} bean. Spring Boot 4
     * only auto-publishes the Jackson 3 ({@code tools.jackson.databind})
     * ObjectMapper, so {@link com.huawei.ascend.runtime.engine.mcp.McpAutoConfiguration}
     * fails to start with "No qualifying bean of type 'ObjectMapper'". Filed as
     * an upstream issue; remove this bean once agent-runtime migrates to
     * Jackson 3 or its MCP auto-config gates on the legacy ObjectMapper bean.
     */
    @Bean
    ObjectMapper mcpJackson2ObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    McpProvider httpMcpProvider(McpProperties properties, ObjectMapper mcpJackson2ObjectMapper) {
        return new HttpMcpProvider(properties, mcpJackson2ObjectMapper);
    }
}