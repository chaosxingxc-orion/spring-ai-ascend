package com.huawei.ascend.runtime.engine.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.GetExtendedAgentCardParams;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class A2aRemoteAgentOutboundAdapterTest {

    @Test
    void invokeStreamsRemoteEventsIntoRemoteAgentResults() {
        RecordingTransport transport = new RecordingTransport(List.of(
                agentMessage("part-1", "remote-task-1", "remote-ctx-1"),
                artifact("part-2", "remote-task-1", "remote-ctx-1"),
                status(TaskState.TASK_STATE_INPUT_REQUIRED, "need more", "remote-task-1", "remote-ctx-1")));
        A2aRemoteAgentOutboundAdapter adapter = new A2aRemoteAgentOutboundAdapter(id -> transport);

        List<RemoteAgentInvocationService.RemoteAgentResult> results = adapter.invoke(
                new RemoteAgentInvocationService.RemoteAgentRequest(
                        "remote-agent", null, null, "tool-call-1", "parent-task", "parent-ctx",
                        "conversation-1", "hello", Map.of()),
                null);

        assertThat(transport.requests).hasSize(1);
        Message sent = transport.requests.get(0).message();
        assertThat(sent.taskId()).isNull();
        assertThat(text(sent)).isEqualTo("hello");
        assertThat(results).extracting(RemoteAgentInvocationService.RemoteAgentResult::type)
                .containsExactly(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.MESSAGE,
                        RemoteAgentInvocationService.RemoteAgentResult.Type.ARTIFACT,
                        RemoteAgentInvocationService.RemoteAgentResult.Type.INPUT_REQUIRED);
        assertThat(results.get(2).remoteTaskId()).isEqualTo("remote-task-1");
        assertThat(results.get(2).remoteContextId()).isEqualTo("remote-ctx-1");
        assertThat(results.get(2).text()).isEqualTo("need more");
    }

    @Test
    void resumeIncludesExistingRemoteTaskAndMapsCompletedStatus() {
        RecordingTransport transport = new RecordingTransport(List.of(
                agentMessage("working", "remote-task-1", "remote-ctx-1"),
                status(TaskState.TASK_STATE_COMPLETED, "done", "remote-task-1", "remote-ctx-1")));
        A2aRemoteAgentOutboundAdapter adapter = new A2aRemoteAgentOutboundAdapter(id -> transport);

        List<RemoteAgentInvocationService.RemoteAgentResult> results = adapter.invoke(
                new RemoteAgentInvocationService.RemoteAgentRequest(
                        "remote-agent", "remote-task-1", "remote-ctx-1", "tool-call-1", "parent-task",
                        "parent-ctx", "conversation-1", "next", Map.of()),
                null);

        Message sent = transport.requests.get(0).message();
        assertThat(sent.taskId()).isEqualTo("remote-task-1");
        assertThat(sent.contextId()).isEqualTo("remote-ctx-1");
        assertThat(text(sent)).isEqualTo("next");
        assertThat(results).extracting(RemoteAgentInvocationService.RemoteAgentResult::type)
                .containsExactly(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.MESSAGE,
                        RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED);
        assertThat(results.get(1).text()).isEqualTo("done");
    }

    @Test
    void inputRequiredFollowedByStreamCancellationIsNotMappedAsFailure() {
        RecordingTransport transport = new RecordingTransport(List.of(
                agentMessage("working", "remote-task-1", "remote-ctx-1"),
                status(TaskState.TASK_STATE_INPUT_REQUIRED, "need more", "remote-task-1", "remote-ctx-1")),
                new RuntimeException("Request cancelled"));
        A2aRemoteAgentOutboundAdapter adapter = new A2aRemoteAgentOutboundAdapter(id -> transport);

        List<RemoteAgentInvocationService.RemoteAgentResult> results = adapter.invoke(
                new RemoteAgentInvocationService.RemoteAgentRequest(
                        "remote-agent", null, null, "tool-call-1", "parent-task", "parent-ctx",
                        "conversation-1", "hello", Map.of()),
                null);

        assertThat(results).extracting(RemoteAgentInvocationService.RemoteAgentResult::type)
                .containsExactly(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.MESSAGE,
                        RemoteAgentInvocationService.RemoteAgentResult.Type.INPUT_REQUIRED);
    }

    @Test
    void taskSnapshotInputRequiredIsMappedAsTerminalRemoteResult() {
        RecordingTransport transport = new RecordingTransport(List.of(
                agentMessage("working", "remote-task-1", "remote-ctx-1"),
                Task.builder()
                        .id("remote-task-1")
                        .contextId("remote-ctx-1")
                        .status(new TaskStatus(
                                TaskState.TASK_STATE_INPUT_REQUIRED,
                                agentMessage("need more", "remote-task-1", "remote-ctx-1"),
                                null))
                        .metadata(Map.of("remote", "metadata"))
                        .build()));
        A2aRemoteAgentOutboundAdapter adapter = new A2aRemoteAgentOutboundAdapter(id -> transport);

        List<RemoteAgentInvocationService.RemoteAgentResult> results = adapter.invoke(
                new RemoteAgentInvocationService.RemoteAgentRequest(
                        "remote-agent", null, null, "tool-call-1", "parent-task", "parent-ctx",
                        "conversation-1", "hello", Map.of()),
                null);

        assertThat(results).extracting(RemoteAgentInvocationService.RemoteAgentResult::type)
                .containsExactly(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.MESSAGE,
                        RemoteAgentInvocationService.RemoteAgentResult.Type.INPUT_REQUIRED);
        assertThat(results.get(1).remoteTaskId()).isEqualTo("remote-task-1");
        assertThat(results.get(1).remoteContextId()).isEqualTo("remote-ctx-1");
        assertThat(results.get(1).text()).isEqualTo("need more");
        assertThat(results.get(1).metadata()).containsEntry("remote", "metadata");
    }

    @Test
    void inputRequiredReturnsWithoutWaitingForStreamCompletionCallback() {
        RecordingTransport transport = new RecordingTransport(List.of(
                agentMessage("working", "remote-task-1", "remote-ctx-1"),
                status(TaskState.TASK_STATE_INPUT_REQUIRED, "need more", "remote-task-1", "remote-ctx-1")),
                null,
                null,
                false);
        A2aRemoteAgentOutboundAdapter adapter = new A2aRemoteAgentOutboundAdapter(id -> transport);

        List<RemoteAgentInvocationService.RemoteAgentResult> results = adapter.invoke(
                new RemoteAgentInvocationService.RemoteAgentRequest(
                        "remote-agent", null, null, "tool-call-1", "parent-task", "parent-ctx",
                        "conversation-1", "hello", Map.of()),
                null);

        assertThat(results).extracting(RemoteAgentInvocationService.RemoteAgentResult::type)
                .containsExactly(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.MESSAGE,
                        RemoteAgentInvocationService.RemoteAgentResult.Type.INPUT_REQUIRED);
    }

    @Test
    void inputRequiredFollowedByThrownStreamCancellationIsNotMappedAsFailure() {
        RecordingTransport transport = new RecordingTransport(List.of(
                agentMessage("working", "remote-task-1", "remote-ctx-1"),
                status(TaskState.TASK_STATE_INPUT_REQUIRED, "need more", "remote-task-1", "remote-ctx-1")),
                null,
                new RuntimeException("Request cancelled"));
        A2aRemoteAgentOutboundAdapter adapter = new A2aRemoteAgentOutboundAdapter(id -> transport);

        List<RemoteAgentInvocationService.RemoteAgentResult> results = adapter.invoke(
                new RemoteAgentInvocationService.RemoteAgentRequest(
                        "remote-agent", null, null, "tool-call-1", "parent-task", "parent-ctx",
                        "conversation-1", "hello", Map.of()),
                null);

        assertThat(results).extracting(RemoteAgentInvocationService.RemoteAgentResult::type)
                .containsExactly(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.MESSAGE,
                        RemoteAgentInvocationService.RemoteAgentResult.Type.INPUT_REQUIRED);
    }

    private static Message agentMessage(String text, String taskId, String contextId) {
        return Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .taskId(taskId)
                .contextId(contextId)
                .parts(List.<Part<?>>of(new TextPart(text)))
                .build();
    }

    private static TaskArtifactUpdateEvent artifact(String text, String taskId, String contextId) {
        return TaskArtifactUpdateEvent.builder()
                .taskId(taskId)
                .contextId(contextId)
                .artifact(Artifact.builder().artifactId("artifact-1").parts(List.<Part<?>>of(new TextPart(text))).build())
                .build();
    }

    private static TaskStatusUpdateEvent status(TaskState state, String text, String taskId, String contextId) {
        return TaskStatusUpdateEvent.builder()
                .taskId(taskId)
                .contextId(contextId)
                .status(new TaskStatus(state, agentMessage(text, taskId, contextId), null))
                .build();
    }

    private static String text(Message message) {
        return message.parts().stream()
                .filter(TextPart.class::isInstance)
                .map(TextPart.class::cast)
                .map(TextPart::text)
                .reduce("", String::concat);
    }

    private static final class RecordingTransport implements ClientTransport {
        private final List<StreamingEventKind> events;
        private final Throwable terminalError;
        private final RuntimeException thrownError;
        private final boolean signalCompletion;
        private final List<MessageSendParams> requests = new ArrayList<>();

        private RecordingTransport(List<StreamingEventKind> events) {
            this(events, null);
        }

        private RecordingTransport(List<StreamingEventKind> events, Throwable terminalError) {
            this(events, terminalError, null);
        }

        private RecordingTransport(List<StreamingEventKind> events, Throwable terminalError, RuntimeException thrownError) {
            this(events, terminalError, thrownError, true);
        }

        private RecordingTransport(List<StreamingEventKind> events, Throwable terminalError, RuntimeException thrownError,
                boolean signalCompletion) {
            this.events = events;
            this.terminalError = terminalError;
            this.thrownError = thrownError;
            this.signalCompletion = signalCompletion;
        }

        @Override
        public EventKind sendMessage(MessageSendParams request, ClientCallContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void sendMessageStreaming(MessageSendParams request, Consumer<StreamingEventKind> eventConsumer,
                Consumer<Throwable> errorConsumer, ClientCallContext context) {
            requests.add(request);
            events.forEach(eventConsumer);
            if (thrownError != null) {
                throw thrownError;
            }
            if (signalCompletion) {
                errorConsumer.accept(terminalError);
            }
        }

        @Override
        public Task getTask(TaskQueryParams request, ClientCallContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Task cancelTask(CancelTaskParams request, ClientCallContext context) {
            return null;
        }

        @Override
        public ListTasksResult listTasks(ListTasksParams request, ClientCallContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskPushNotificationConfig createTaskPushNotificationConfiguration(TaskPushNotificationConfig request,
                ClientCallContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskPushNotificationConfig getTaskPushNotificationConfiguration(GetTaskPushNotificationConfigParams request,
                ClientCallContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ListTaskPushNotificationConfigsResult listTaskPushNotificationConfigurations(
                ListTaskPushNotificationConfigsParams request, ClientCallContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteTaskPushNotificationConfigurations(DeleteTaskPushNotificationConfigParams request,
                ClientCallContext context) {
        }

        @Override
        public void subscribeToTask(TaskIdParams request, Consumer<StreamingEventKind> eventConsumer,
                Consumer<Throwable> errorConsumer, ClientCallContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentCard getExtendedAgentCard(GetExtendedAgentCardParams params, ClientCallContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
        }
    }
}
