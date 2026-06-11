package com.huawei.ascend.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

/**
 * Locks the platform's A2A stream interpretation. The terminal-event and
 * post-terminal-cancellation cases are ported unchanged from the e2e
 * example's SampleA2aClientTest — they encode wire behavior proven against
 * the real runtime and must not drift.
 */
class A2aEventsTest {

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
                Map.of());
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
                Map.of());

        assertThat(A2aEvents.textFrom(List.of(message, status, artifactEvent)))
                .isEqualTo("message status artifact");
    }

    @Test
    void excludesAcceptedMessageFromUserVisibleAnswer() {
        Message accepted = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("message-accepted")
                .metadata(Map.of("accepted", Boolean.TRUE))
                .parts(List.of(new TextPart("execution enqueued")))
                .build();
        Message completed = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("message-completed")
                .metadata(Map.of("runStatus", "completed"))
                .parts(List.of(new TextPart("pong")))
                .build();

        assertThat(A2aEvents.textFrom(List.of(accepted, completed))).isEqualTo("pong");
    }

    @Test
    void extractsTextFromNonStreamingTaskResult() {
        // The non-streaming SendMessage path answers a single Task: its status
        // message and artifacts carry the user-visible text.
        Message statusMessage = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("message-3")
                .parts(List.of(new TextPart("status ")))
                .build();
        Artifact artifact = Artifact.builder()
                .artifactId("artifact-1")
                .parts(List.of(new TextPart("artifact")))
                .build();
        Task task = new Task(
                "task-1",
                "session-1",
                new TaskStatus(TaskState.TASK_STATE_COMPLETED, statusMessage, null),
                List.of(artifact),
                List.of(),
                Map.of());

        assertThat(A2aEvents.textFrom(List.of(task))).isEqualTo("status artifact");
    }

    @Test
    void recognizesEveryRuntimeTerminalRunStatusIncludingCanceledSpelling() {
        // Wire values are RunStatus.wire() (lower-cased enum names): completed/failed/canceled/rejected.
        assertThat(A2aEvents.isTerminal(messageWithRunStatus("completed"))).isTrue();
        assertThat(A2aEvents.isTerminal(messageWithRunStatus("failed"))).isTrue();
        assertThat(A2aEvents.isTerminal(messageWithRunStatus("canceled"))).isTrue();
        assertThat(A2aEvents.isTerminal(messageWithRunStatus("rejected"))).isTrue();
        // A paused/waiting run is not stream-terminal.
        assertThat(A2aEvents.isTerminal(messageWithRunStatus("in_progress"))).isFalse();
        assertThat(A2aEvents.isTerminal(messageWithRunStatus("incomplete"))).isFalse();
    }

    @Test
    void recognizesFinalA2aTaskStatusUpdateAsTerminal() {
        TaskStatusUpdateEvent completed = new TaskStatusUpdateEvent(
                "task-1",
                new TaskStatus(TaskState.TASK_STATE_COMPLETED, null, null),
                "session-1",
                Map.of());
        TaskStatusUpdateEvent working = new TaskStatusUpdateEvent(
                "task-1",
                new TaskStatus(TaskState.TASK_STATE_WORKING, null, null),
                "session-1",
                Map.of());

        assertThat(A2aEvents.isTerminal(completed)).isTrue();
        assertThat(A2aEvents.isTerminal(working)).isFalse();
    }

    @Test
    void recognizesFinalTaskSnapshotAsTerminal() {
        // The OSS client auto-closes the SSE subscription on a final Task
        // snapshot; not treating it as terminal would misread the resulting
        // unsubscribe as a pre-terminal transport failure.
        Task completed = new Task(
                "task-1",
                "session-1",
                new TaskStatus(TaskState.TASK_STATE_COMPLETED, null, null),
                List.of(),
                List.of(),
                Map.of());
        Task working = new Task(
                "task-1",
                "session-1",
                new TaskStatus(TaskState.TASK_STATE_WORKING, null, null),
                List.of(),
                List.of(),
                Map.of());

        assertThat(A2aEvents.isTerminal(completed)).isTrue();
        assertThat(A2aEvents.isTerminal(working)).isFalse();
    }

    @Test
    void inputAndAuthRequiredEndTheTurnWithoutEndingTheRun() {
        // The runtime's INTERRUPTED route emits a NON-final input-required
        // status update and keeps the SSE stream open for the suspended run:
        // the stream must complete on it (turn-ending) while the run itself
        // is not terminal.
        TaskStatusUpdateEvent inputRequired = statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED);
        TaskStatusUpdateEvent authRequired = statusUpdate(TaskState.TASK_STATE_AUTH_REQUIRED);
        TaskStatusUpdateEvent working = statusUpdate(TaskState.TASK_STATE_WORKING);
        TaskStatusUpdateEvent completed = statusUpdate(TaskState.TASK_STATE_COMPLETED);

        assertThat(A2aEvents.isAwaitingInput(inputRequired)).isTrue();
        assertThat(A2aEvents.isAwaitingInput(authRequired)).isTrue();
        assertThat(A2aEvents.isTurnEnding(inputRequired)).isTrue();
        assertThat(A2aEvents.isTurnEnding(authRequired)).isTrue();
        assertThat(A2aEvents.isTerminal(inputRequired)).isFalse();
        assertThat(A2aEvents.isTerminal(authRequired)).isFalse();

        assertThat(A2aEvents.isTurnEnding(working)).isFalse();
        assertThat(A2aEvents.isAwaitingInput(working)).isFalse();
        assertThat(A2aEvents.isTurnEnding(completed)).isTrue();
        assertThat(A2aEvents.isAwaitingInput(completed)).isFalse();
    }

    @Test
    void recognizesAwaitingInputOnTaskSnapshot() {
        // The non-streaming SendMessage path answers a Task snapshot; an
        // input-required snapshot must read as awaiting input there too.
        Task inputRequired = new Task(
                "task-1",
                "session-1",
                new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED, null, null),
                List.of(),
                List.of(),
                Map.of());

        assertThat(A2aEvents.isAwaitingInput(inputRequired)).isTrue();
        assertThat(A2aEvents.isTurnEnding(inputRequired)).isTrue();
        assertThat(A2aEvents.isTerminal(inputRequired)).isFalse();
    }

    @Test
    void cancellationIsNormalCompletionOnlyAfterTerminalEvent() {
        CancellationException cancel = new CancellationException("sse unsubscribed");
        // Post-terminal cancellation (the SDK's normal unsubscribe) is NOT a failure.
        assertThat(A2aEvents.isFailureError(cancel, true)).isFalse();
        // Pre-terminal cancellation (partial stream / transport break) IS a failure.
        assertThat(A2aEvents.isFailureError(cancel, false)).isTrue();
        // A cancellation nested inside another throwable, after terminal, is still tolerated.
        assertThat(A2aEvents.isFailureError(new RuntimeException("io", cancel), true)).isFalse();
        // Any non-cancellation error is always a failure, even after a terminal event.
        assertThat(A2aEvents.isFailureError(new RuntimeException("transport reset"), true)).isTrue();
    }

    private static TaskStatusUpdateEvent statusUpdate(TaskState state) {
        return new TaskStatusUpdateEvent(
                "task-1",
                new TaskStatus(state, null, null),
                "session-1",
                Map.of());
    }

    private static Message messageWithRunStatus(String runStatus) {
        return Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("message-" + runStatus)
                .metadata(Map.of("runStatus", runStatus))
                .parts(List.of(new TextPart("x")))
                .build();
    }
}
