package com.huawei.ascend.service.remote;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Remote-agent topology configuration. Keeps the historical {@code agent-runtime.remote-agents}
 * prefix so existing deployments keep working, but the topology is service-plane configuration:
 * the runtime consumes the resolved catalog, never the raw URL list.
 */
@ConfigurationProperties(prefix = "agent-runtime")
public record RemoteAgentProperties(List<RemoteAgent> remoteAgents) {

    public List<String> urls() {
        return remoteAgents == null ? List.of() : remoteAgents.stream()
                .map(RemoteAgent::url)
                .filter(url -> url != null && !url.isBlank())
                .toList();
    }

    public record RemoteAgent(String url) {
    }
}
