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
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
public class VersatileClient {

    private static final Logger LOG = LoggerFactory.getLogger(VersatileClient.class);

    private static final String POISON = "__VERSATILE_STREAM_END__";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final VersatileProperties properties;

    /** Always route directly — never through a proxy. */
    private static final ProxySelector NO_PROXY = new ProxySelector() {
        @Override public java.util.List<Proxy> select(URI uri) { return java.util.List.of(Proxy.NO_PROXY); }
        @Override public void connectFailed(URI uri, SocketAddress sa, java.io.IOException ioe) {}
    };

    public VersatileClient(VersatileProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        Duration timeout = properties.getTimeout() != null ? properties.getTimeout() : Duration.ofSeconds(30);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(NO_PROXY)
                .executor(Executors.newCachedThreadPool())
                .build();
    }

    /**
     * POST to the versatile REST endpoint and return a lazy stream of response
     * body lines. Each emitted string is one SSE {@code data:} line or a raw
     * text line from the stream.
     */
    public Stream<String> stream(VersatileHttpRequest request) {
        BlockingQueue<String> queue = new LinkedBlockingQueue<>(256);
        AtomicReference<Throwable> upstreamError = new AtomicReference<>();

        String bodyJson;
        try {
            bodyJson = MAPPER.writeValueAsString(
                    request.body() != null ? request.body() : java.util.Collections.emptyMap());
        } catch (Exception e) {
            throw new VersatileClientException("Failed to serialize request body", e);
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(request.url()))
                .timeout(properties.getTimeout());

        // Set headers first so Content-Type is established before the body publisher
        request.headers().forEach((key, value) -> {
            if (value != null) {
                reqBuilder.header(key, value);
            }
        });

        // If no Content-Type was set by config, default to application/json
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
                    String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    upstreamError.set(new VersatileClientException(
                            "HTTP " + statusCode + ": " + errorBody));
                    safeOffer(queue, POISON);
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isEmpty()) {
                            LOG.debug("versatile sse line len={}", line.length());
                            safeOffer(queue, line);
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warn("versatile upstream error: {}", e.getMessage());
                upstreamError.set(e);
            } finally {
                safeOffer(queue, POISON);
            }
        });

        long timeoutMs = properties.getTimeout().toMillis();

        return Stream.generate(() -> {
            try {
                String line = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
                if (line == null) {
                    LOG.warn("versatile stream timeout after {}ms", timeoutMs);
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
                return null;
            }
        }).takeWhile(Objects::nonNull);
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

    private static void safeOffer(BlockingQueue<String> queue, String item) {
        try {
            queue.put(item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
