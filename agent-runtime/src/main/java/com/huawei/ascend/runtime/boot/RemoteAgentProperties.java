package com.huawei.ascend.runtime.boot;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent-runtime")
public record RemoteAgentProperties(List<RemoteAgent> remoteAgents) {

    public List<String> urls() {
        return remoteAgents == null ? List.of() : remoteAgents.stream()
                .map(RemoteAgent::url)
                .filter(url -> url != null && !url.isBlank())
                .toList();
    }

    /**
     * Configured per-remote stream timeouts keyed by the configured url; entries
     * without an explicit timeout are absent and fall back to the adapter default.
     */
    public Map<String, Duration> streamTimeouts() {
        if (remoteAgents == null) {
            return Map.of();
        }
        Map<String, Duration> timeouts = new LinkedHashMap<>();
        for (RemoteAgent agent : remoteAgents) {
            if (agent.url() != null && !agent.url().isBlank() && agent.streamTimeout() != null) {
                timeouts.putIfAbsent(agent.url(), agent.streamTimeout());
            }
        }
        return Map.copyOf(timeouts);
    }

    public record RemoteAgent(String url, Duration streamTimeout) {
    }
}
