package com.huawei.ascend.runtime.access.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.access.AgentNotification;
import com.huawei.ascend.runtime.access.NotificationType;
import com.huawei.ascend.runtime.common.AgentResponseEvent;
import com.huawei.ascend.runtime.common.ResponseStatus;
import com.huawei.ascend.runtime.common.ResponseType;
import com.huawei.ascend.runtime.common.Message;
import com.huawei.ascend.runtime.common.RunStatus;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class A2aOutputMapperTest {

    private final A2aOutputMapper mapper = new A2aOutputMapper();

    @Test
    void mapsCompletedNotificationToFinalResponseEvent() {
        AgentNotification notification = notification(
                NotificationType.LLM_RESULT,
                RunStatus.COMPLETED,
                List.of(Message.assistant("pong")),
                null,
                true);

        AgentResponseEvent event = mapper.toResponseEvent(notification);

        assertThat(event.responseType()).isEqualTo(ResponseType.FINAL);
        assertThat(event.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(event.output()).isEqualTo("pong");
    }

    @Test
    void mapsRunningOutputToDeltaResponseEvent() {
        AgentNotification notification = notification(
                NotificationType.LLM_RESULT,
                RunStatus.IN_PROGRESS,
                List.of(Message.assistant("po")),
                null,
                false);

        AgentResponseEvent event = mapper.toResponseEvent(notification);

        assertThat(event.responseType()).isEqualTo(ResponseType.DELTA);
        assertThat(event.status()).isEqualTo(ResponseStatus.RUNNING);
        assertThat(event.output()).isEqualTo("po");
    }

    @Test
    void mapsFailedNotificationToErrorResponseEvent() {
        AgentNotification notification = notification(
                NotificationType.ERROR,
                RunStatus.FAILED,
                List.of(),
                new AgentNotification.RunError("RUNTIME_ERROR", "boom"),
                true);

        AgentResponseEvent event = mapper.toResponseEvent(notification);

        assertThat(event.responseType()).isEqualTo(ResponseType.ERROR);
        assertThat(event.status()).isEqualTo(ResponseStatus.FAILED);
        assertThat(event.error().code()).isEqualTo("RUNTIME_ERROR");
        assertThat(event.error().message()).isEqualTo("boom");
    }

    private static AgentNotification notification(
            NotificationType type,
            RunStatus status,
            List<Message> output,
            AgentNotification.RunError error,
            boolean terminal) {
        return new AgentNotification(
                "tenant-1",
                "session-1",
                "task-1",
                type,
                status,
                output,
                error,
                Map.of("requestId", "request-1", "userId", "user-1", "agentId", "agent-1"),
                terminal);
    }
}
