package com.huawei.ascend.examples.a2a.memory;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
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
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;

public final class SampleA2aClient {

    private final URI baseUri;
    private final Duration timeout;

    public SampleA2aClient(URI baseUri, Duration timeout) {
        this.baseUri = baseUri;
        this.timeout = timeout;
    }

    public AgentCard agentCard() throws Exception {
        return A2ACardResolver.builder().baseUrl(baseUri.toString()).build().getAgentCard();
    }

    public List<StreamingEventKind> streamMessage(String userId, String agentId, String sessionId, String text)
            throws Exception {
        AgentCard card = agentCard();
        List<StreamingEventKind> events = new ArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean sawTerminal = new AtomicBoolean(false);
        JSONRPCTransport transport = new JSONRPCTransport(card);
        try {
            transport.sendMessageStreaming(
                    messageSendParams(userId, agentId, sessionId, text),
                    event -> {
                        events.add(event);
                        if (isTerminal(event)) {
                            sawTerminal.set(true);
                            completed.countDown();
                        }
                    },
                    error -> {
                        if (isFailureError(error, sawTerminal.get())) {
                            failure.set(error);
                        }
                        completed.countDown();
                    },
                    new ClientCallContext(Map.of(), Map.of()));
            if (!completed.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("A2A stream did not complete before timeout");
            }
        } finally {
            transport.close();
        }
        if (failure.get() != null) {
            throw new IllegalStateException("A2A stream failed", failure.get());
        }
        return List.copyOf(events);
    }

    public static String textFrom(List<StreamingEventKind> events) {
        StringBuilder result = new StringBuilder();
        for (StreamingEventKind event : events) {
            if (event instanceof Message message) {
                if (message.metadata() == null || !Boolean.TRUE.equals(message.metadata().get("accepted"))) {
                    result.append(textFromParts(message.parts()));
                }
            } else if (event instanceof TaskStatusUpdateEvent statusEvent
                    && statusEvent.status() != null
                    && statusEvent.status().message() != null) {
                result.append(textFromParts(statusEvent.status().message().parts()));
            } else if (event instanceof TaskArtifactUpdateEvent artifactEvent
                    && artifactEvent.artifact() != null) {
                result.append(textFromParts(artifactEvent.artifact().parts()));
            }
        }
        return result.toString();
    }

    static String textFromParts(List<Part<?>> parts) {
        StringBuilder result = new StringBuilder();
        for (Part<?> part : parts) {
            if (part instanceof TextPart textPart && !textPart.text().isBlank()) {
                result.append(textPart.text());
            }
        }
        return result.toString();
    }

    private MessageSendParams messageSendParams(String userId, String agentId, String sessionId, String text) {
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(UUID.randomUUID().toString())
                .contextId(sessionId)
                .metadata(Map.of(
                        "userId", userId,
                        "agentId", agentId,
                        "sessionId", sessionId))
                .parts(List.of(new TextPart(text)))
                .build();
        return MessageSendParams.builder()
                .message(message)
                .build();
    }

    public static boolean isTerminal(StreamingEventKind event) {
        if (event instanceof TaskStatusUpdateEvent statusEvent
                && statusEvent.status() != null
                && statusEvent.status().state() != null) {
            TaskState state = statusEvent.status().state();
            return state == TaskState.TASK_STATE_COMPLETED
                    || state == TaskState.TASK_STATE_FAILED
                    || state == TaskState.TASK_STATE_CANCELED
                    || state == TaskState.TASK_STATE_REJECTED;
        }
        if (event instanceof Message message && message.metadata() != null) {
            return TERMINAL_RUN_STATUSES.contains(String.valueOf(message.metadata().get("runStatus")));
        }
        return false;
    }

    private static final java.util.Set<String> TERMINAL_RUN_STATUSES =
            java.util.Set.of("completed", "failed", "canceled", "rejected", "cancelled");

    static boolean isFailureError(Throwable error, boolean sawTerminal) {
        return !(causedByCancellation(error) && sawTerminal);
    }

    private static boolean causedByCancellation(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof java.util.concurrent.CancellationException) {
                return true;
            }
        }
        return false;
    }
}
