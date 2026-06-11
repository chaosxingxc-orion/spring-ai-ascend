package com.huawei.ascend.examples.a2a.remoteopenjiuwen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class RemoteOpenJiuwenA2aE2eTest {

    @Test
    void agentADefaultsToDeterministicModeAndCanSwitchToLlmMode() {
        try (ConfigurableApplicationContext deterministic = startRuntime("a", List.of())) {
            OpenJiuwenAgentRuntimeHandler handler = deterministic.getBean(OpenJiuwenAgentRuntimeHandler.class);
            assertThat(handler.getClass().getName()).contains("AgentAConfiguration");
        }

        try (ConfigurableApplicationContext llm = startRuntime("a", List.of(
                "sample.remote-openjiuwen.agent-a.mode=llm",
                "sample.remote-openjiuwen.agent-a.llm.api-key=test-key",
                "sample.remote-openjiuwen.agent-a.llm.api-base=http://localhost:1/v1",
                "sample.remote-openjiuwen.agent-a.llm.model-name=test-model"))) {
            OpenJiuwenAgentRuntimeHandler handler = llm.getBean(OpenJiuwenAgentRuntimeHandler.class);
            assertThat(handler.getClass().getName()).contains("AgentALlmConfiguration");
        }
    }

    @Test
    void agentALlmModeRequiresExternalModelCredentials() {
        assertThatThrownBy(() -> startRuntime("a", List.of(
                "sample.remote-openjiuwen.agent-a.mode=llm",
                "sample.remote-openjiuwen.agent-a.llm.api-key=",
                "sample.remote-openjiuwen.agent-a.llm.api-base=")))
                .hasRootCauseMessage("SAA_REMOTE_OPENJIUWEN_LLM_API_KEY must be set "
                        + "when sample.remote-openjiuwen.agent-a.mode=llm");
    }

    @Test
    void localAgentInvokesRemoteAgentWithInputRequiredAndResume() throws Exception {
        try (ConfigurableApplicationContext agentB = startRuntime("b", List.of())) {
            int agentBPort = port(agentB);
            try (ConfigurableApplicationContext agentA = startRuntime("a", List.of(
                    "agent-runtime.remote-agents[0].url=http://localhost:" + agentBPort))) {
                A2aTestClient client = new A2aTestClient(
                        URI.create("http://localhost:" + port(agentA)), Duration.ofSeconds(20));

                List<StreamingEventKind> first = client.streamMessage(
                        "manual-user",
                        AgentAConfiguration.AGENT_ID,
                        "ctx-remote-e2e",
                        null,
                        "call remote b",
                        TaskState.TASK_STATE_INPUT_REQUIRED);

                assertThat(A2aTestClient.hasState(first, TaskState.TASK_STATE_INPUT_REQUIRED)).isTrue();
                String parentTaskId = A2aTestClient.firstTaskIdWithState(first, TaskState.TASK_STATE_INPUT_REQUIRED);
                assertThat(parentTaskId).isNotBlank();
                TaskSnapshot firstTask = client.getTask(parentTaskId);
                assertThat(firstTask.state()).isEqualTo(TaskState.TASK_STATE_INPUT_REQUIRED.name());
                assertThat(firstTask.text()).contains("AgentB needs one more user input");

                client.streamMessage(
                        "manual-user",
                        AgentAConfiguration.AGENT_ID,
                        "ctx-remote-e2e",
                        parentTaskId,
                        "please continue",
                        null);

                TaskSnapshot completedTask = client.awaitTaskState(parentTaskId, TaskState.TASK_STATE_COMPLETED);
                assertThat(completedTask.text())
                        .contains("AgentA resumed from remote tool result")
                        .contains("AgentB completed after the second user input");
                assertThat(completedTask.state()).isEqualTo(TaskState.TASK_STATE_COMPLETED.name());
            }
        }
    }

    @Test
    void manualLlmAgentInvokesRemoteAgentWithInputRequiredAndResume() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("SAA_REMOTE_OPENJIUWEN_RUN_LLM_E2E")),
                "Set SAA_REMOTE_OPENJIUWEN_RUN_LLM_E2E=true to run the real LLM manual E2E test");
        try (ConfigurableApplicationContext agentB = startRuntime("b", List.of())) {
            int agentBPort = port(agentB);
            try (ConfigurableApplicationContext agentA = startRuntime("a", List.of(
                    "sample.remote-openjiuwen.agent-a.mode=llm",
                    "agent-runtime.remote-agents[0].url=http://localhost:" + agentBPort))) {
                A2aTestClient client = new A2aTestClient(
                        URI.create("http://localhost:" + port(agentA)), Duration.ofSeconds(90));

                List<StreamingEventKind> first = client.streamMessage(
                        "manual-user",
                        AgentAConfiguration.AGENT_ID,
                        "ctx-remote-e2e-llm",
                        null,
                        "Please call remote AgentB to run the streaming input-required demo.",
                        TaskState.TASK_STATE_INPUT_REQUIRED);

                String parentTaskId = A2aTestClient.firstTaskIdWithState(first, TaskState.TASK_STATE_INPUT_REQUIRED);
                assertThat(parentTaskId).isNotBlank();
                TaskSnapshot firstTask = client.getTask(parentTaskId);
                assertThat(firstTask.state()).isEqualTo(TaskState.TASK_STATE_INPUT_REQUIRED.name());
                assertThat(firstTask.text()).contains("AgentB needs one more user input");

                List<StreamingEventKind> second = client.streamMessage(
                        "manual-user",
                        AgentAConfiguration.AGENT_ID,
                        "ctx-remote-e2e-llm",
                        parentTaskId,
                        "follow up from user",
                        null);
                assertThat(A2aTestClient.textFrom(second)).contains("AgentB second stream message");

                TaskSnapshot completedTask = client.awaitTaskState(parentTaskId, TaskState.TASK_STATE_COMPLETED);
                assertThat(completedTask.statusText()).isNotBlank();
                assertThat(completedTask.state()).isEqualTo(TaskState.TASK_STATE_COMPLETED.name());
            }
        }
    }

    private static ConfigurableApplicationContext startRuntime(String role, List<String> extraProperties) {
        List<String> args = new ArrayList<>();
        args.add("--server.port=0");
        args.add("--sample.remote-openjiuwen.role=" + role);
        extraProperties.stream()
                .map(property -> property.startsWith("--") ? property : "--" + property)
                .forEach(args::add);
        return new SpringApplicationBuilder(RemoteOpenJiuwenA2aApplication.class)
                .run(args.toArray(String[]::new));
    }

    private static int port(ConfigurableApplicationContext context) {
        Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
        if (port == null) {
            throw new IllegalStateException("local.server.port is not available");
        }
        return port;
    }

    private static final class A2aTestClient {
        private static final java.util.Set<TaskState> FINAL_STATES = java.util.Set.of(
                TaskState.TASK_STATE_COMPLETED,
                TaskState.TASK_STATE_FAILED,
                TaskState.TASK_STATE_CANCELED,
                TaskState.TASK_STATE_REJECTED);

        private final URI baseUri;
        private final Duration timeout;
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final HttpClient httpClient = HttpClient.newHttpClient();

        private A2aTestClient(URI baseUri, Duration timeout) {
            this.baseUri = baseUri;
            this.timeout = timeout;
        }

        private List<StreamingEventKind> streamMessage(String userId, String agentId, String contextId,
                String taskId, String text, TaskState expectedState) throws Exception {
            AgentCard card = new A2ACardResolver(baseUri.toString()).getAgentCard();
            List<StreamingEventKind> events = new ArrayList<>();
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean sawExpectedEnd = new AtomicBoolean();
            JSONRPCTransport transport = new JSONRPCTransport(card);
            try {
                transport.sendMessageStreaming(
                        messageSendParams(userId, agentId, contextId, taskId, text),
                        event -> {
                            events.add(event);
                            // Stop on a stable status: the requested non-final state (e.g. input-required)
                            // or any terminal state. Intermediate remote progress is best-effort per the
                            // design contract, so it must never gate the stop condition.
                            boolean reached = expectedState != null
                                    ? hasState(events, expectedState)
                                    : hasFinalState(events);
                            if (reached) {
                                sawExpectedEnd.set(true);
                                completed.countDown();
                            }
                        },
                        error -> {
                            if (!causedByCancellation(error)) {
                                failure.set(error);
                            }
                            completed.countDown();
                        },
                        new ClientCallContext(Map.of(), Map.of()));
                if (!completed.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("A2A stream did not reach expected state before timeout");
                }
            } finally {
                transport.close();
            }
            if (failure.get() != null) {
                throw new IllegalStateException("A2A stream failed", failure.get());
            }
            if (!sawExpectedEnd.get()) {
                throw new IllegalStateException("A2A stream ended before expected state, events="
                        + events.size() + ", text=" + textFrom(events));
            }
            return List.copyOf(events);
        }

        private TaskSnapshot getTask(String taskId) throws Exception {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "jsonrpc", "2.0",
                    "id", "get-task-" + UUID.randomUUID(),
                    "method", "GetTask",
                    "params", Map.of("id", taskId)));
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/a2a"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("GetTask failed with HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.hasNonNull("error")) {
                throw new IllegalStateException("GetTask failed: " + root.get("error"));
            }
            JsonNode task = root.path("result").has("task") ? root.path("result").path("task") : root.path("result");
            String state = task.path("status").path("state").asText("");
            StringBuilder statusText = new StringBuilder();
            appendTextFields(task.path("status").path("message"), statusText);
            StringBuilder text = new StringBuilder();
            appendTextFields(task, text);
            return new TaskSnapshot(state, statusText.toString(), text.toString());
        }

        private TaskSnapshot awaitTaskState(String taskId, TaskState expectedState) throws Exception {
            long deadline = System.nanoTime() + timeout.toNanos();
            TaskSnapshot last = null;
            while (System.nanoTime() < deadline) {
                last = getTask(taskId);
                if (expectedState.name().equals(last.state())) {
                    return last;
                }
                Thread.sleep(100L);
            }
            String actual = last == null ? null : last.state();
            throw new IllegalStateException(
                    "Task " + taskId + " did not reach " + expectedState + " before timeout, actual=" + actual);
        }

        private static void appendTextFields(JsonNode node, StringBuilder text) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return;
            }
            if (node.isObject()) {
                JsonNode textNode = node.get("text");
                if (textNode != null && textNode.isTextual()) {
                    text.append(textNode.asText());
                }
                Iterator<JsonNode> fields = node.elements();
                while (fields.hasNext()) {
                    appendTextFields(fields.next(), text);
                }
            } else if (node.isArray()) {
                node.forEach(child -> appendTextFields(child, text));
            }
        }

        private MessageSendParams messageSendParams(String userId, String agentId, String contextId,
                String taskId, String text) {
            Message.Builder message = Message.builder()
                    .role(Message.Role.ROLE_USER)
                    .messageId(UUID.randomUUID().toString())
                    .contextId(contextId)
                    .metadata(Map.of(
                            "userId", userId,
                            "agentId", agentId,
                            "sessionId", contextId))
                    .parts(List.<Part<?>>of(new TextPart(text)));
            if (taskId != null && !taskId.isBlank()) {
                message.taskId(taskId);
            }
            return MessageSendParams.builder().message(message.build()).build();
        }

        private static boolean hasState(List<StreamingEventKind> events, TaskState expected) {
            return events.stream().anyMatch(event -> event instanceof TaskStatusUpdateEvent statusEvent
                    && statusEvent.status() != null
                    && statusEvent.status().state() == expected
                    || event instanceof Task task
                    && task.status() != null
                    && task.status().state() == expected);
        }

        private static String firstTaskIdWithState(List<StreamingEventKind> events, TaskState expected) {
            for (StreamingEventKind event : events) {
                if (event instanceof TaskStatusUpdateEvent statusEvent
                        && statusEvent.status() != null
                        && statusEvent.status().state() == expected
                        && statusEvent.taskId() != null
                        && !statusEvent.taskId().isBlank()) {
                    return statusEvent.taskId();
                }
                if (event instanceof Task task
                        && task.status() != null
                        && task.status().state() == expected
                        && task.id() != null
                        && !task.id().isBlank()) {
                    return task.id();
                }
            }
            return "";
        }

        private static boolean hasFinalState(List<StreamingEventKind> events) {
            return FINAL_STATES.stream().anyMatch(state -> hasState(events, state));
        }

        private static String textFrom(List<StreamingEventKind> events) {
            StringBuilder result = new StringBuilder();
            for (StreamingEventKind event : events) {
                if (event instanceof Message message) {
                    result.append(textFromParts(message.parts()));
                } else if (event instanceof TaskStatusUpdateEvent statusEvent
                        && statusEvent.status() != null
                        && statusEvent.status().message() != null) {
                    result.append(textFromParts(statusEvent.status().message().parts()));
                } else if (event instanceof TaskArtifactUpdateEvent artifactEvent
                        && artifactEvent.artifact() != null) {
                    result.append(textFromParts(artifactEvent.artifact().parts()));
                } else if (event instanceof Task task) {
                    if (task.status() != null && task.status().message() != null) {
                        result.append(textFromParts(task.status().message().parts()));
                    }
                    if (task.artifacts() != null) {
                        task.artifacts().stream()
                                .filter(artifact -> artifact != null && artifact.parts() != null)
                                .forEach(artifact -> result.append(textFromParts(artifact.parts())));
                    }
                }
            }
            return result.toString();
        }

        private static String textFromParts(List<Part<?>> parts) {
            if (parts == null) {
                return "";
            }
            StringBuilder result = new StringBuilder();
            for (Part<?> part : parts) {
                if (part instanceof TextPart textPart) {
                    result.append(textPart.text());
                }
            }
            return result.toString();
        }

        private static boolean causedByCancellation(Throwable error) {
            for (Throwable cursor = error; cursor != null; cursor = cursor.getCause()) {
                if (cursor instanceof java.util.concurrent.CancellationException) {
                    return true;
                }
            }
            return false;
        }
    }

    private record TaskSnapshot(String state, String statusText, String text) {
    }
}
