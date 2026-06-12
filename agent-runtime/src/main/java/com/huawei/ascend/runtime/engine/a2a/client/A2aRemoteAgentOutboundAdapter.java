package com.huawei.ascend.runtime.engine.a2a.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A2A JSON-RPC transport adapter that implements {@link RemoteAgentInvocationService.OutboundPort}.
 *
 * <p>One transport (and its underlying HTTP client) is cached per remote agent endpoint
 * and reused across invocations. Each invocation is a blocking streaming call with
 * a configurable timeout. Messages and artifacts received from the remote are forwarded
 * to the caller via the {@code eventConsumer} callback so the framework can project
 * progress onto the parent task without waiting for the remote invocation to complete.
 */
public final class A2aRemoteAgentOutboundAdapter implements RemoteAgentInvocationService.OutboundPort {
    private static final Logger LOG = LoggerFactory.getLogger(A2aRemoteAgentOutboundAdapter.class);
    private static final Duration DEFAULT_STREAM_TIMEOUT = Duration.ofSeconds(60);

    private final Function<String, ClientTransport> transportFactory;
    private final Duration streamTimeout;
    private final Map<String, ClientTransport> transportCache = new ConcurrentHashMap<>();

    public A2aRemoteAgentOutboundAdapter(RemoteAgentCardCache cardCache) {
        this(remoteAgentId -> {
            String endpoint = cardCache.endpoint(remoteAgentId);
            return endpoint == null || endpoint.isBlank() ? null : new JSONRPCTransport(endpoint);
        }, DEFAULT_STREAM_TIMEOUT);
    }

    public A2aRemoteAgentOutboundAdapter(Function<String, ClientTransport> transportFactory) {
        this(transportFactory, DEFAULT_STREAM_TIMEOUT);
    }

    public A2aRemoteAgentOutboundAdapter(Function<String, ClientTransport> transportFactory, Duration streamTimeout) {
        this.transportFactory = Objects.requireNonNull(transportFactory, "transportFactory");
        this.streamTimeout = streamTimeout == null ? DEFAULT_STREAM_TIMEOUT : streamTimeout;
    }

