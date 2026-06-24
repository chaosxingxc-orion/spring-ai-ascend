package com.huawei.ascend.examples.deepresearch.search.a2a;

import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the YAML assembly path (selected via {@code search-agent.yaml-classpath},
 * which the {@code stub} Spring profile overrides) into a single
 * {@link SearchAgentHandler} bean. agent-runtime discovers the handler through
 * the {@link AgentRuntimeHandler} SPI and serves it on the port declared in
 * {@code application.yaml}.
 */
@Configuration(proxyBeanMethods = false)
public class SearchAgentConfiguration {

    @Bean
    Path searchAgentYamlPath(@Value("${search-agent.yaml-classpath}") String yamlClasspath) {
        return ClasspathYamlExtractor.extract(yamlClasspath);
    }

    @Bean
    AgentRuntimeHandler searchAgentHandler(Path searchAgentYamlPath) {
        return new SearchAgentHandler(searchAgentYamlPath);
    }
}