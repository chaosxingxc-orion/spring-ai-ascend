package com.huawei.ascend.versatile;

import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.versatile.VersatileAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.versatile.VersatileClient;
import com.huawei.ascend.runtime.engine.versatile.VersatileMessageAdapter;
import com.huawei.ascend.runtime.engine.versatile.VersatileProperties;
import com.huawei.ascend.runtime.engine.versatile.VersatileStreamAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册 Versatile 工作流代理 Agent 为可被 A2A 路由的 Handler。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>组合 agent-runtime 的 {@link VersatileClient}（REST/SSE 调用）、
 *         {@link VersatileMessageAdapter}（a2a→REST 提参）、
 *         {@link VersatileStreamAdapter}（SSE 节点→Target.USER/LLM 路由），
 *         注册为 {@link AgentRuntimeHandler} Bean。</li>
 *     <li>透传/结果分流由 {@code versatile.result-node-type} 等配置驱动，业务代码极薄。</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VersatileProperties.class)
public class VersatileAgentConfiguration {

    /** A2A 路由使用的 Agent 标识，同时是上游远程工具名的派生来源。 */
    public static final String AGENT_ID = "versatile-agent";

    @Bean
    AgentRuntimeHandler versatileAgentRuntimeHandler(
            VersatileProperties props,
            // node_type 与 node_name 同时命中时才视为终态结果节点，其余 message 完整 JSON 透传。
            @Value("${versatile.result-node-name:}") String resultNodeName) {
        VersatileClient client = new VersatileClient(props);
        return new VersatileAgentRuntimeHandler(
                AGENT_ID,
                "Versatile Agent",
                "Versatile workflow proxy agent — relays A2A requests to a remote versatile REST API",
                client,
                new VersatileMessageAdapter(props),
                new RawNodePassthroughVersatileStreamAdapter(props, resultNodeName));
    }
}
