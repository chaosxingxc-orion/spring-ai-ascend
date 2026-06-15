package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent-runtime")
public record RemoteAgentProperties(List<RemoteAgentProperties.RemoteAgent> remoteAgents) {

    public List<String> urls() {
        return remoteAgents == null ? List.of() : remoteAgents.stream()
                .map(RemoteAgent::getUrl)
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
            if (agent.getUrl() != null && !agent.getUrl().isBlank() && agent.getStreamTimeout() != null) {
                timeouts.putIfAbsent(agent.getUrl(), agent.getStreamTimeout());
            }
        }
        return Map.copyOf(timeouts);
    }

    /**
     * A remote A2A agent entry. Only {@code url} is required; {@code output}
     * routing configuration is optional.
     */
    public static final class RemoteAgent {
        private String url;
        private Duration streamTimeout;
        private OutputConfig output;

        public RemoteAgent() {
        }

        public RemoteAgent(String url) {
            this.url = url;
        }

        public RemoteAgent(String url, Duration streamTimeout) {
            this.url = url;
            this.streamTimeout = streamTimeout;
        }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public Duration getStreamTimeout() { return streamTimeout; }
        public void setStreamTimeout(Duration streamTimeout) { this.streamTimeout = streamTimeout; }

        public OutputConfig getOutput() { return output; }
        public void setOutput(OutputConfig output) { this.output = output; }

        /**
         * Effective default target for non-terminal output messages from this
         * remote agent. Falls back to {@link AgentExecutionResult.Target#BOTH}
         * when not configured.
         */
        public AgentExecutionResult.Target effectiveDefaultTarget() {
            if (output != null && output.getDefaultTarget() != null) {
                return output.getDefaultTarget();
            }
            return AgentExecutionResult.Target.BOTH;
        }

        /**
         * Effective target for the terminal completion message from this
         * remote agent. Falls back to {@link AgentExecutionResult.Target#BOTH}
         * when not configured.
         */
        public AgentExecutionResult.Target effectiveCompletionTarget() {
            if (output != null && output.getCompletionTarget() != null) {
                return output.getCompletionTarget();
            }
            return AgentExecutionResult.Target.BOTH;
        }
    }

    /**
     * Per-remote-agent output routing configuration. Both fields are optional;
     * unset values default to {@code BOTH}.
     */
    public static final class OutputConfig {
        private AgentExecutionResult.Target defaultTarget;
        private AgentExecutionResult.Target completionTarget;

        public OutputConfig() {
        }

        public OutputConfig(AgentExecutionResult.Target defaultTarget, AgentExecutionResult.Target completionTarget) {
            this.defaultTarget = defaultTarget;
            this.completionTarget = completionTarget;
        }

        public AgentExecutionResult.Target getDefaultTarget() { return defaultTarget; }
        public void setDefaultTarget(AgentExecutionResult.Target defaultTarget) { this.defaultTarget = defaultTarget; }

        public AgentExecutionResult.Target getCompletionTarget() { return completionTarget; }
        public void setCompletionTarget(AgentExecutionResult.Target completionTarget) { this.completionTarget = completionTarget; }
    }
}
