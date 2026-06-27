package com.huawei.ascend.examples.workmate.team;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteOutcome;
import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteRequest;
import com.huawei.ascend.examples.workmate.a2a.A2aMemberClient;
import com.huawei.ascend.examples.workmate.config.WorkmateMemberRuntimeProperties;
import com.huawei.ascend.examples.workmate.runtime.ConfiguredMemberRuntimeLifecycle;
import com.huawei.ascend.examples.workmate.runtime.RuntimeLifecycle;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Routes member sub-runs to in-process {@link AgentRunExecutor} or outbound A2A member-runtime (W21).
 */
@Component
public class MemberRunRouter {

    private static final Logger LOG = LoggerFactory.getLogger(MemberRunRouter.class);

    private final AgentRunExecutor agentRunExecutor;
    private final RuntimeLifecycle runtimeLifecycle;
    private final WorkmateMemberRuntimeProperties memberRuntimeProperties;

    public MemberRunRouter(
            AgentRunExecutor agentRunExecutor,
            ConfiguredMemberRuntimeLifecycle runtimeLifecycle,
            WorkmateMemberRuntimeProperties memberRuntimeProperties) {
        this.agentRunExecutor = agentRunExecutor;
        this.runtimeLifecycle = runtimeLifecycle;
        this.memberRuntimeProperties = memberRuntimeProperties;
    }

    public ExecuteOutcome executeMember(ExecuteRequest request, String memberExpertId) {
        Optional<URI> remote = runtimeLifecycle.memberBaseUrl(memberExpertId);
        if (remote.isPresent()) {
            return executeRemote(request, memberExpertId, remote.get());
        }
        return agentRunExecutor.execute(request);
    }

    private ExecuteOutcome executeRemote(ExecuteRequest request, String memberExpertId, URI baseUrl) {
        if (!runtimeLifecycle.probeHealthy(baseUrl)) {
            String error = "Member runtime unhealthy: " + baseUrl;
            LOG.warn("{} expertId={}", error, memberExpertId);
            return new ExecuteOutcome("", true, error);
        }
        try {
            Duration timeout = Duration.ofSeconds(memberRuntimeProperties.getRequestTimeoutSeconds());
            A2aMemberClient client = new A2aMemberClient(baseUrl, timeout);
            String sessionId = request.session().getId().toString();
            String text = client.sendMessage(sessionId, request.taskId(), memberExpertId, request.message());
            return new ExecuteOutcome(text != null ? text : "", false, null);
        } catch (Exception ex) {
            LOG.warn(
                    "Remote member A2A failed expertId={} baseUrl={} taskId={}",
                    memberExpertId,
                    baseUrl,
                    request.taskId(),
                    ex);
            String message = ex.getMessage() != null ? ex.getMessage() : "remote member run failed";
            return new ExecuteOutcome("", true, message);
        }
    }

    public boolean usesRemoteMember(String memberExpertId) {
        return memberRuntimeProperties.isEnabled() && runtimeLifecycle.memberBaseUrl(memberExpertId).isPresent();
    }

    public Optional<URI> remoteBaseUrl(String memberExpertId) {
        return runtimeLifecycle.memberBaseUrl(memberExpertId);
    }
}
