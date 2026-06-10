package com.huawei.ascend.examples.a2a.returnmodes;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = ReturnModesA2aRuntimeApplication.class)
class ReturnModesA2aE2eTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private PushNotificationInbox inbox;

    @Test
    void verifiesSynchronousStreamingAndPushNotificationReturnModes() throws Exception {
        ReturnModesA2aClient client = new ReturnModesA2aClient(baseUri(), TIMEOUT);

        AgentCard card = client.agentCard();
        assertThat(card.capabilities().streaming()).isTrue();
        assertThat(card.capabilities().pushNotifications()).isTrue();

        EventKind syncResult = client.sendMessage("sync");
        assertThat(syncResult).isInstanceOf(Task.class);
        Task syncTask = (Task) syncResult;
        assertThat(syncTask.status().state()).isEqualTo(TaskState.TASK_STATE_COMPLETED);
        assertThat(ReturnModesA2aClient.textFrom(syncResult)).contains("sync-pong");

        List<StreamingEventKind> streamEvents = client.streamMessage("stream");
        assertThat(streamEvents).isNotEmpty();
        assertThat(ReturnModesA2aClient.textFrom(streamEvents))
                .contains("stream-part-1")
                .contains("stream-part-2")
                .contains("stream-done");

        // The client must be notified of task start: collect lifecycle states seen on the stream
        // from both Task snapshots and status-update events. SUBMITTED then WORKING must both appear.
        List<TaskState> streamStates = new java.util.ArrayList<>();
        for (StreamingEventKind e : streamEvents) {
            if (e instanceof TaskStatusUpdateEvent s && s.status() != null) {
                streamStates.add(s.status().state());
            } else if (e instanceof Task t && t.status() != null) {
                streamStates.add(t.status().state());
            }
        }
        assertThat(streamStates).contains(TaskState.TASK_STATE_SUBMITTED, TaskState.TASK_STATE_WORKING);

        inbox.reset();
        String taskId = syncTask.id();
        String callbackUrl = baseUri() + "/test/push-notifications";
        TaskPushNotificationConfig created = client.createPushConfig(taskId, callbackUrl);
        assertThat(created.taskId()).isEqualTo(taskId);
        assertThat(created.url()).isEqualTo(callbackUrl);
        assertThat(client.getPushConfig(taskId, created.id()).url()).isEqualTo(callbackUrl);
        ListTaskPushNotificationConfigsResult listed = client.listPushConfigs(taskId);
        assertThat(listed.configs()).extracting(TaskPushNotificationConfig::id).contains(created.id());

        inbox.reset();
        List<StreamingEventKind> pushStreamEvents = client.streamMessageWithPushConfig("stream", callbackUrl);
        assertThat(pushStreamEvents).isNotEmpty();

        assertThat(inbox.awaitPayload(payload -> payload.contains("stream-part-1"), TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .isTrue();
        assertThat(inbox.payloads()).anySatisfy(payload -> assertThat(payload).contains("stream-part-1"));
    }

    /** A turn whose agent throws must return a FAILED task carrying a structured, client-readable error. */
    @Test
    void failingTurnReturnsFailedTaskWithStructuredError() throws Exception {
        ReturnModesA2aClient client = new ReturnModesA2aClient(baseUri(), TIMEOUT);

        EventKind result = client.sendMessage("fail");

        assertThat(result).isInstanceOf(Task.class);
        Task task = (Task) result;
        assertThat(task.status().state()).isEqualTo(TaskState.TASK_STATE_FAILED);
        // Human-readable reason reaches the client...
        assertThat(ReturnModesA2aClient.textFrom(result)).contains("INVALID_INPUT");
        // ...and a machine-readable structured error (code + retryable) survives the A2A round-trip.
        String structured = String.valueOf(structuredErrorData(task));
        assertThat(structured).contains("INVALID_INPUT").contains("retryable");
    }

    private static Object structuredErrorData(Task task) {
        return task.status().message().parts().stream()
                .filter(DataPart.class::isInstance)
                .map(part -> ((DataPart) part).data())
                .findFirst()
                .orElseThrow(() -> new AssertionError("FAILED task carried no structured error DataPart"));
    }

    private URI baseUri() {
        return URI.create("http://localhost:" + port);
    }
}
