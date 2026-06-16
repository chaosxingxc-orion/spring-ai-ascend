package com.huawei.ascend.examples.a2a.externalaccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendConfiguration;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;

public final class A2aExternalAccessClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final URI baseUri;
    private final Duration timeout;
    private final HttpClient httpClient;

    public A2aExternalAccessClient(URI baseUri, Duration timeout) {
        this.baseUri = baseUri;
        this.timeout = timeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public AgentCard agentCard() throws Exception {
        return A2ACardResolver.builder().baseUrl(baseUri.toString()).build().getAgentCard();
    }

    public EventKind sendMessage(String text) throws Exception {
        return sendMessage(messageSendParams(text));
    }

    public EventKind sendMessageReturnImmediately(String text) throws Exception {
        MessageSendConfiguration configuration = MessageSendConfiguration.builder()
                .returnImmediately(true)
                .build();
        return sendMessage(messageSendParams(text, configuration));
    }

    private EventKind sendMessage(MessageSendParams params) throws Exception {
        AgentCard card = agentCard();
        JSONRPCTransport transport = new JSONRPCTransport(card);
        try {
            return transport.sendMessage(params, context());
        } finally {
            transport.close();
        }
    }

    public List<StreamingEventKind> streamMessage(String text) throws Exception {
        return streamMessage(messageSendParams(text));
    }

    public List<StreamingEventKind> streamMessageWithPushConfig(String text, String callbackUrl) throws Exception {
        TaskPushNotificationConfig pushConfig = new TaskPushNotificationConfig(
                "push-" + UUID.randomUUID(), null, callbackUrl, null, null, null);
        MessageSendConfiguration configuration = MessageSendConfiguration.builder()
                .taskPushNotificationConfig(pushConfig)
                .build();
        return streamMessage(messageSendParams(text, configuration));
    }

    public JsonNode getTask(String taskId) throws Exception {
        return jsonRpc("GetTask", Map.of("id", taskId));
    }

    public JsonNode cancelTask(String taskId) throws Exception {
        return jsonRpc("CancelTask", Map.of("id", taskId));
    }

    public JsonNode listTasks() throws Exception {
        return jsonRpc("ListTasks", Map.of());
    }

    public List<StreamingEventKind> subscribeToTask(String taskId) throws Exception {
        AgentCard card = agentCard();
        JSONRPCTransport transport = new JSONRPCTransport(card);
        List<StreamingEventKind> events = new ArrayList<>();
        CountDownLatch firstEvent = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            transport.subscribeToTask(
                    new TaskIdParams(taskId),
                    event -> {
                        events.add(event);
                        firstEvent.countDown();
                    },
                    error -> {
                        failure.set(error);
                        firstEvent.countDown();
                    },
                    context());
            if (!firstEvent.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("A2A task subscription did not emit before timeout");
            }
        } finally {
            transport.close();
        }
        if (failure.get() != null && events.isEmpty()) {
            throw new IllegalStateException("A2A task subscription failed", failure.get());
        }
        return List.copyOf(events);
    }

    public JsonNode sendMessageReturnImmediatelyJson(String text) throws Exception {
        return jsonRpc("SendMessage", messageSendRequest(text, Map.of("returnImmediately", true)));
    }

    public List<JsonNode> streamMessageJson(String text) throws Exception {
        return streamingJsonRpc("SendStreamingMessage", messageSendRequest(text, null), true);
    }

    public List<JsonNode> streamMessageWithPushConfigJson(String text, String callbackUrl) throws Exception {
        Map<String, Object> configuration = Map.of("taskPushNotificationConfig",
                Map.of("id", "push-" + UUID.randomUUID(), "url", callbackUrl));
        return streamingJsonRpc("SendStreamingMessage", messageSendRequest(text, configuration), true);
    }

    private List<StreamingEventKind> streamMessage(MessageSendParams params) throws Exception {
        AgentCard card = agentCard();
        JSONRPCTransport transport = new JSONRPCTransport(card);
        List<StreamingEventKind> events = new ArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            transport.sendMessageStreaming(
                    params,
                    event -> {
                        events.add(event);
                        if (isTerminal(event)) {
                            completed.countDown();
                        }
                    },
                    error -> {
                        failure.set(error);
                        completed.countDown();
                    },
                    context());
            if (!completed.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("A2A stream did not complete before timeout");
            }
        } finally {
            transport.close();
        }
        if (failure.get() != null && events.stream().noneMatch(A2aExternalAccessClient::isTerminal)) {
            throw new IllegalStateException("A2A stream failed", failure.get());
        }
        return List.copyOf(events);
    }

    public TaskPushNotificationConfig createPushConfig(String taskId, String callbackUrl) throws Exception {
        AgentCard card = agentCard();
        JSONRPCTransport transport = new JSONRPCTransport(card);
        try {
            return transport.createTaskPushNotificationConfiguration(
                    new TaskPushNotificationConfig("push-" + taskId, taskId, callbackUrl, null, null, null),
                    context());
        } finally {
            transport.close();
        }
    }

    public TaskPushNotificationConfig getPushConfig(String taskId, String id) throws Exception {
        AgentCard card = agentCard();
        JSONRPCTransport transport = new JSONRPCTransport(card);
        try {
            return transport.getTaskPushNotificationConfiguration(
                    new GetTaskPushNotificationConfigParams(taskId, id),
                    context());
        } finally {
            transport.close();
        }
    }

    public ListTaskPushNotificationConfigsResult listPushConfigs(String taskId) throws Exception {
        AgentCard card = agentCard();
        JSONRPCTransport transport = new JSONRPCTransport(card);
        try {
            return transport.listTaskPushNotificationConfigurations(
                    new ListTaskPushNotificationConfigsParams(taskId),
                    context());
        } finally {
            transport.close();
        }
    }

    static String textFrom(EventKind event) {
        if (event instanceof Task task) {
            StringBuilder text = new StringBuilder();
            if (task.history() != null) {
                task.history().forEach(message -> text.append(textFromParts(message.parts())));
            }
            if (task.artifacts() != null) {
                task.artifacts().forEach(artifact -> text.append(textFromParts(artifact.parts())));
            }
            if (task.status() != null && task.status().message() != null) {
                text.append(textFromParts(task.status().message().parts()));
            }
            return text.toString();
        }
        if (event instanceof Message message) {
            return textFromParts(message.parts());
        }
        return "";
    }

    static String textFrom(List<StreamingEventKind> events) {
        StringBuilder text = new StringBuilder();
        for (StreamingEventKind event : events) {
            if (event instanceof Message message) {
                text.append(textFromParts(message.parts()));
            } else if (event instanceof TaskStatusUpdateEvent statusEvent
                    && statusEvent.status() != null
                    && statusEvent.status().message() != null) {
                text.append(textFromParts(statusEvent.status().message().parts()));
            } else if (event instanceof TaskArtifactUpdateEvent artifactEvent
                    && artifactEvent.artifact() != null) {
                text.append(textFromParts(artifactEvent.artifact().parts()));
            }
        }
        return text.toString();
    }

    static boolean hasTerminalEvent(List<StreamingEventKind> events) {
        return events.stream().anyMatch(A2aExternalAccessClient::isTerminal);
    }

    private static boolean isTerminal(StreamingEventKind event) {
        return event instanceof TaskStatusUpdateEvent statusEvent
                && statusEvent.status() != null
                && statusEvent.status().state() != null
                && (statusEvent.status().state() == TaskState.TASK_STATE_COMPLETED
                || statusEvent.status().state() == TaskState.TASK_STATE_FAILED
                || statusEvent.status().state() == TaskState.TASK_STATE_CANCELED
                || statusEvent.status().state() == TaskState.TASK_STATE_REJECTED);
    }

    private static String textFromParts(List<Part<?>> parts) {
        if (parts == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Part<?> part : parts) {
            if (part instanceof TextPart textPart) {
                text.append(textPart.text());
            }
        }
        return text.toString();
    }

    private MessageSendParams messageSendParams(String text) {
        return messageSendParams(text, null);
    }

    private MessageSendParams messageSendParams(String text, MessageSendConfiguration configuration) {
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(UUID.randomUUID().toString())
                .contextId("session-" + UUID.randomUUID())
                .metadata(Map.of(
                        "userId", "external-access-user",
                        "agentId", A2aExternalAccessAgentConfiguration.AGENT_ID))
                .parts(List.of(new TextPart(text)))
                .build();
        return MessageSendParams.builder().message(message).configuration(configuration).build();
    }

    private JsonNode jsonRpc(String method, Object params) throws Exception {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", method + "-" + UUID.randomUUID(),
                "method", method,
                "params", params);
        HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve("/a2a"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(request)))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("A2A JSON-RPC HTTP status " + response.statusCode() + ": " + response.body());
        }
        return JSON.readTree(response.body());
    }

    private List<JsonNode> streamingJsonRpc(String method, Object params, boolean waitForTerminal) throws Exception {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", method + "-" + UUID.randomUUID(),
                "method", method,
                "params", params);
        HttpRequest httpRequest = HttpRequest.newBuilder(baseUri.resolve("/a2a"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(request)))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("A2A SSE HTTP status " + response.statusCode() + ": " + response.body());
        }
        List<JsonNode> events = sseData(response.body());
        if (waitForTerminal && events.stream().noneMatch(A2aExternalAccessClient::isTerminalJson)) {
            throw new IllegalStateException("A2A SSE response did not contain a terminal event: " + response.body());
        }
        if (!waitForTerminal && events.isEmpty()) {
            throw new IllegalStateException("A2A SSE response did not contain any event: " + response.body());
        }
        return events;
    }

    private static List<JsonNode> sseData(String body) throws IOException {
        List<JsonNode> events = new ArrayList<>();
        for (String line : body.split("\\R")) {
            if (line.startsWith("data:")) {
                events.add(JSON.readTree(line.substring("data:".length()).trim()));
            }
        }
        return List.copyOf(events);
    }

    private Map<String, Object> messageSendRequest(String text, Map<String, Object> configuration) {
        Map<String, Object> message = Map.of(
                "role", "ROLE_USER",
                "messageId", UUID.randomUUID().toString(),
                "contextId", "session-" + UUID.randomUUID(),
                "metadata", Map.of(
                        "userId", "external-access-user",
                        "agentId", A2aExternalAccessAgentConfiguration.AGENT_ID),
                "parts", List.of(Map.of("text", text)));
        if (configuration == null) {
            return Map.of("message", message);
        }
        return Map.of("message", message, "configuration", configuration);
    }

    /**
     * Extracts the task id from a GetTask/CancelTask response.
     * A2A 1.0.0 serializes these responses as a naked Task in result.
     */
    static String taskIdFrom(JsonNode root) {
        return root.path("result").path("id").asText();
    }

    /**
     * Extracts the task state from a GetTask/CancelTask response.
     * A2A 1.0.0 serializes these responses as a naked Task in result.
     */
    static String taskStateFrom(JsonNode root) {
        return root.path("result").path("status").path("state").asText();
    }

    static String sendMessageTaskIdFrom(JsonNode root) {
        return root.path("result").path("task").path("id").asText();
    }

    static String sendMessageTaskStateFrom(JsonNode root) {
        return root.path("result").path("task").path("status").path("state").asText();
    }

    static String textFrom(JsonNode root) {
        StringBuilder text = new StringBuilder();
        JsonNode task = root.path("result");
        appendTextParts(task.path("status").path("message").path("parts"), text);
        JsonNode artifacts = task.path("artifacts");
        if (artifacts.isArray()) {
            artifacts.forEach(artifact -> appendTextParts(artifact.path("parts"), text));
        }
        return text.toString();
    }

    static String textFromJsonEvents(List<JsonNode> events) {
        StringBuilder text = new StringBuilder();
        for (JsonNode event : events) {
            JsonNode result = event.path("result");
            appendTextParts(result.path("artifact").path("parts"), text);
            appendTextParts(result.path("status").path("message").path("parts"), text);
            appendTextParts(result.path("message").path("parts"), text);
        }
        return text.toString();
    }

    static List<String> taskIdsFromList(JsonNode root) {
        List<String> ids = new ArrayList<>();
        JsonNode tasks = root.path("result").path("tasks");
        if (tasks.isArray()) {
            tasks.forEach(task -> ids.add(taskIdFromTaskNode(task)));
        }
        return List.copyOf(ids);
    }

    private static String taskIdFromTaskNode(JsonNode task) {
        return task.path("id").asText();
    }

    private static boolean isTerminalJson(JsonNode root) {
        String state = root.path("result").path("status").path("state").asText();
        return "TASK_STATE_COMPLETED".equals(state)
                || "TASK_STATE_FAILED".equals(state)
                || "TASK_STATE_CANCELED".equals(state)
                || "TASK_STATE_REJECTED".equals(state);
    }

    private static void appendTextParts(JsonNode parts, StringBuilder text) {
        if (!parts.isArray()) {
            return;
        }
        parts.forEach(part -> {
            JsonNode value = part.path("text");
            if (value.isTextual()) {
                text.append(value.asText());
            }
        });
    }

    private static ClientCallContext context() {
        return new ClientCallContext(Map.of(), Map.of());
    }
}
