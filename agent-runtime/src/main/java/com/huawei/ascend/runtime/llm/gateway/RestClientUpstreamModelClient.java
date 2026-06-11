package com.huawei.ascend.runtime.llm.gateway;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Default upstream forwarder. Non-streaming exchanges go through Spring
 * {@link RestClient} (raw {@code exchange} access, so 4xx/5xx come back as data
 * instead of exceptions). Streaming uses the JDK {@link java.net.http.HttpClient}
 * directly because {@code RestClient} closes the upstream response when its
 * exchange callback returns — too early for a servlet streaming body that relays
 * chunks on another thread.
 *
 * <p>Both paths are time-bounded: an upstream that accepts the connection and
 * never answers must surface as a 502, not pin a servlet thread forever. The
 * request timeout bounds the buffered exchange end-to-end and, on streams,
 * time-to-response-headers — once a stream is open, relay duration is the
 * provider's to decide and stays unbounded by design.
 */
public final class RestClientUpstreamModelClient implements UpstreamModelClient {

    private final RestClient restClient;
    private final java.net.http.HttpClient streamingClient;
    private final Duration requestTimeout;

    public RestClientUpstreamModelClient(Duration connectTimeout, Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
        // The JDK request factory on both paths: it streams response bodies without
        // buffering, which the SSE relay depends on.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder()
                        .connectTimeout(connectTimeout)
                        .build());
        requestFactory.setReadTimeout(requestTimeout);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.streamingClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public UpstreamResponse exchange(UpstreamRequest request) {
        try {
            RestClient.RequestBodySpec spec = restClient.post()
                    .uri(URI.create(request.url()))
                    .contentType(MediaType.APPLICATION_JSON);
            if (hasCredential(request)) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + request.apiKey());
            }
            return spec.body(request.body())
                    .exchange((clientRequest, clientResponse) -> new UpstreamResponse(
                            clientResponse.getStatusCode().value(),
                            contentTypeOf(clientResponse.getHeaders()),
                            clientResponse.getBody().readAllBytes()));
        } catch (ResourceAccessException | UncheckedIOException e) {
            throw new UpstreamIoException("upstream I/O failure: " + e.getMessage(), e);
        }
    }

    @Override
    public UpstreamStreamResponse openStream(UpstreamRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(request.url()))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofByteArray(request.body()));
        if (hasCredential(request)) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + request.apiKey());
        }
        HttpRequest httpRequest = builder.build();
        try {
            HttpResponse<InputStream> response =
                    streamingClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            return new UpstreamStreamResponse(
                    response.statusCode(),
                    response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(null),
                    response.body());
        } catch (IOException e) {
            throw new UpstreamIoException("upstream I/O failure: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamIoException("interrupted while contacting upstream", e);
        }
    }

    /**
     * An explicitly empty api-key means a no-auth upstream (e.g. a local model
     * server): the Authorization header is omitted entirely — a concatenated
     * blank or null credential must never go on the wire.
     */
    private static boolean hasCredential(UpstreamRequest request) {
        return request.apiKey() != null && !request.apiKey().isBlank();
    }

    Duration connectTimeout() {
        return streamingClient.connectTimeout().orElse(null);
    }

    Duration requestTimeout() {
        return requestTimeout;
    }

    private static String contentTypeOf(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        return contentType == null ? null : contentType.toString();
    }
}
