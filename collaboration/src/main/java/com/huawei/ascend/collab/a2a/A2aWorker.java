package com.huawei.ascend.collab.a2a;

import com.huawei.ascend.collab.core.SubTask;
import com.huawei.ascend.collab.core.TaskToken;
import com.huawei.ascend.collab.core.WorkResult;
import com.huawei.ascend.collab.core.Worker;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.http.JdkA2AHttpClient;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TransportProtocol;

/**
 * Bridges the collaboration engine to a real A2A agent: the
 * {@link com.huawei.ascend.collab.core.Coordinator} dispatches a {@link SubTask}
 * to this worker, which streams it to a remote A2A endpoint over the SDK
 * {@link ClientTransport}, carrying the {@link TaskToken} on the message metadata,
 * and maps the remote task's terminal state to a {@link WorkResult}. The same
 * Coordinator therefore orchestrates real A2A agents (this worker) or, in eval,
 * deterministic in-memory workers.
 *
 * <p>Uses the blocking {@code message/send} call and maps the terminal
 * {@link Task} state (or a direct {@link Message} reply) to a {@link WorkResult}.
 * The {@link TaskToken} rides the message metadata as the idempotency/deadline
 * credential; the tenant rides the {@code X-Tenant-Id} header (not
 * {@code MessageSendParams.tenant()}, which would route to a tenant-scoped URL).
 * On a response this worker verifies the remote echoed the issued token back on the
 * response metadata; an absent/mismatched echo makes the coordinator reject it.
 *
 * <p><b>Robustness.</b> The card fetch (constructor) and each call's wall-clock time are
 * bounded by {@code timeoutMs} (connect timeout on the HTTP client + a timed wait on the
 * daemon pool); a hung remote yields a {@link WorkResult.Status#TIMEOUT} the coordinator
 * reclaims rather than blocking forever. The abandoned remote call is NOT actively cancelled
 * on timeout (a timed-out send returns no remote task id; {@link #cancelRemote} is a manual
 * hook for callers holding a task id). Because a reclaim re-dispatches the same work, the
 * remote agent MUST treat {@code task.token.idempotencyKey} (carried in metadata, stable
 * across a task's redispatch lineage) as a dedupe key to avoid double execution.
 */
public final class A2aWorker implements Worker {

    public static final String MK_TOKEN = "task.token.id";
    public static final String MK_TASK = "task.token.task";
    public static final String MK_IDEM = "task.token.idempotencyKey";
    public static final String MK_DEADLINE = "task.token.deadlineEpochMs";

    /** A2A protocol major versions this client speaks; a peer on a newer major is rejected. */
    private static final Set<Integer> SUPPORTED_PROTOCOL_MAJORS = Set.of(1);