    @Override
    public List<RemoteAgentInvocationService.RemoteAgentResult> invoke(
            RemoteAgentInvocationService.RemoteAgentRequest request,
            Consumer<RemoteAgentInvocationService.RemoteAgentResult> eventConsumer) {
        LOG.info("remote agent invocation start remoteAgentId={} remoteTaskId={} messageLen={}",
                request.remoteAgentId(), request.remoteTaskId(), request.message().length());
        ClientTransport transport = obtainTransport(request.remoteAgentId());
        if (transport == null) {
            LOG.warn("remote agent invocation rejected: no transport for remoteAgentId={}",
                    request.remoteAgentId());
            return List.of(RemoteAgentInvocationService.RemoteAgentResult.failed(
                    "No A2A transport for remote agent " + request.remoteAgentId()));
        }
        List<RemoteAgentInvocationService.RemoteAgentResult> results = new ArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        try {
            transport.sendMessageStreaming(toParams(request),
                    event -> {
                        RemoteAgentInvocationService.RemoteAgentResult result = toResult(event);
                        if (result != null) {
                            results.add(result);
                            if (eventConsumer != null) {
                                eventConsumer.accept(result);
                            }
                            if (isRemoteTerminal(result)) {
                                completed.countDown();
                            }
                        }
                    },
                    throwable -> {
                        if (throwable != null) {
                            error.set(throwable);
                        }
                        completed.countDown();
                    },
                    null);
            if (!completed.await(streamTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return List.of(RemoteAgentInvocationService.RemoteAgentResult.failed("remote A2A stream timed out"));
            }
            if (error.get() != null) {
                if (hasRemoteTerminal(results) && causedByCancellation(error.get())) {
                    return List.copyOf(results);
                }
                return List.of(RemoteAgentInvocationService.RemoteAgentResult.failed(error.get().getMessage()));
            }
            return List.copyOf(results);
        } catch (Exception ex) {
            if (hasRemoteTerminal(results) && causedByCancellation(ex)) {
                return List.copyOf(results);
            }
            return List.of(RemoteAgentInvocationService.RemoteAgentResult.failed(ex.getMessage()));
        }
    }

    @Override
    public void cancel(RemoteAgentInvocationService.RemoteTaskReference reference) {
        if (reference == null || reference.remoteAgentId() == null || reference.remoteTaskId() == null) {
            return;
        }
        ClientTransport transport = obtainTransport(reference.remoteAgentId());
        if (transport != null) {
            transport.cancelTask(CancelTaskParams.builder().id(reference.remoteTaskId()).build(), null);
        }
    }

    private ClientTransport obtainTransport(String remoteAgentId) {
        if (remoteAgentId == null) {
            return null;
        }
        ClientTransport transport = transportCache.computeIfAbsent(remoteAgentId, id -> {
            LOG.info("remote agent transport created remoteAgentId={}", id);
            return transportFactory.apply(id);
        });
        if (transport == null) {
            LOG.warn("remote agent transport factory returned null for remoteAgentId={}", remoteAgentId);
        }
        return transport;
    }

    private static MessageSendParams toParams(RemoteAgentInvocationService.RemoteAgentRequest request) {
        Message.Builder message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(UUID.randomUUID().toString())
                .parts(List.<Part<?>>of(new TextPart(request.message())));
        if (hasText(request.remoteTaskId())) {
            message.taskId(request.remoteTaskId());
        }
        if (hasText(request.remoteContextId())) {
            message.contextId(request.remoteContextId());
        }
        return MessageSendParams.builder()
                .message(message.build())
                .metadata(request.arguments())
                .build();
    }

    private static RemoteAgentInvocationService.RemoteAgentResult toResult(StreamingEventKind event) {
        if (event instanceof Task task) {
            return statusResult(task.status(), task.id(), task.contextId(), task.metadata());
        }
        if (event instanceof Message message) {
            return new RemoteAgentInvocationService.RemoteAgentResult(
                    RemoteAgentInvocationService.RemoteAgentResult.Type.MESSAGE,
                    messageText(message),
                    message.taskId(),
                    message.contextId(),
                    message.metadata());
        }
        if (event instanceof TaskArtifactUpdateEvent artifactUpdate) {
            Artifact artifact = artifactUpdate.artifact();
            return new RemoteAgentInvocationService.RemoteAgentResult(
                    RemoteAgentInvocationService.RemoteAgentResult.Type.ARTIFACT,
                    artifact == null ? "" : partsText(artifact.parts()),
                    artifactUpdate.taskId(),
                    artifactUpdate.contextId(),
                    artifactUpdate.metadata());
        }
        if (event instanceof TaskStatusUpdateEvent statusUpdate) {
            return statusResult(statusUpdate.status(), statusUpdate.taskId(), statusUpdate.contextId(),
                    statusUpdate.metadata());
        }
        return null;
    }

    private static RemoteAgentInvocationService.RemoteAgentResult statusResult(
            TaskStatus status, String taskId, String contextId, Map<String, Object> metadata) {
        TaskState state = status == null ? null : status.state();
        String text = status == null ? "" : messageText(status.message());
        if (state == TaskState.TASK_STATE_INPUT_REQUIRED) {
            return new RemoteAgentInvocationService.RemoteAgentResult(
                    RemoteAgentInvocationService.RemoteAgentResult.Type.INPUT_REQUIRED,
                    text, taskId, contextId, metadata);
        }
        if (state == TaskState.TASK_STATE_COMPLETED) {
            return new RemoteAgentInvocationService.RemoteAgentResult(
                    RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED,
                    text, taskId, contextId, metadata);
        }
        if (state != null && state.isFinal()) {
            return new RemoteAgentInvocationService.RemoteAgentResult(
                    RemoteAgentInvocationService.RemoteAgentResult.Type.FAILED,
                    text, taskId, contextId, metadata);
        }
        return null;
    }

    private static boolean hasRemoteTerminal(List<RemoteAgentInvocationService.RemoteAgentResult> results) {
        return results.stream().anyMatch(A2aRemoteAgentOutboundAdapter::isRemoteTerminal);
    }

    private static boolean isRemoteTerminal(RemoteAgentInvocationService.RemoteAgentResult result) {
        return result.type() == RemoteAgentInvocationService.RemoteAgentResult.Type.INPUT_REQUIRED
                || result.type() == RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED
                || result.type() == RemoteAgentInvocationService.RemoteAgentResult.Type.FAILED;
    }

    private static boolean causedByCancellation(Throwable error) {
        for (Throwable cursor = error; cursor != null; cursor = cursor.getCause()) {
            if (cursor instanceof CancellationException) {
                return true;
            }
            if (cursor.getMessage() != null
                    && cursor.getMessage().toLowerCase(java.util.Locale.ROOT).contains("request cancelled")) {
                return true;
            }
        }
        return false;
    }

    private static String messageText(Message message) {
        return message == null ? "" : partsText(message.parts());
    }

    private static String partsText(List<Part<?>> parts) {
        if (parts == null) {
            return "";
        }
        return parts.stream()
                .filter(TextPart.class::isInstance)
                .map(TextPart.class::cast)
                .map(TextPart::text)
                .reduce("", String::concat);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
