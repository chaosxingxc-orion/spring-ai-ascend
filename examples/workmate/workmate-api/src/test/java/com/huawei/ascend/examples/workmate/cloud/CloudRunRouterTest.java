package com.huawei.ascend.examples.workmate.cloud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.ascend.examples.workmate.agent.SseRunEventMapper;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.config.WorkmateCloudProperties;
import com.huawei.ascend.examples.workmate.runtime.ConfiguredMemberRuntimeLifecycle;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class CloudRunRouterTest {

    @Mock
    private CloudSessionRepository cloudSessionRepository;

    @Mock
    private CloudSessionService cloudSessionService;

    @Mock
    private SessionPersistenceService sessionPersistenceService;

    @Mock
    private SessionService sessionService;

    private CloudRunRouter router;
    private WorkmateCloudProperties cloud;

    @BeforeEach
    void setUp() {
        cloud = new WorkmateCloudProperties(true, "http://localhost:8080", "img", "local-stub", "/workspace", false, 300);
        router = new CloudRunRouter(
                cloudSessionRepository,
                cloudSessionService,
                cloud,
                new ConfiguredMemberRuntimeLifecycle(new com.huawei.ascend.examples.workmate.config.WorkmateMemberRuntimeProperties()),
                sessionPersistenceService,
                sessionService,
                new SseRunEventMapper(new ObjectMapper()));
    }

    @Test
    void skipsRoutingWhenDisabled() {
        cloud = new WorkmateCloudProperties(true, "http://localhost:8080", "img", "local-stub", "/workspace", false, 300);
        router = new CloudRunRouter(
                cloudSessionRepository,
                cloudSessionService,
                cloud,
                new ConfiguredMemberRuntimeLifecycle(new com.huawei.ascend.examples.workmate.config.WorkmateMemberRuntimeProperties()),
                sessionPersistenceService,
                sessionService,
                new SseRunEventMapper(new ObjectMapper()));

        WorkmateSession session = session(UUID.randomUUID());
        boolean routed = router.tryExecuteRemote(
                session, "hi", "task-1", persistenceContext(), new SseEmitter(), new AtomicBoolean(true));

        assertThat(routed).isFalse();
        verify(cloudSessionRepository, never()).findFirstByLinkedSessionIdAndStatusInOrderByUpdatedAtDesc(any(), any());
    }

    @Test
    void skipsRoutingWhenNoLinkedCloudSession() {
        cloud = new WorkmateCloudProperties(true, "http://localhost:8080", "img", "local-stub", "/workspace", true, 300);
        router = new CloudRunRouter(
                cloudSessionRepository,
                cloudSessionService,
                cloud,
                new ConfiguredMemberRuntimeLifecycle(new com.huawei.ascend.examples.workmate.config.WorkmateMemberRuntimeProperties()),
                sessionPersistenceService,
                sessionService,
                new SseRunEventMapper(new ObjectMapper()));

        UUID sessionId = UUID.randomUUID();
        when(cloudSessionRepository.findFirstByLinkedSessionIdAndStatusInOrderByUpdatedAtDesc(
                        sessionId, List.of(CloudSessionStatus.RUNNING, CloudSessionStatus.SLEEPING)))
                .thenReturn(Optional.empty());

        boolean routed = router.tryExecuteRemote(
                session(sessionId), "hi", "task-1", persistenceContext(), new SseEmitter(), new AtomicBoolean(true));

        assertThat(routed).isFalse();
    }

    @Test
    void resolvesLinkedCloudSession() {
        UUID linkedId = UUID.randomUUID();
        CloudSession cloudSession = new CloudSession();
        cloudSession.setId(UUID.randomUUID());
        cloudSession.setStatus(CloudSessionStatus.RUNNING);
        when(cloudSessionRepository.findFirstByLinkedSessionIdAndStatusNotOrderByUpdatedAtDesc(
                        linkedId, CloudSessionStatus.DESTROYED))
                .thenReturn(Optional.of(cloudSession));

        assertThat(router.resolveLinkedCloudSession(linkedId)).contains(cloudSession);
    }

    private static WorkmateSession session(UUID id) {
        return new WorkmateSession(id, "t", "/tmp", SessionStatus.CREATED, "fund-analyst", PermissionMode.CRAFT);
    }

    private static RunPersistenceContext persistenceContext() {
        return RunPersistenceContext.forAudit(UUID.randomUUID(), "run-1");
    }
}
