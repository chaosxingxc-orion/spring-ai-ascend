package com.huawei.ascend.client;

import com.huawei.ascend.client.telemetry.ClientCallSpan;
import com.huawei.ascend.client.telemetry.ClientTelemetry;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A2A client for spring-ai-ascend ingress endpoints, built on the OSS
 * a2a-java-sdk JSON-RPC client. The facade exists for the semantics a raw
 * protocol client leaves to every caller:
 *
 * <ul>
 *   <li>blocking send/stream that completes on the platform's terminal
 *       events and classifies the SDK's post-terminal SSE cancellation as
 *       normal completion (see {@link A2aEvents});</li>
 *   <li>per-call W3C {@code traceparent} origination and {@code traceresponse}
 *       correlation surfaced on every {@link A2aResponse}; with a
 *       {@link ClientTelemetry} configured, the outbound header is derived
 *       from that call's business span so wire trace and local span share
 *       one trace-id;</li>
 *   <li>JWT bearer / {@code X-Tenant-Id} headers on every request, matching
 *       the platform's tenant cross-check ingress (ADR-0040).</li>
 * </ul>
 *
 * <p>Instances are thread-safe: each call builds its own transport over one
 * shared JDK HTTP client, and the agent card is fetched once and cached.
 */
public final class AscendA2aClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AscendA2aClient.class);

    private final String baseUrl;
    private final Duration timeout;
    private final ClientAuth auth;
    private final TracePropagation trace;
    private final ClientTelemetry telemetry;
    private final String serverAddress;
    private final HttpClient http;
    private final AtomicReference<AgentCard> cachedCard = new AtomicReference<>();

    private AscendA2aClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.timeout = builder.timeout;
        this.auth = builder.auth;
        this.trace = builder.trace;
        this.telemetry = builder.telemetry;
        this.serverAddress = hostOf(builder.baseUrl);
        this.http = HttpClient.newBuilder().connectTimeout(builder.timeout).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The server's agent card from {@code /.well-known/agent-card.json},
     * fetched with this client's auth/trace headers and cached for the
     * lifetime of the client (the card also routes every subsequent call).
     */
    public AgentCard agentCard() {
        AgentCard cached = cachedCard.get();
        if (cached != null) {
            return cached;
        }
        AgentCard fetched = new A2ACardResolver(
                newHttpClient(traceresponse -> { }), baseUrl, null, null,
                callHeaders(trace.newTraceparent()))
                .getAgentCard();
        cachedCard.compareAndSet(null, fetched);
        return cachedCard.get();
    }

    /**
     * Blocking non-streaming send (JSON-RPC {@code SendMessage}); the answer
     * arrives as one terminal {@code Message} or {@code Task}.
     */
    public A2aResponse sendText(SendSpec spec) {
        Objects.requireNonNull(spec, "spec");
        AgentCard card = agentCard();
        ClientCallSpan span = telemetry.startCall("send", spec, tenantId(), serverAddress);
        String traceparent = outboundTraceparent(span);
        AtomicReference<String> traceresponse = new AtomicReference<>();
        JSONRPCTransport transport = newTransport(card, traceresponse);
        try {
            EventKind result = transport.sendMessage(
                    messageSendParams(spec), callContext(traceparent));
            List<StreamingEventKind> events = result instanceof StreamingEventKind streaming
                    ? List.of(streaming) : List.of();
            return complete(span, events, traceparent, traceresponse.get());
        } catch (RuntimeException e) {
            fail(span, e, traceresponse.get());
            throw e;
        } finally {
            transport.close();
        }
    }

    /** Streaming send aggregated into one blocking result; see {@link #streamText(SendSpec, Consumer)}. */
    public A2aResponse streamText(SendSpec spec) throws InterruptedException {
        return streamText(spec, event -> { });
    }

    /**
     * Streaming send (JSON-RPC {@code SendStreamingMessage} over SSE):
     * {@code listener} observes every event as it arrives; the call blocks
     * until a terminal event (or classified failure), then returns the
     * aggregate. Failure classification: any transport error before a
     * terminal event — including a premature SSE cancellation — fails the
     * call; the SDK's normal post-terminal cancellation does not.
     *
     * @throws IllegalStateException when the stream fails or does not reach a
     *                               terminal event within the configured timeout
     */
    public A2aResponse streamText(SendSpec spec, Consumer<StreamingEventKind> listener)
            throws InterruptedException {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(listener, "listener");
        AgentCard card = agentCard();
        ClientCallSpan span = telemetry.startCall("stream", spec, tenantId(), serverAddress);
        String traceparent = outboundTraceparent(span);
        AtomicReference<String> traceresponse = new AtomicReference<>();
        try {
            List<StreamingEventKind> events = streamEvents(card, spec, listener, traceparent,
                    traceresponse);
            return complete(span, events, traceparent, traceresponse.get());
        } catch (RuntimeException | InterruptedException e) {
            fail(span, e, traceresponse.get());
            throw e;
        }
    }

    /** The streaming exchange itself: collect events until terminal/failure/timeout. */
    private List<StreamingEventKind> streamEvents(AgentCard card, SendSpec spec,
            Consumer<StreamingEventKind> listener, String traceparent,
            AtomicReference<String> traceresponse) throws InterruptedException {
        List<StreamingEventKind> events = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean sawTerminal = new AtomicBoolean(false);
        JSONRPCTransport transport = newTransport(card, traceresponse);
        try {
            transport.sendMessageStreaming(
                    messageSendParams(spec),
                    event -> {
                        events.add(event);
                        listener.accept(event);
                        if (A2aEvents.isTerminal(event)) {
                            sawTerminal.set(true);
                            completed.countDown();
                        }
                    },
                    error -> {
                        if (A2aEvents.isFailureError(error, sawTerminal.get())) {
                            failure.set(error);
                        }
                        completed.countDown();
                    },
                    callContext(traceparent));
            if (!completed.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("A2A stream did not complete before timeout");
            }
        } finally {
            transport.close();
        }
        if (failure.get() != null) {
            throw new IllegalStateException("A2A stream failed", failure.get());
        }
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    @Override
    public void close() {
        // Immediate shutdown, not the graceful close(): a graceful close waits
        // for in-flight exchanges, and a stream that just failed its completion
        // timeout may still hold an open SSE connection — close() must never
        // hang on it. All successful calls complete before returning, so there
        // is nothing graceful shutdown would protect.
        http.shutdownNow();
        // No-op unless the telemetry owns its pipeline (ClientTelemetry.otlpHttp):
        // a consumer-supplied OpenTelemetry SDK is never shut down here.
        telemetry.close();
    }

    /**
     * The header that crosses the wire: the active span's trace context when
     * telemetry originates one, otherwise this client's own per-call mint.
     */
    private String outboundTraceparent(ClientCallSpan span) {
        String fromSpan = span.traceparent();
        return fromSpan != null ? fromSpan : trace.newTraceparent();
    }

    private A2aResponse complete(ClientCallSpan span, List<StreamingEventKind> events,
            String traceparent, String traceresponse) {
        A2aResponse result = response(events, traceparent, traceresponse);
        span.traceresponse(traceresponse);
        span.succeed(events.stream().anyMatch(A2aEvents::isTerminal), result.text());
        return result;
    }

    private static void fail(ClientCallSpan span, Throwable error, String traceresponse) {
        span.traceresponse(traceresponse);
        span.fail(error);
    }

    private String tenantId() {
        return auth == null ? null : auth.tenantId();
    }

    /** Null (attribute omitted) rather than failing on an unparsable base URL. */
    private static String hostOf(String baseUrl) {
        try {
            return URI.create(baseUrl).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private A2aResponse response(List<StreamingEventKind> events, String traceparent,
            String traceresponse) {
        if (log.isDebugEnabled()) {
            log.debug("A2A call completed: events={} traceparent={} traceresponse={}",
                    events.size(), traceparent, traceresponse);
        }
        return new A2aResponse(A2aEvents.textFrom(events), events,
                new TraceCorrelation(traceparent, traceresponse));
    }

    private JSONRPCTransport newTransport(AgentCard card, AtomicReference<String> traceresponse) {
        return new JSONRPCTransport(newHttpClient(traceresponse::set), card,
                Utils.getFavoriteInterface(card), List.of());
    }

    private HeaderCapturingHttpClient newHttpClient(Consumer<String> traceresponseSink) {
        return new HeaderCapturingHttpClient(http, timeout, traceresponseSink);
    }

    private ClientCallContext callContext(String traceparent) {
        return new ClientCallContext(Map.of(), callHeaders(traceparent));
    }

    private Map<String, String> callHeaders(String traceparent) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (auth != null) {
            headers.putAll(auth.headers());
        }
        headers.put("traceparent", traceparent);
        return headers;
    }

    private static MessageSendParams messageSendParams(SendSpec spec) {
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(spec.messageId())
                .contextId(spec.sessionId())
                .metadata(spec.messageMetadata())
                .parts(List.of(new TextPart(spec.text())))
                .build();
        return MessageSendParams.builder()
                .message(message)
                .build();
    }

    /** Builder; {@code baseUrl} is the only required field. */
    public static final class Builder {

        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(30);
        private ClientAuth auth;
        private TracePropagation trace = TracePropagation.sampled();
        private ClientTelemetry telemetry = ClientTelemetry.noop();

        private Builder() {
        }

        /** Server base URL, e.g. {@code http://localhost:8080}. Required. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** Per-call completion timeout (connect + request + stream-to-terminal). Default 30s. */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        /** Optional; without it no Authorization/X-Tenant-Id headers are sent. */
        public Builder auth(ClientAuth auth) {
            this.auth = auth;
            return this;
        }

        /**
         * Optional; defaults to {@link TracePropagation#sampled()}. Used only
         * for calls whose telemetry originates no trace context.
         */
        public Builder tracePropagation(TracePropagation trace) {
            this.trace = Objects.requireNonNull(trace, "trace");
            return this;
        }

        /**
         * Optional; defaults to {@link ClientTelemetry#noop()} (no spans,
         * unchanged behavior). The telemetry is closed with the client —
         * harmless for span-only telemetry, and exactly what an owned
         * {@link ClientTelemetry#otlpHttp} pipeline needs.
         */
        public Builder telemetry(ClientTelemetry telemetry) {
            this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
            return this;
        }

        public AscendA2aClient build() {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("baseUrl must not be null or blank");
            }
            return new AscendA2aClient(this);
        }
    }
}
