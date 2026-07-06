package com.huawei.ascend.edp;

import com.huawei.ascend.edp.handler.EdpaRuntimeHandler;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * EDPAgent spike Spring Bean 配置。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>创建 EDPAgent RuntimeHandler Bean。</li>
 *     <li>从 application.yml 注入 edp-agent.yaml 和 edp-config.yaml 路径。</li>
 *     <li>在 Spring 容器启动期间完成 EDPAgent 初始化。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>{@link #edpaRuntimeHandler(String, String)}：向 Spring 容器暴露 OpenJiuwenAgentRuntimeHandler Bean。</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class EdpaSpikeConfiguration {

    /**
     * 创建并初始化 EDPAgent runtime handler。
     *
     * <p>作用：agent-runtime 通过该 Bean 获取可处理 A2A 请求的 OpenJiuwen AgentRuntimeHandler。</p>
     *
     * @param yamlPath 标准 agent 配置路径，来自 edpa.agent.yaml-path
     * @param configPath EDP 专有配置路径，来自 edpa.agent.config-path
     * @return 已初始化的 OpenJiuwenAgentRuntimeHandler Bean
     */
    @Bean
    OpenJiuwenAgentRuntimeHandler edpaRuntimeHandler(
            @Value("${edpa.agent.yaml-path}") String yamlPath,
            @Value("${edpa.agent.config-path}") String configPath) {
        // 第一步：创建 EDPAgent runtime handler，构造函数会声明固定 agentId。
        EdpaRuntimeHandler handler = new EdpaRuntimeHandler();

        // 第二步：加载配置、创建 DeepAgent、注册工具和 Rails。
        handler.init(yamlPath, configPath);

        // 第三步：返回父类型 Bean，供 agent-runtime 自动发现和注册。
        return handler;
    }
}
