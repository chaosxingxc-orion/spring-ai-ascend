package com.huawei.ascend.examples.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Test;

class SampleA2aClientTest {

    @Test
    void resolvesRelativeJsonRpcInterfaceAgainstDiscoveredServiceBaseUri() {
        SampleA2aClient client = new SampleA2aClient(URI.create("http://localhost:8080"), Duration.ofSeconds(1));
        AgentCard card = AgentCard.builder()
                .name("sample")
                .description("sample")
                .version("1")
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a")))
                .build();

        assertThat(client.jsonRpcEndpoint(card)).isEqualTo("http://localhost:8080/a2a");
    }

    @Test
    void extractsTextFromAllA2aStreamingEventShapes() {
        Message message = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("message-1")
                .parts(List.of(new TextPart("message ")))
                .build();
        Message statusMessage = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("message-2")
                .parts(List.of(new TextPart("status ")))
                .build();
        TaskStatusUpdateEvent status = new TaskStatusUpdateEvent(
                "task-1",
                new TaskStatus(TaskState.TASK_STATE_COMPLETED, statusMessage, null),
                "session-1",
                java.util.Map.of());
        Artifact artifact = Artifact.builder()
                .artifactId("artifact-1")
                .parts(List.of(new TextPart("artifact")))
                .build();
        TaskArtifactUpdateEvent artifactEvent = new TaskArtifactUpdateEvent(
                "task-1",
                artifact,
                "session-1",
                Boolean.TRUE,
                Boolean.TRUE,
                java.util.Map.of());

        assertThat(SampleA2aClient.textFrom(List.of(message, status, artifactEvent)))
                .isEqualTo("message status artifact");
    }

    @Test
    void excludesAcceptedMessageFromUserVisibleAnswer() {
        Message accepted = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("message-accepted")
                .metadata(java.util.Map.of("accepted", Boolean.TRUE))
                .parts(List.of(new TextPart("execution enqueued")))
                .build();
        Message completed = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("message-completed")
                .metadata(java.util.Map.of("runStatus", "completed"))
                .parts(List.of(new TextPart("pong")))
                .build();

        assertThat(SampleA2aClient.textFrom(List.of(accepted, completed))).isEqualTo("pong");
    }

    @Test
    void recognizesEveryRuntimeTerminalRunStatusIncludingCanceledSpelling() {
        // Wire values are RunStatus.wire() (lower-cased enum names): completed/failed/canceled/rejected.
        assertThat(SampleA2aClient.isTerminal(messageWithRunStatus("completed"))).isTrue();
        assertThat(SampleA2aClient.isTerminal(messageWithRunStatus("failed"))).isTrue();
        assertThat(SampleA2aClient.isTerminal(messageWithRunStatus("canceled"))).isTrue();
        assertThat(SampleA2aClient.isTerminal(messageWithRunStatus("rejected"))).isTrue();
        // A paused/waiting run is not stream-terminal.
        assertThat(SampleA2aClient.isTerminal(messageWithRunStatus("in_progress"))).isFalse();
        assertThat(SampleA2aClient.isTerminal(messageWithRunStatus("incomplete"))).isFalse();
    }

    @Test
    void cancellationIsNormalCompletionOnlyAfterTerminalEvent() {
        java.util.concurrent.CancellationException cancel =
                new java.util.concurrent.CancellationException("sse unsubscribed");
        // Post-terminal cancellation (the SDK's normal unsubscribe) is NOT a failure.
        assertThat(SampleA2aClient.isFailureError(cancel, true)).isFalse();
        // Pre-terminal cancellation (partial stream / transport break) IS a failure.
        assertThat(SampleA2aClient.isFailureError(cancel, false)).isTrue();
        // A cancellation nested inside another throwable, after terminal, is still tolerated.
        assertThat(SampleA2aClient.isFailureError(new RuntimeException("io", cancel), true)).isFalse();
        // Any non-cancellation error is always a failure, even after a terminal event.
        assertThat(SampleA2aClient.isFailureError(new RuntimeException("transport reset"), true)).isTrue();
    }

    private static Message messageWithRunStatus(String runStatus) {
        return Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("message-" + runStatus)
                .metadata(java.util.Map.of("runStatus", runStatus))
                .parts(List.of(new TextPart("x")))
                .build();
    }
}
