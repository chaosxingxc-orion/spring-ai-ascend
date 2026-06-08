package com.huawei.ascend.runtime.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentResponseEventTest {

    @Test
    void finalTextBuildsTerminalCompletedEvent() {
        AgentResponseEvent event = AgentResponseEvent.finalText(
                "request-1", "tenant-1", "user-1", "agent-1", "session-1", "task-1", "pong", Map.of("k", "v"));

        assertThat(event.requestId()).isEqualTo("request-1");
        assertThat(event.sequence()).isEqualTo(1L);
        assertThat(event.responseType()).isEqualTo(ResponseType.FINAL);
        assertThat(event.status()).isEqualTo(ResponseStatus.COMPLETED);
        assertThat(event.output()).isEqualTo("pong");
        assertThat(event.error()).isNull();
        assertThat(event.metadata()).containsEntry("k", "v");
        assertThat(event.terminal()).isTrue();
    }

    @Test
    void errorBuildsTerminalFailedEvent() {
        AgentResponseEvent event = AgentResponseEvent.error(
                "request-2", "tenant-1", "user-1", "agent-1", "session-1", "task-2",
                new ErrorInfo("RUNTIME_ERROR", "handler failed"));

        assertThat(event.responseType()).isEqualTo(ResponseType.ERROR);
        assertThat(event.status()).isEqualTo(ResponseStatus.FAILED);
        assertThat(event.error().code()).isEqualTo("RUNTIME_ERROR");
        assertThat(event.terminal()).isTrue();
    }

    @Test
    void requiresCoreIdentity() {
        assertThatThrownBy(() -> AgentResponseEvent.finalText(
                "request-1", " ", "user-1", "agent-1", "session-1", "task-1", "pong", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }
}
