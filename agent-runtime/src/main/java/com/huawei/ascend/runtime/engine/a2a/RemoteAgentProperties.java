package com.huawei.ascend.runtime.engine.a2a;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
