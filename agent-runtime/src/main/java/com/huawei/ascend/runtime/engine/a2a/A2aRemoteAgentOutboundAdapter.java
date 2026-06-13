package com.huawei.ascend.runtime.engine.a2a;

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
import java.util.concurrent.atomic.AtomicBoolean;
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
    /** Stable, programmatically matchable error code carried on the timeout result's metadata. */
    public static final String REMOTE_TIMEOUT_CODE = "REMOTE_TIMEOUT";

    private static final Logger LOG = LoggerFactory.getLogger(A2aRemoteAgentOutboundAdapter.class);
    private static final Duration DEFAULT_STREAM_TIMEOUT = Duration.ofSeconds(60);

    private final Function<String, String> endpointResolver;
    private final Function<String, ClientTransport> transportBuilder;
    private final Function<String, Duration> streamTimeoutResolver;
    // One transport (and its underlying HTTP client) per remote agent, reused across
    // invocations and input-required continuations instead of rebuilt on every call.
    // The cached endpoint is compared on every lookup so a card/endpoint change
    // observed by the card cache rebuilds the transport instead of calling the old host.
    private final Map<String, CachedTransport> transportCache = new ConcurrentHashMap<>();

    private record CachedTransport(String endpoint, ClientTransport transport) {
    }

    public A2aRemoteAgentOutboundAdapter(RemoteAgentCardCache cardCache) {
        this(cardCache::endpoint, JSONRPCTransport::new, cardCache::streamTimeout);
    }

    public A2aRemoteAgentOutboundAdapter(Function<String, ClientTransport> transportFactory) {
        this(transportFactory, DEFAULT_STREAM_TIMEOUT);
    }

    public A2aRemoteAgentOutboundAdapter(Function<String, ClientTransport> transportFactory, Duration streamTimeout) {
        // Test seam keyed by remoteAgentId: the id doubles as the cached endpoint,
        // so the cache never invalidates and the factory sees the id unchanged.
        this(Function.identity(), transportFactory, remoteAgentId -> streamTimeout);
    }

    A2aRemoteAgentOutboundAdapter(Function<String, String> endpointResolver,
            Function<String, ClientTransport> transportBuilder,
            Function<String, Duration> streamTimeoutResolver) {
        this.endpointResolver = Objects.requireNonNull(endpointResolver, "endpointResolver");
        this.transportBuilder = Objects.requireNonNull(transportBuilder, "transportBuilder");
        this.streamTimeoutResolver = Objects.requireNonNull(streamTimeoutResolver, "streamTimeoutResolver");
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
        // Latch for late events: the SDK exposes no per-stream close handle, so after
        // a terminal/timeout return the callback can still fire on its own thread.
        // Every event runs under the gate and is dropped once closed, so a late event
        // never reaches the single-writer emitter (via eventConsumer) and never races
        // the result snapshot taken on the invoking thread.
        AtomicBoolean closed = new AtomicBoolean();
        Object gate = new Object();
        try {
            transport.sendMessageStreaming(toParams(request),
                    event -> {
                        RemoteAgentInvocationService.RemoteAgentResult result = toResult(event);
                        if (result == null) {
                            return;
                        }
                        synchronized (gate) {
                            if (closed.get()) {
                                return;
                            }
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
            boolean finished = completed.await(
                    effectiveStreamTimeout(request.remoteAgentId()).toMillis(), TimeUnit.MILLISECONDS);
            List<RemoteAgentInvocationService.RemoteAgentResult> received = closeAndSnapshot(gate, closed, results);
            if (!finished) {
                // Results received before the deadline were already projected to the
                // caller, so dropping them now would contradict what the caller saw;
                // keep them and append a classified failure. The remote task would
                // otherwise keep running as an orphan — best-effort cancel it.
                cancelAfterTimeout(request.remoteAgentId(), received);
                List<RemoteAgentInvocationService.RemoteAgentResult> timedOut = new ArrayList<>(received);
                timedOut.add(new RemoteAgentInvocationService.RemoteAgentResult(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.FAILED,
                        "remote A2A stream timed out",
                        lastNonNull(received, RemoteAgentInvocationService.RemoteAgentResult::remoteTaskId),
                        lastNonNull(received, RemoteAgentInvocationService.RemoteAgentResult::remoteContextId),
                        Map.of("code", REMOTE_TIMEOUT_CODE, "retryable", true)));
                return List.copyOf(timedOut);
            }
            if (error.get() != null) {
                if (hasRemoteTerminal(received) && causedByCancellation(error.get())) {
                    return received;
                }
                return List.of(RemoteAgentInvocationService.RemoteAgentResult.failed(error.get().getMessage()));
            }
            return received;
        } catch (Exception ex) {
            List<RemoteAgentInvocationService.RemoteAgentResult> received = closeAndSnapshot(gate, closed, results);
            if (hasRemoteTerminal(received) && causedByCancellation(ex)) {
                return received;
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

    Duration effectiveStreamTimeout(String remoteAgentId) {
        Duration configured = streamTimeoutResolver.apply(remoteAgentId);
        return configured == null ? DEFAULT_STREAM_TIMEOUT : configured;
    }

    private static List<RemoteAgentInvocationService.RemoteAgentResult> closeAndSnapshot(Object gate,
            AtomicBoolean closed, List<RemoteAgentInvocationService.RemoteAgentResult> results) {
        synchronized (gate) {
            closed.set(true);
            return List.copyOf(results);
        }
    }

    private void cancelAfterTimeout(String remoteAgentId,
            List<RemoteAgentInvocationService.RemoteAgentResult> received) {
        String remoteTaskId = lastNonNull(received, RemoteAgentInvocationService.RemoteAgentResult::remoteTaskId);
        if (remoteTaskId == null) {
            return;
        }
        try {
            cancel(new RemoteAgentInvocationService.RemoteTaskReference(remoteAgentId, remoteTaskId,
                    lastNonNull(received, RemoteAgentInvocationService.RemoteAgentResult::remoteContextId)));
        } catch (RuntimeException ex) {
            LOG.warn("remote task cancel after stream timeout failed remoteAgentId={} remoteTaskId={} message={}",
                    remoteAgentId, remoteTaskId, A2aLogMasking.mask(ex.getMessage()));
        }
    }

    private static String lastNonNull(List<RemoteAgentInvocationService.RemoteAgentResult> results,
            Function<RemoteAgentInvocationService.RemoteAgentResult, String> extractor) {
        for (int i = results.size() - 1; i >= 0; i--) {
            String value = extractor.apply(results.get(i));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private ClientTransport obtainTransport(String remoteAgentId) {
        if (remoteAgentId == null) {
            return null;
        }
        String endpoint = endpointResolver.apply(remoteAgentId);
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        // compute() removes the mapping when the builder yields null, so an endpoint
        // that is not yet resolvable stays uncached and is retried on the next call.
        CachedTransport cached = transportCache.compute(remoteAgentId, (id, existing) -> {
            if (existing != null && existing.endpoint().equals(endpoint)) {
                return existing;
            }
            if (existing != null) {
                closeQuietly(id, existing.transport());
            }
            ClientTransport created = transportBuilder.apply(endpoint);
            return created == null ? null : new CachedTransport(endpoint, created);
        });
        return cached == null ? null : cached.transport();
    }

    private static void closeQuietly(String remoteAgentId, ClientTransport transport) {
        try {
            transport.close();
        } catch (RuntimeException ex) {
            LOG.warn("stale remote transport close failed remoteAgentId={} message={}",
                    remoteAgentId, A2aLogMasking.mask(ex.getMessage()));
        }
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
                    Messages.text(message),
                    message.taskId(),
                    message.contextId(),
                    message.metadata());
        }
        if (event instanceof TaskArtifactUpdateEvent artifactUpdate) {
            Artifact artifact = artifactUpdate.artifact();
            return new RemoteAgentInvocationService.RemoteAgentResult(
                    RemoteAgentInvocationService.RemoteAgentResult.Type.ARTIFACT,
                    artifact == null ? "" : Messages.text(artifact.parts()),
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
        String text = status == null ? "" : Messages.text(status.message());
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
