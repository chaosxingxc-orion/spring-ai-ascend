package com.huawei.ascend.examples.workmate.team.backend;

import com.huawei.ascend.examples.workmate.a2a.A2aMemberClient;
import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteRequest;
import com.huawei.ascend.examples.workmate.config.WorkmateMemberRuntimeProperties;
import com.huawei.ascend.examples.workmate.runtime.ConfiguredMemberRuntimeLifecycle;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link MemberBackend} that runs a member on a remote A2A member runtime (W21).
 *
 * <p>Selected when the member's expert resolves to a configured, enabled remote runtime;
 * otherwise the {@link LocalMemberBackend} fallback handles it.</p>
 */
@Component
public class A2aMemberBackend implements MemberBackend {

    private static final Logger LOG = LoggerFactory.getLogger(A2aMemberBackend.class);

    private final ConfiguredMemberRuntimeLifecycle runtimeLifecycle;
    private final WorkmateMemberRuntimeProperties memberRuntimeProperties;

    public A2aMemberBackend(
            ConfiguredMemberRuntimeLifecycle runtimeLifecycle,
            WorkmateMemberRuntimeProperties memberRuntimeProperties) {
        this.runtimeLifecycle = runtimeLifecycle;
        this.memberRuntimeProperties = memberRuntimeProperties;
    }

    @Override
    public String kind() {
        return "a2a";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean supports(MemberDescriptor member) {
        if (!memberRuntimeProperties.isEnabled() || member.expertId() == null) {
            return false;
        }
        return runtimeLifecycle.memberBaseUrl(member.expertId()).isPresent();
    }

    @Override
    public MemberRunResult run(MemberRunContext context) {
        String memberExpertId = context.memberExpertId();
        Optional<URI> remote = runtimeLifecycle.memberBaseUrl(memberExpertId);
        if (remote.isEmpty()) {
            return MemberRunResult.failed("No remote runtime configured for member: " + memberExpertId);
        }
        URI baseUrl = remote.get();
        if (!runtimeLifecycle.probeHealthy(baseUrl)) {
            String error = "Member runtime unhealthy: " + baseUrl;
            LOG.warn("{} expertId={}", error, memberExpertId);
            return MemberRunResult.failed(error);
        }
        ExecuteRequest request = context.request();
        try {
            Duration timeout = Duration.ofSeconds(memberRuntimeProperties.getRequestTimeoutSeconds());
            A2aMemberClient client = new A2aMemberClient(baseUrl, timeout);
            String sessionId = request.session().getId().toString();
            String text = client.sendMessage(sessionId, request.taskId(), memberExpertId, request.message());
            return MemberRunResult.ok(text, "a2a");
        } catch (Exception ex) {
            LOG.warn(
                    "Remote member A2A failed expertId={} baseUrl={} taskId={}",
                    memberExpertId,
                    baseUrl,
                    request.taskId(),
                    ex);
            String message = ex.getMessage() != null ? ex.getMessage() : "remote member run failed";
            return MemberRunResult.failed(message);
        }
    }
}
