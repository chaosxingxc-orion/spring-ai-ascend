package com.huawei.ascend.examples.workmate.a2a;

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
import org.a2aproject.sdk.client.http.A2ACardResolver;

/**
 * Outbound A2A client for W21 member-runtime fan-out (adapted from ascend SampleA2aClient).
 */
public final class A2aMemberClient {

    private static final java.util.Set<String> TERMINAL_RUN_STATUSES =
            java.util.Set.of("completed", "failed", "canceled", "rejected", "cancelled");

    private final URI baseUri;
    private final Duration timeout;

    public A2aMemberClient(URI baseUri, Duration timeout) {
        this.baseUri = baseUri;
        this.timeout = timeout;
    }

    public AgentCard agentCard() throws Exception {
        return A2ACardResolver.builder().baseUrl(baseUri.toString()).build().getAgentCard();
    }

    public String sendMessage(String sessionId, String taskId, String expertId, String text) throws Exception {
        return sendMessage(sessionId, taskId, expertId, text, false);
    }

    public String sendCloudMessage(String sessionId, String taskId, String expertId, String text) throws Exception {
        return sendMessage(sessionId, taskId, expertId, text, true);
    }

    private String sendMessage(
            String sessionId, String taskId, String expertId, String text, boolean cloudRuntime) throws Exception {
        AgentCard card = agentCard();
        List<StreamingEventKind> events = streamMessage(card, sessionId, taskId, expertId, text, cloudRuntime);
        return textFrom(events);
    }

    private List<StreamingEventKind> streamMessage(
            AgentCard card, String sessionId, String taskId, String expertId, String text) throws Exception {
        return streamMessage(card, sessionId, taskId, expertId, text, false);
    }

    private List<StreamingEventKind> streamMessage(
            AgentCard card, String sessionId, String taskId, String expertId, String text, boolean cloudRuntime)
            throws Exception {
        List<StreamingEventKind> events = new ArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean sawTerminal = new AtomicBoolean(false);
        JSONRPCTransport transport = new JSONRPCTransport(card);
        try {
            transport.sendMessageStreaming(
                    messageSendParams(sessionId, taskId, expertId, text, cloudRuntime),
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
                throw new IllegalStateException("A2A member stream did not complete before timeout");
            }
        } finally {
            transport.close();
        }
        if (failure.get() != null) {
            throw new IllegalStateException("A2A member stream failed", failure.get());
        }
        return List.copyOf(events);
    }

    private MessageSendParams messageSendParams(String sessionId, String taskId, String expertId, String text) {
        return messageSendParams(sessionId, taskId, expertId, text, false);
    }

    private MessageSendParams messageSendParams(
            String sessionId, String taskId, String expertId, String text, boolean cloudRuntime) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put("taskId", taskId);
        metadata.put("expertId", expertId);
        if (cloudRuntime) {
            metadata.put("workmate.cloudRuntime", "true");
        } else {
            metadata.put("workmate.memberRuntime", "true");
        }
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(UUID.randomUUID().toString())
                .contextId(sessionId)
                .metadata(metadata)
                .parts(List.of(new TextPart(text)))
                .build();
        return MessageSendParams.builder().message(message).build();
    }

    static String textFrom(List<StreamingEventKind> events) {
        StringBuilder finalText = new StringBuilder();
        StringBuilder streamingText = new StringBuilder();
        for (StreamingEventKind event : events) {
            if (event instanceof Message message) {
                if (message.metadata() == null || !Boolean.TRUE.equals(message.metadata().get("accepted"))) {
                    String partText = textFromParts(message.parts());
                    if (isTerminal(message)) {
                        finalText.append(partText);
                    } else {
                        streamingText.append(partText);
                    }
                }
            } else if (event instanceof TaskStatusUpdateEvent statusEvent
                    && statusEvent.status() != null
                    && statusEvent.status().message() != null) {
                String partText = textFromParts(statusEvent.status().message().parts());
                if (isTerminal(statusEvent)) {
                    finalText.append(partText);
                } else {
                    streamingText.append(partText);
                }
            } else if (event instanceof TaskArtifactUpdateEvent artifactEvent
                    && artifactEvent.artifact() != null) {
                streamingText.append(textFromParts(artifactEvent.artifact().parts()));
            }
        }
        return !finalText.isEmpty() ? finalText.toString() : streamingText.toString();
    }

    private static String textFromParts(List<Part<?>> parts) {
        StringBuilder result = new StringBuilder();
        for (Part<?> part : parts) {
            if (part instanceof TextPart textPart && !textPart.text().isBlank()) {
                result.append(textPart.text());
            }
        }
        return result.toString();
    }

    private static boolean isTerminal(StreamingEventKind event) {
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

    private static boolean isFailureError(Throwable error, boolean sawTerminal) {
        if (causedByCancellation(error) && sawTerminal) {
            return false;
        }
        return true;
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
