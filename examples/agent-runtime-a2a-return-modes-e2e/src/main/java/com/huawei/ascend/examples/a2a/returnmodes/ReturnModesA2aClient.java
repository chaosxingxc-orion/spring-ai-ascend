package com.huawei.ascend.examples.a2a.returnmodes;

import java.net.URI;
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
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;

public final class ReturnModesA2aClient {
    private final URI baseUri;
    private final Duration timeout;

    public ReturnModesA2aClient(URI baseUri, Duration timeout) {
        this.baseUri = baseUri;
        this.timeout = timeout;
    }

    public AgentCard agentCard() throws Exception {
        return new A2ACardResolver(baseUri.toString()).getAgentCard();
    }

    public EventKind sendMessage(String text) throws Exception {
        return sendMessage(messageSendParams(text));
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
        if (failure.get() != null && events.stream().noneMatch(ReturnModesA2aClient::isTerminal)) {
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
        return events.stream().anyMatch(ReturnModesA2aClient::isTerminal);
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
                        "userId", "return-modes-user",
                        "agentId", ReturnModesAgentConfiguration.AGENT_ID))
                .parts(List.of(new TextPart(text)))
                .build();
        return MessageSendParams.builder().message(message).configuration(configuration).build();
    }

    private static ClientCallContext context() {
        return new ClientCallContext(Map.of(), Map.of());
    }
}
