package com.huawei.ascend.runtime.engine.versatile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP client that calls the remote versatile REST API and returns a lazy
 * {@link Stream} of SSE text lines.
 *
 * <p>Uses {@link java.net.http.HttpClient} (JDK 11+) with a
 * {@link BlockingQueue} to bridge the async HTTP response body into a
 * synchronous {@link Stream} consumable by the runtime engine dispatcher.
 */
public class VersatileClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VersatileClient.class);

    private static final String POISON = "__VERSATILE_STREAM_END__";
    static final String CONNECTION_CLOSED_EVENT =
            "data:{\"event\":\"connection_closed\",\"data\":{\"reason\":\"eof\"}}";
    static final String CONNECTION_CLOSED_ERROR_EVENT =
            "data:{\"event\":\"connection_closed\",\"data\":{\"reason\":\"error\"}}";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final VersatileProperties properties;
    private final ExecutorService httpExecutor;
    private final Duration timeout;

    /** Always route directly — never through a proxy. */
    private static final ProxySelector NO_PROXY = new ProxySelector() {
        @Override public java.util.List<Proxy> select(URI uri) { return java.util.List.of(Proxy.NO_PROXY); }
        @Override public void connectFailed(URI uri, SocketAddress sa, java.io.IOException ioe) {}
    };

    public VersatileClient(VersatileProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.timeout = properties.getTimeout() != null ? properties.getTimeout() : Duration.ofSeconds(30);
        this.httpExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread t = new Thread(runnable, "versatile-http");
            t.setDaemon(true);
            return t;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(NO_PROXY)
                .executor(httpExecutor)
                .build();
    }

    @Override
    public void close() {
        httpExecutor.shutdownNow();
    }

    /**
     * POST to the versatile REST endpoint and return a lazy stream of response
     * body lines. Each emitted string is one SSE {@code data:} line or a raw
     * text line from the stream.
     */
    public Stream<String> stream(VersatileHttpRequest request) {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>(256);
        AtomicReference<Throwable> upstreamError = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<java.io.InputStream> bodyStreamRef = new AtomicReference<>();

        // Must be safe to call from any thread — closes the upstream body so a
        // producer blocked in readLine() unblocks immediately with an IOException.
        Runnable cancel = () -> {
            cancelled.set(true);
            java.io.InputStream stream = bodyStreamRef.getAndSet(null);
            if (stream != null) {
                try { stream.close(); } catch (Exception ignored) { /* best-effort */ }
            }
        };

        String bodyJson;
        try {
            bodyJson = MAPPER.writeValueAsString(
                    request.body() != null ? request.body() : java.util.Collections.emptyMap());
        } catch (Exception e) {
            throw new VersatileClientException("Failed to serialize request body", e);
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(request.url()))
                .timeout(timeout);

        request.headers().forEach((key, value) -> {
            if (value != null) {
                reqBuilder.header(key, value);
            }
        });

        if (!request.headers().containsKey("content-type")
                && !request.headers().containsKey("Content-Type")) {
            reqBuilder.header("Content-Type", "application/json");
        }

        byte[] bodyBytes = bodyJson.getBytes(StandardCharsets.UTF_8);
        reqBuilder.method(request.method().toUpperCase(),
                HttpRequest.BodyPublishers.ofByteArray(bodyBytes));

        HttpRequest httpRequest = reqBuilder.build();
        logCurl(httpRequest, bodyJson);

        Thread.ofVirtual().start(() -> {
            try {
                HttpResponse<java.io.InputStream> response = httpClient.send(
                        httpRequest, HttpResponse.BodyHandlers.ofInputStream());

                int statusCode = response.statusCode();
                LOG.info("versatile response status={}", statusCode);

                if (statusCode >= 400) {
                    try (java.io.InputStream errorStream = response.body()) {
                        byte[] errorBytes = errorStream.readNBytes(4096);
                        String errorBody = new String(errorBytes, StandardCharsets.UTF_8);
                        upstreamError.set(new VersatileClientException(
                                "HTTP " + statusCode + ": " + errorBody));
                    }
                    safeOffer(queue, CONNECTION_CLOSED_ERROR_EVENT, cancelled);
                    return;
                }

                java.io.InputStream bodyStream = response.body();
                bodyStreamRef.set(bodyStream);
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (cancelled.get()) {
                            LOG.info("versatile producer cancelled, stopping read loop");
                            break;
                        }
                        if (!line.isEmpty()) {
                            LOG.info("versatile sse received: {}", line);
                            if (!safeOffer(queue, line, cancelled)) {
                                break;
                            }
                        }
                    }
                    if (!cancelled.get()) {
                        LOG.info("versatile sse stream ended normally — injecting connection_closed");
                        safeOffer(queue, CONNECTION_CLOSED_EVENT, cancelled);
                    }
                } finally {
                    bodyStreamRef.set(null);
                }
            } catch (Exception e) {
                if (!cancelled.get()) {
                    LOG.warn("versatile upstream error: {}", e.getMessage());
                    upstreamError.set(e);
                    safeOffer(queue, CONNECTION_CLOSED_ERROR_EVENT, cancelled);
                } else {
                    LOG.info("versatile producer stream closed (cancelled): {}", e.getMessage());
                }
            } finally {
                safeOffer(queue, POISON, cancelled);
            }
        });

        long timeoutMs = timeout.toMillis();

        return Stream.generate(() -> {
            try {
                String line = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
                if (line == null) {
                    LOG.warn("versatile stream timeout after {}ms", timeoutMs);
                    cancel.run();
                    return POISON;
                }
                if (POISON.equals(line)) {
                    Throwable err = upstreamError.get();
                    if (err != null) {
                        throw new VersatileClientException("versatile upstream error: " + err.getMessage(), err);
                    }
                    return null;
                }
                return line;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancel.run();
                return null;
            }
        }).takeWhile(Objects::nonNull).onClose(cancel);
    }

    /** Header names that must never be logged in plain text. */
    private static final java.util.Set<String> SENSITIVE_HEADERS = java.util.Set.of(
            "authorization", "cookie", "set-cookie", "x-api-key",
            "x-auth-token", "cftk", "cf2-cftk", "cust-token");

    /**
     * Log the outgoing request as a copy-pasteable {@code curl} command.
     * Sensitive headers (tokens, cookies) have their values redacted.
     */
    private static void logCurl(HttpRequest request, String body) {
        if (!LOG.isInfoEnabled()) return;
        StringBuilder curl = new StringBuilder();
        curl.append("curl -X ").append(request.method()).append(" '").append(request.uri()).append("'");
        request.headers().map().forEach((name, values) -> {
            String lower = name.toLowerCase();
            if ("content-length".equals(lower) || "host".equals(lower)) return;
            for (String value : values) {
                String displayValue = SENSITIVE_HEADERS.contains(lower) ? "***" : value;
                curl.append(" \\\n  -H '").append(name).append(": ").append(displayValue).append("'");
            }
        });
        if (body != null && !body.isEmpty()) {
            String displayBody = body.length() > 500 ? body.substring(0, 500) + "..." : body;
            String escaped = displayBody.replace("'", "'\\''");
            curl.append(" \\\n  -d '").append(escaped).append("'");
        }
        LOG.info("versatile outgoing request:\n{}", curl);
    }

    private static boolean safeOffer(BlockingQueue<String> queue, String item, AtomicBoolean cancelled) {
        while (!cancelled.get()) {
            try {
                if (queue.offer(item, 100, TimeUnit.MILLISECONDS)) {
                    return true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("versatile safeOffer interrupted itemLen={}", item.length());
                return false;
            }
        }
        return false;
    }

    public VersatileProperties properties() {
        return properties;
    }

    /**
     * Wraps upstream HTTP / transport errors for consistent handling.
     */
    public static class VersatileClientException extends RuntimeException {
        public VersatileClientException(String message) {
            super(message);
        }

        public VersatileClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
