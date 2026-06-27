package com.huawei.ascend.examples.workmate.cloud;

import com.huawei.ascend.examples.workmate.a2a.A2aMemberClient;
import com.huawei.ascend.examples.workmate.agent.SseRunEventMapper;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.config.WorkmateCloudProperties;
import com.huawei.ascend.examples.workmate.runtime.RuntimeLifecycle;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * W43.1 — when a local session is linked to a RUNNING cloud sandbox, optionally route prompts via outbound A2A
 * (path C double-hop seam). Falls back to in-process {@link com.huawei.ascend.examples.workmate.agent.AgentRunExecutor}.
 */
@Component
public class CloudRunRouter {

    private static final Logger LOG = LoggerFactory.getLogger(CloudRunRouter.class);

    private static final List<CloudSessionStatus> ROUTABLE_STATUSES =
            List.of(CloudSessionStatus.RUNNING, CloudSessionStatus.SLEEPING);

    private final CloudSessionRepository cloudSessionRepository;
    private final CloudSessionService cloudSessionService;
    private final WorkmateCloudProperties cloud;
    private final RuntimeLifecycle runtimeLifecycle;
    private final SessionPersistenceService sessionPersistenceService;
    private final SessionService sessionService;
    private final SseRunEventMapper eventMapper;

    public CloudRunRouter(
            CloudSessionRepository cloudSessionRepository,
            CloudSessionService cloudSessionService,
            WorkmateCloudProperties cloud,
            RuntimeLifecycle runtimeLifecycle,
            SessionPersistenceService sessionPersistenceService,
            SessionService sessionService,
            SseRunEventMapper eventMapper) {
        this.cloudSessionRepository = cloudSessionRepository;
        this.cloudSessionService = cloudSessionService;
        this.cloud = cloud;
        this.runtimeLifecycle = runtimeLifecycle;
        this.sessionPersistenceService = sessionPersistenceService;
        this.sessionService = sessionService;
        this.eventMapper = eventMapper;
    }

    public Optional<CloudSession> resolveLinkedCloudSession(UUID linkedSessionId) {
        return cloudSessionRepository.findFirstByLinkedSessionIdAndStatusNotOrderByUpdatedAtDesc(
                linkedSessionId, CloudSessionStatus.DESTROYED);
    }

    public boolean routingEnabled() {
        return cloud.enabled() && cloud.routePrompts();
    }

    /**
     * @return true when the prompt was handled remotely (success or terminal remote failure); false to fall back local.
     */
    public boolean tryExecuteRemote(
            WorkmateSession session,
            String message,
            String taskId,
            RunPersistenceContext persistenceContext,
            SseEmitter emitter,
            AtomicBoolean clientConnected) {
        if (!routingEnabled()) {
            return false;
        }
        Optional<CloudSession> cloudSessionOpt = cloudSessionRepository
                .findFirstByLinkedSessionIdAndStatusInOrderByUpdatedAtDesc(session.getId(), ROUTABLE_STATUSES);
        if (cloudSessionOpt.isEmpty()) {
            return false;
        }
        CloudSession cloudSession = cloudSessionOpt.get();
        if (cloudSession.getStatus() == CloudSessionStatus.SLEEPING) {
            try {
                cloudSessionService.wake(cloudSession.getId());
                cloudSession = cloudSessionRepository
                        .findById(cloudSession.getId())
                        .orElse(cloudSession);
            } catch (Exception ex) {
                LOG.warn(
                        "Cloud auto-wake failed cloudSessionId={} linkedSessionId={}",
                        cloudSession.getId(),
                        session.getId(),
                        ex);
                return false;
            }
        }
        if (cloudSession.getStatus() != CloudSessionStatus.RUNNING) {
            return false;
        }
        String runtimeUrl = cloudSession.getRuntimeBaseUrl();
        if (runtimeUrl == null || runtimeUrl.isBlank()) {
            return false;
        }
        URI baseUrl = URI.create(runtimeUrl.trim());
        if (!runtimeLifecycle.probeHealthy(baseUrl)) {
            LOG.warn(
                    "Cloud runtime unhealthy cloudSessionId={} baseUrl={}",
                    cloudSession.getId(),
                    baseUrl);
            return false;
        }
        String expertId = session.getExpertId();
        if (expertId == null || expertId.isBlank()) {
            return false;
        }
        try {
            Duration timeout = Duration.ofSeconds(cloud.requestTimeoutSeconds());
            A2aMemberClient client = new A2aMemberClient(baseUrl, timeout);
            String text = client.sendCloudMessage(session.getId().toString(), taskId, expertId, message);
            sessionPersistenceService.persistRunEvent(
                    persistenceContext,
                    "cloud.run.routed",
                    Map.of(
                            "cloudSessionId",
                            cloudSession.getId().toString(),
                            "runtimeBaseUrl",
                            baseUrl.toString(),
                            "sandboxId",
                            nullToEmpty(cloudSession.getSandboxId())));
            if (text != null && !text.isBlank()) {
                sessionPersistenceService.appendAssistantDelta(persistenceContext, text);
                emit(emitter, clientConnected, eventMapper.messageDelta(text));
            }
            sessionPersistenceService.finalizeAssistant(persistenceContext);
            sessionPersistenceService.persistRunEvent(persistenceContext, "run.completed", Map.of());
            emit(emitter, clientConnected, eventMapper.runCompleted());
            sessionService.updateStatus(session.getId(), SessionStatus.COMPLETED);
            return true;
        } catch (Exception ex) {
            LOG.warn(
                    "Cloud A2A prompt failed cloudSessionId={} baseUrl={} taskId={}",
                    cloudSession.getId(),
                    baseUrl,
                    taskId,
                    ex);
            String error = ex.getMessage() != null ? ex.getMessage() : "cloud A2A run failed";
            sessionPersistenceService.recordSystemMessage(persistenceContext, error, "error");
            sessionPersistenceService.persistRunEvent(persistenceContext, "run.failed", Map.of("message", error));
            emit(emitter, clientConnected, eventMapper.runFailed(error));
            sessionService.updateStatus(session.getId(), SessionStatus.STOPPED);
            return true;
        }
    }

    private static void emit(
            SseEmitter emitter, AtomicBoolean clientConnected, SseEmitter.SseEventBuilder event) {
        if (emitter == null || clientConnected == null || !clientConnected.get()) {
            return;
        }
        try {
            emitter.send(event);
        } catch (Exception ex) {
            clientConnected.set(false);
        }
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
