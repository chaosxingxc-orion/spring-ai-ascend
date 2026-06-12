package com.huawei.ascend.examples.langgraph;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class LangGraphRuntimeClientHandlerTest {

    @Test
    void handlerPostsLangGraphRunAndMapsEventNamedSse() throws Exception {
        CapturingHttpClient httpClient = new CapturingHttpClient(200, List.of(
                "event: metadata",
                "data: {\"run_id\":\"run-1\"}",
                "",
                "event: values",
                "data: {\"messages\":[{\"type\":\"human\",\"content\":\"ping\"},{\"type\":\"ai\",\"content\":\"pong\"}]}",
                "",
                "event: end",
                ""));
        LangGraphRuntimeClientHandler handler = handler(httpClient);

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.OUTPUT, AgentExecutionResult.Type.COMPLETED);
        assertThat(results.get(0).outputContent()).isEqualTo("pong");
        assertThat(httpClient.request.uri()).isEqualTo(URI.create("http://langgraph.local/runs/stream"));
        assertThat(httpClient.request.headers().firstValue("Accept")).contains("text/event-stream");
        assertThat(httpClient.request.headers().firstValue("X-Tenant-Id")).contains("tenant");
        assertThat(httpClient.request.headers().firstValue("X-Api-Key")).contains("key-1");
        // A hung upstream must not wedge the task forever: time-to-headers is bounded.
        assertThat(httpClient.request.timeout()).contains(Duration.ofSeconds(60));

        JsonNode body = new ObjectMapper().readTree(httpClient.body);
        assertThat(body.get("assistant_id").asText()).isEqualTo("wealth-advisor");
        assertThat(body.get("config").get("configurable").get("thread_id").asText()).isEqualTo("session");
        JsonNode messages = body.get("input").get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("role").asText()).isEqualTo("user");
        assertThat(messages.get(0).get("content").asText()).isEqualTo("ping");
        assertThat(messages.get(1).get("role").asText()).isEqualTo("assistant");
        assertThat(messages.get(1).get("content").asText()).isEqualTo("pong");
        assertThat(body.get("stream_mode").get(0).asText()).isEqualTo("values");
    }

    @Test
    void nonSuccessHttpStatusBecomesStructuredFailure() {
        CapturingHttpClient httpClient = new CapturingHttpClient(503, List.of("service unavailable"));
        LangGraphRuntimeClientHandler handler = handler(httpClient);

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).type()).isEqualTo(AgentExecutionResult.Type.FAILED);
        assertThat(results.get(0).errorCode()).isEqualTo("LANGGRAPH_RUNTIME_HTTP_503");
    }

    @Test
    void connectionFailureBecomesStructuredFailure() {
        LangGraphRuntimeClientHandler handler = handler(new FailingHttpClient(
                new UncheckedIOException(new IOException("connection refused"))));

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).type()).isEqualTo(AgentExecutionResult.Type.FAILED);
        assertThat(results.get(0).errorCode()).isEqualTo("LANGGRAPH_RUNTIME_IO");
        assertThat(results.get(0).errorMessage()).contains("connection refused");
    }

    @Test
    void midStreamDisconnectBecomesStructuredFailure() {
        // The concat tail throws when the iterator reaches it, like a dropped socket.
        LangGraphRuntimeClientHandler handler = handler(new CapturingHttpClient(200, () -> Stream.concat(
                Stream.of(
                        "event: values",
                        "data: {\"messages\":[{\"type\":\"ai\",\"content\":\"par\"}]}",
                        ""),
                Stream.<String>generate(() -> {
                    throw new UncheckedIOException(new IOException("stream reset"));
                }).limit(1))));

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.OUTPUT, AgentExecutionResult.Type.FAILED);
        assertThat(results.get(1).errorCode()).isEqualTo("LANGGRAPH_RUNTIME_IO");
        assertThat(results.get(1).errorMessage()).contains("stream reset");
    }

    /**
     * A data block that partially parses keeps the decoded events but must
     * surface the corrupt remainder as a structured failure — the dropped tail
     * may have been the terminal, and silence would drain into a false
     * COMPLETED.
     */
    @Test
    void partiallyParsedDataBlockSurfacesStructuredParseFailure() {
        CapturingHttpClient httpClient = new CapturingHttpClient(200, List.of(
                "event: values",
                "data: {\"messages\":[{\"type\":\"ai\",\"content\":\"par\"}]}",
                "data: {\"messages\":[{\"type\":\"ai\",\"content\":\"partial\"}],",
                ""));
        LangGraphRuntimeClientHandler handler = handler(httpClient);

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.OUTPUT, AgentExecutionResult.Type.FAILED);
        assertThat(results.get(0).outputContent()).isEqualTo("par");
        assertThat(results.get(1).errorCode()).isEqualTo("LANGGRAPH_RUNTIME_PARSE");
    }

    /**
     * stop() must release the SSE transport when the client owns it (created it
     * itself) — the leak this fixes: an owned JDK HttpClient keeps selector
     * threads alive until GC.
     */
    @Test
    void stopClosesClientOwnedHttpTransport() {
        java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        LangGraphRuntimeClient client = new LangGraphRuntimeClient(
                closeRecordingHttpClient(closed),
                new ObjectMapper(),
                new LangGraphRuntimeClientProperties("http://langgraph.local", "wealth-advisor", "/runs/stream", Map.of()),
                true);
        LangGraphRuntimeClientHandler handler = new LangGraphRuntimeClientHandler("langgraph-advisor", client);

        handler.stop();

        assertThat(closed).isTrue();
    }

    /** A borrowed (injected) transport belongs to its injector and must survive close(). */
    @Test
    void closeLeavesBorrowedHttpTransportOpen() {
        java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        LangGraphRuntimeClient client = new LangGraphRuntimeClient(
                closeRecordingHttpClient(closed),
                new ObjectMapper(),
                new LangGraphRuntimeClientProperties("http://langgraph.local", "wealth-advisor", "/runs/stream", Map.of()));

        client.close();

        assertThat(closed).isFalse();
    }

    private static HttpClient closeRecordingHttpClient(java.util.concurrent.atomic.AtomicBoolean closed) {
        return new TestHttpClient() {
            @Override
            public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                    HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
                throw new UnsupportedOperationException("no request expected");
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
    }

    private static LangGraphRuntimeClientHandler handler(HttpClient httpClient) {
        LangGraphRuntimeClient client = new LangGraphRuntimeClient(
                httpClient,
                new ObjectMapper(),
                new LangGraphRuntimeClientProperties(
                        "http://langgraph.local", "wealth-advisor", "/runs/stream", Map.of("X-Api-Key", "key-1")));
        return new LangGraphRuntimeClientHandler("langgraph-advisor", client);
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "session", "task", "langgraph-advisor"),
                "USER_MESSAGE",
                List.of(
                        Message.builder().role(Message.Role.ROLE_USER)
                                .parts(List.<Part<?>>of(new TextPart("ping"))).build(),
                        Message.builder().role(Message.Role.ROLE_AGENT)
                                .parts(List.<Part<?>>of(new TextPart("pong"))).build()),
                Map.of());
    }

    private abstract static class TestHttpClient extends HttpClient {

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return sendAsync(request, responseBodyHandler).join();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
    }

    private static final class CapturingHttpClient extends TestHttpClient {
        private final int responseStatusCode;
        private final Supplier<Stream<String>> responseBody;
        private HttpRequest request;
        private String body;

        private CapturingHttpClient(int responseStatusCode, List<String> responseLines) {
            this(responseStatusCode, responseLines::stream);
        }

        private CapturingHttpClient(int responseStatusCode, Supplier<Stream<String>> responseBody) {
            this.responseStatusCode = responseStatusCode;
            this.responseBody = responseBody;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            this.request = request;
            this.body = body(request);
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) new FixedResponse(request, responseBody, responseStatusCode);
            return CompletableFuture.completedFuture(response);
        }

        private static String body(HttpRequest request) {
            return request.bodyPublisher()
                    .map(CapturingSubscriber::capture)
                    .orElse("");
        }
    }

    private static final class FailingHttpClient extends TestHttpClient {
        private final RuntimeException failure;

        private FailingHttpClient(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static final class CapturingSubscriber implements Flow.Subscriber<ByteBuffer> {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<String> completed = new CompletableFuture<>();

        private static String capture(HttpRequest.BodyPublisher publisher) {
            CapturingSubscriber subscriber = new CapturingSubscriber();
            publisher.subscribe(subscriber);
            return subscriber.completed.join();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            output.writeBytes(bytes);
        }

        @Override
        public void onError(Throwable throwable) {
            completed.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            completed.complete(output.toString(StandardCharsets.UTF_8));
        }
    }

    private record FixedResponse(
            HttpRequest request,
            Supplier<Stream<String>> lines,
            int statusCode) implements HttpResponse<Stream<String>> {

        @Override
        public Optional<HttpResponse<Stream<String>>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of("Content-Type", List.of("text/event-stream")), (a, b) -> true);
        }

        @Override
        public Stream<String> body() {
            return lines.get();
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
