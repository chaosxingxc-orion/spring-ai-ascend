/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.agentscope.trip.a2a.tool;

import com.huawei.ascend.examples.agentscope.trip.tool.HotelPlannerClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A2A JSON-RPC binding for {@link HotelPlannerClient}. The trip agent calls this
 * once per ReAct tool invocation; one blocking call sends the natural-language
 * request to the downstream hotel agent and returns the concatenated assistant
 * text from the terminal events.
 *
 * <p>The {@link JSONRPCTransport} and the resolved {@link AgentCard} are lazily
 * initialised on the first call so booting this app before the hotel app is up
 * does not crash startup. The transport (and its underlying HTTP client) is
 * shared across calls — JSONRPCTransport instances are thread-safe.
 */
public final class A2aHotelPlannerClient implements HotelPlannerClient, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(A2aHotelPlannerClient.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private final String baseUrl;
    private final Duration timeout;

    private volatile JSONRPCTransport transport;

    public A2aHotelPlannerClient(String baseUrl) {
        this(baseUrl, DEFAULT_TIMEOUT);
    }

    public A2aHotelPlannerClient(String baseUrl, Duration timeout) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public String plan(String naturalLanguageRequest) {
        JSONRPCTransport client = resolveTransport();
        StringBuilder sink = new StringBuilder();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean sawTerminal = new AtomicBoolean(false);
        try {
            client.sendMessageStreaming(
                    toParams(naturalLanguageRequest),
                    event -> {
                        appendText(sink, event);
                        if (isTerminal(event)) {
                            sawTerminal.set(true);
                            completed.countDown();
                        }
                    },
                    error -> {
                        // Cancellation after a terminal event is the SDK's normal way to close
                        // the stream — only treat real failures as failures.
                        if (error != null && !(causedByCancellation(error) && sawTerminal.get())) {
                            failure.set(error);
                        }
                        completed.countDown();
                    },
                    new ClientCallContext(Map.of(), Map.of()));
            if (!completed.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(
                        "remote hotel A2A call timed out after " + timeout.toSeconds() + "s");
            }
            if (failure.get() != null) {
                throw new IllegalStateException(
                        "remote hotel A2A call failed: " + rootMessage(failure.get()), failure.get());
            }
            return sink.toString();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while awaiting remote hotel A2A response", ie);
        }
    }

    private JSONRPCTransport resolveTransport() {
        JSONRPCTransport snapshot = transport;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (this) {
            if (transport == null) {
                AgentCard card;
                try {
                    card = A2ACardResolver.builder().baseUrl(baseUrl).build().getAgentCard();
                } catch (Exception ex) {
                    throw new IllegalStateException(
                            "failed to resolve hotel agent card from " + baseUrl + ": " + rootMessage(ex), ex);
                }
                transport = new JSONRPCTransport(card);
                LOG.info("trip agentscope hotel A2A transport ready baseUrl={} cardName={}", baseUrl, card.name());
            }
            return transport;
        }
    }

    @Override
    public void close() {
        JSONRPCTransport snapshot = transport;
        if (snapshot != null) {
            try {
                snapshot.close();
            } catch (RuntimeException ignored) {
                // Best-effort: transport may already be shut down by Spring lifecycle.
            }
        }
    }

    private static MessageSendParams toParams(String text) {
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(UUID.randomUUID().toString())
                .parts(List.<Part<?>>of(new TextPart(text)))
                .build();
        return MessageSendParams.builder().message(message).build();
    }

    private static void appendText(StringBuilder sink, StreamingEventKind event) {
        if (event instanceof Message message) {
            // Skip the runtime's "accepted" acknowledgement message — it carries no agent text.
            if (message.metadata() == null || !Boolean.TRUE.equals(message.metadata().get("accepted"))) {
                appendParts(sink, message.parts());
            }
        } else if (event instanceof TaskStatusUpdateEvent statusEvent
                && statusEvent.status() != null
                && statusEvent.status().message() != null) {
            appendParts(sink, statusEvent.status().message().parts());
        } else if (event instanceof TaskArtifactUpdateEvent artifactEvent) {
            Artifact artifact = artifactEvent.artifact();
            if (artifact != null) {
                appendParts(sink, artifact.parts());
            }
        }
    }

    private static void appendParts(StringBuilder sink, List<Part<?>> parts) {
        if (parts == null) {
            return;
        }
        for (Part<?> part : parts) {
            if (part instanceof TextPart textPart && textPart.text() != null && !textPart.text().isBlank()) {
                sink.append(textPart.text());
            }
        }
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

    private static final List<String> TERMINAL_RUN_STATUSES =
            List.of("completed", "failed", "canceled", "rejected", "cancelled");

    private static boolean causedByCancellation(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof CancellationException) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable error) {
        StringBuilder out = new StringBuilder();
        Throwable cur = error;
        while (cur != null) {
            String m = cur.getMessage();
            if (m != null && !m.isBlank()) {
                if (!out.isEmpty()) {
                    out.append(": ");
                }
                out.append(m);
            }
            cur = cur.getCause();
        }
        return out.isEmpty() ? error.getClass().getName() : out.toString();
    }
}