package com.huawei.ascend.examples.a2a.versatile;

import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.versatile.VersatileAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.versatile.VersatileClient;
import com.huawei.ascend.runtime.engine.versatile.VersatileMessageAdapter;
import com.huawei.ascend.runtime.engine.versatile.VersatileProperties;
import com.huawei.ascend.runtime.engine.versatile.VersatileStreamAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the versatile workflow proxy agent as an A2A-routable handler.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VersatileProperties.class)
public class VersatileAgentConfiguration {

    public static final String AGENT_ID = "versatile-agent";

    @Bean
    AgentRuntimeHandler versatileAgentRuntimeHandler(VersatileProperties props) {
        VersatileClient client = new VersatileClient(props);
        return new VersatileAgentRuntimeHandler(
                AGENT_ID,
                "Versatile Agent",
                "Versatile workflow proxy agent — relays A2A requests to a remote versatile REST API",
                client,
                new VersatileMessageAdapter(props),
                new VersatileStreamAdapter());
    }
}