    /** Daemon pool that bounds a blocking A2A call's wall-clock time (see {@link #execute}). */
    private static final ExecutorService CALL_POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "a2a-worker-call");
        t.setDaemon(true);
        return t;
    });

    private final String id;
    private final Set<String> capabilities;
    private final ClientTransport transport;
    private final long timeoutMs;

    /**
     * @param baseUrl the remote agent's BASE url (e.g. {@code http://host:8080}); the
     *                agent card is resolved from {@code /.well-known/agent-card.json}.
     */
    public A2aWorker(String id, Set<String> capabilities, String baseUrl) {
        this(id, capabilities, baseUrl, 30_000);
    }

    public A2aWorker(String id, Set<String> capabilities, String baseUrl, long timeoutMs) {
        this.id = id;
        this.capabilities = Set.copyOf(capabilities);
        this.timeoutMs = timeoutMs;
        try {
            // A connect-timeout-bounded HTTP client so an unreachable agent fails fast.
            JdkA2AHttpClient http = new JdkA2AHttpClient(HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs)).build());
            // The JDK HttpClient has no read timeout and A2ACardResolver exposes no per-request
            // deadline, so a peer that accepts the TCP connection but never sends the card body
            // would block this constructor forever. Bound the fetch on the daemon CALL_POOL.
            AgentCard card = fetchAgentCardWithin(http, baseUrl, timeoutMs);
            // Negotiate by binding + protocol version (mixed-fleet safe) rather than blindly
            // taking supportedInterfaces.get(0); fails clearly if the peer is on a newer major.
            AgentInterface iface = ProtocolNegotiator.select(
                    card, Set.of(TransportProtocol.JSONRPC.asString()), SUPPORTED_PROTOCOL_MAJORS);
            this.transport = new JSONRPCTransport(http, card, iface, List.of());
        } catch (Throwable e) {
            throw new IllegalStateException(
                    "failed to resolve A2A agent card at " + baseUrl + ": " + e.getMessage(), e);
        }
    }

    /** Resolve the agent card, never blocking longer than {@code timeoutMs} (the read side is otherwise unbounded). */
    private static AgentCard fetchAgentCardWithin(JdkA2AHttpClient http, String baseUrl, long timeoutMs)
            throws Exception {
        Future<AgentCard> f = CALL_POOL.submit(() ->
                A2ACardResolver.builder().baseUrl(baseUrl).httpClient(http).build().getAgentCard());
        try {
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            f.cancel(true);
            throw new IllegalStateException("agent card fetch exceeded " + timeoutMs + "ms");
        }
    }

    /** For tests/custom transports. */
    public A2aWorker(String id, Set<String> capabilities, ClientTransport transport, long timeoutMs) {
        this.id = id;
        this.capabilities = Set.copyOf(capabilities);
        this.transport = transport;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<String> capabilities() {
        return capabilities;
    }

    @Override
    public WorkResult execute(SubTask task, TaskToken token) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put(MK_TOKEN, token.tokenId().toString());
            meta.put(MK_TASK, token.taskId());
            meta.put(MK_IDEM, token.idempotencyKey().toString());
            meta.put(MK_DEADLINE, token.deadlineEpochMs());

            Message message = Message.builder()
                    .role(Message.Role.ROLE_USER)
                    .messageId(UUID.randomUUID().toString())
                    // A2A-native correlation: a stable contextId per task (constant across the
                    // task's redispatch lineage) lets the remote runtime and any tracing tie the
                    // remote execution back to this coordinator task.
                    .contextId(token.taskId())
                    .parts(List.<Part<?>>of(new TextPart(task.payload() == null ? "" : task.payload())))
                    .metadata(meta)
                    .build();
            // Tenant rides the X-Tenant-Id header (which the runtime reads), NOT
            // MessageSendParams.tenant() — the latter makes the SDK route to a
            // tenant-scoped URL path (/a2a/{tenant}) the runtime does not serve.
            MessageSendParams params = MessageSendParams.builder()
                    .message(message).metadata(meta).build();
            ClientCallContext ctx = new ClientCallContext(Map.of(),
                    Map.of("X-Tenant-Id", token.tenantId()));

            // Bound the blocking call's wall-clock time: the runtime read side has no
            // request timeout, so a hung remote would otherwise block the coordinator
            // forever. On timeout we abandon and report TIMEOUT (the coordinator reclaims).
            Future<EventKind> call = CALL_POOL.submit(() -> transport.sendMessage(params, ctx));
            try {
                EventKind result = call.get(timeoutMs, TimeUnit.MILLISECONDS);
                return map(task, token, result);
            } catch (TimeoutException te) {
                call.cancel(true);
                return WorkResult.timeout(task.id(), token, id);
            }
        } catch (CompletionException | java.util.concurrent.ExecutionException ee) {
            Throwable cause = ee.getCause() == null ? ee : ee.getCause();
            return WorkResult.failed(task.id(), token, id,
                    "a2a error: " + cause.getClass().getSimpleName()
                            + (cause.getMessage() == null ? "" : ": " + cause.getMessage()));
        } catch (Throwable t) {
            return WorkResult.failed(task.id(), token, id,
                    "a2a error: " + t.getClass().getSimpleName()
                            + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        }
    }

    private WorkResult map(SubTask task, TaskToken token, EventKind result) {
        if (result instanceof Task t) {
            // Verify the remote actually echoed the issued token (the real-bridge counterpart
            // of the in-memory token check): parse it from the response metadata. A null echo
            // makes the coordinator REJECT an agent that didn't prove token possession, instead
            // of silently trusting the locally-issued token.
            TaskToken echoed = echoedToken(token, taskMetadata(t));
            TaskState state = t.status() == null ? null : t.status().state();
            String text = textFrom(t);
            if (state == TaskState.TASK_STATE_COMPLETED) {
                return WorkResult.completed(task.id(), text == null || text.isBlank() ? "completed" : text, echoed, id);
            }
            if (state == TaskState.TASK_STATE_INPUT_REQUIRED || state == TaskState.TASK_STATE_AUTH_REQUIRED) {
                return new WorkResult(task.id(), WorkResult.Status.INPUT_REQUIRED, null, echoed, id, null, "remote input");
            }
            if (state == TaskState.TASK_STATE_CANCELED) {
                return WorkResult.timeout(task.id(), echoed, id);
            }
            return WorkResult.failed(task.id(), echoed, id, "remote state " + state);
        }
        if (result instanceof Message m) {
            TaskToken echoed = echoedToken(token, m.metadata());
            String text = textOf(m);
            return WorkResult.completed(task.id(), text == null || text.isBlank() ? "completed" : text, echoed, id);
        }
        return WorkResult.failed(task.id(), null, id, "unexpected event kind: " + result);
    }

    /** Prefer task-level metadata for the token echo, falling back to the status message's. */
    private static Map<String, Object> taskMetadata(Task t) {
        if (t.metadata() != null && !t.metadata().isEmpty()) {
            return t.metadata();
        }
        if (t.status() != null && t.status().message() != null) {
            return t.status().message().metadata();
        }
        return null;
    }

    /**
     * Reconstruct the token the remote echoed back from response metadata (the same keys this
     * worker sent). Returns {@code null} when the echo is absent or malformed, so the coordinator
     * rejects an agent that did not present the issued token. Only tokenId/taskId/idempotencyKey/
     * deadline are coordinator-verified; the remaining fields are copied from the issued token to
     * satisfy {@link TaskToken} construction.
     */
    private static TaskToken echoedToken(TaskToken issued, Map<String, Object> md) {
        if (md == null) {
            return null;
        }
        Object tok = md.get(MK_TOKEN);
        Object tsk = md.get(MK_TASK);
        Object idem = md.get(MK_IDEM);
        Object deadline = md.get(MK_DEADLINE);
        if (tok == null || tsk == null || idem == null || deadline == null) {
            return null;
        }
        try {
            long deadlineMs = deadline instanceof Number n
                    ? n.longValue() : Long.parseLong(deadline.toString().trim());
            return new TaskToken(
                    UUID.fromString(tok.toString()),
                    tsk.toString(),
                    issued.capability(),
                    issued.assignedAgentId(),
                    issued.tenantId(),
                    UUID.fromString(idem.toString()),
                    deadlineMs,
                    issued.issuedAtEpochMs());
        } catch (RuntimeException malformed) {
            return null;   // unparseable echo == no proof of possession
        }
    }

    /**
     * Best-effort <b>manual</b> cleanup hook: sends a {@code CancelTask} for a remote task whose
     * id the caller already obtained from a prior response. It is intentionally NOT auto-invoked
     * on {@link #execute} timeout — a timed-out {@code message/send} returns no remote task id to
     * cancel — so on timeout the coordinator reclaims by re-dispatch and the remote relies on
     * {@code idempotencyKey} dedupe (see class doc) rather than active cancellation.
     */
    public void cancelRemote(String remoteTaskId, String tenantId) {
        try {
            transport.cancelTask(new CancelTaskParams(remoteTaskId),
                    new ClientCallContext(Map.of(), Map.of("X-Tenant-Id", tenantId)));
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    /** Collect text from a completed task: its artifacts plus any status message. */
    private static String textFrom(Task t) {
        StringBuilder sb = new StringBuilder();
        if (t.artifacts() != null) {
            for (Artifact a : t.artifacts()) {
                appendParts(sb, a.parts());
            }
        }
        if (t.status() != null && t.status().message() != null) {
            appendParts(sb, t.status().message().parts());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String textOf(Message m) {
        StringBuilder sb = new StringBuilder();
        appendParts(sb, m.parts());
        return sb.isEmpty() ? null : sb.toString();
    }

    private static void appendParts(StringBuilder sb, List<Part<?>> parts) {
        if (parts == null) {
            return;
        }
        for (Part<?> p : parts) {
            if (p instanceof TextPart tp) {
                sb.append(tp.text());
            }
        }
    }
}
