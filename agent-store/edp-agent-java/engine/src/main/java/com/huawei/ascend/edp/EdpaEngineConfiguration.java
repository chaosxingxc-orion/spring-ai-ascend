package com.huawei.ascend.edp;

import com.huawei.ascend.edp.config.EdpaSpringBootConfig;
import com.huawei.ascend.edp.config.RedisConfig;
import com.huawei.ascend.edp.handler.EdpaRuntimeHandler;
import com.huawei.ascend.edp.todo.RedisTodoStore;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * EDPAgent Spring Bean 配置。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>创建 EDPAgent RuntimeHandler Bean。</li>
 *     <li>从 application.yml 注入配置路径和场景目录。</li>
 *     <li>model / versatile 配置已迁移到 application.yml，通过 {@link EdpaSpringBootConfig} 自动绑定。</li>
 *     <li>Spring Boot 原生支持 {@code ${ENV_VAR:default}} 占位符替换，不再需要手写环境变量覆盖逻辑。</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EdpaSpringBootConfig.class)
public class EdpaEngineConfiguration {

    /**
     * 创建并初始化 EDPAgent runtime handler。
     *
     * <p>作用：agent-runtime 通过该 Bean 获取可处理 A2A 请求的 OpenJiuwen AgentRuntimeHandler。</p>
     *
     * @param springBootConfig model / versatile 配置（Spring Boot 自动绑定）
     * @param configPath EDP 专有配置路径，来自 edpa.agent.config-path
     * @param scenarioHome 场景目录路径，来自 edpa.agent.scenario-home
     * @return 已初始化的 OpenJiuwenAgentRuntimeHandler Bean
     */
    @Bean
    OpenJiuwenAgentRuntimeHandler edpaRuntimeHandler(
            EdpaSpringBootConfig springBootConfig,
            @Value("${edpa.agent.config-path}") String configPath,
            @Value("${edpa.agent.scenario-home}") String scenarioHome,
            RedisTodoStore redisTodoStore) {
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();
        handler.init(springBootConfig, configPath, scenarioHome);
        return handler;
    }
}
