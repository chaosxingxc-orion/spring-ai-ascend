package com.huawei.ascend.runtime.engine.agentscope;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
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
import org.junit.jupiter.api.Test;

class AgentScopeRuntimeClientHandlerTest {

    @Test
    void runtimeClientHandlerPostsAgentScopeRequestAndMapsSseResponse() throws Exception {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        AgentScopeRuntimeClientHandler handler = handler(httpClient);

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.OUTPUT, AgentExecutionResult.Type.COMPLETED);
        assertThat(results.get(0).outputContent()).isEqualTo("hel");
        assertThat(results.get(1).outputContent()).isEqualTo("hello");
        assertThat(httpClient.request.uri()).isEqualTo(URI.create("http://agentscope-runtime.local/process"));
        assertThat(httpClient.request.headers().firstValue("Accept")).contains("text/event-stream");
        assertThat(httpClient.request.headers().firstValue("X-Tenant-Id")).contains("tenant");
        assertThat(httpClient.request.headers().firstValue("X-Agent-Id")).contains("agentscope-rest");
        assertThat(httpClient.request.headers().firstValue("X-Task-Id")).contains("task");

        JsonNode body = new ObjectMapper().readTree(httpClient.body);
        assertThat(body.get("session_id").asText()).isEqualTo("session");
        assertThat(body.get("user_id").asText()).isEqualTo("user");
        assertThat(body.get("stream").asBoolean()).isTrue();
        JsonNode input = body.get("input");
        assertThat(input).hasSize(2);
        assertThat(input.get(0).get("role").asText()).isEqualTo("user");
        assertThat(input.get(0).get("content").get(0).get("type").asText()).isEqualTo("text");
        assertThat(input.get(0).get("content").get(0).get("text").asText()).isEqualTo("ping");
        assertThat(input.get(1).get("role").asText()).isEqualTo("assistant");
        assertThat(input.get(1).get("content").get(0).get("text").asText()).isEqualTo("pong");
    }

    @Test
    void runtimeClientCombinesMultiLineSseDataBlocks() {
        CapturingHttpClient httpClient = new CapturingHttpClient(200, List.of(
                "data: {\"status\":\"in_progress\",",
                "data: \"text\":\"hello\"}",
                "",
                "data: {\"status\":\"completed\",\"output\":\"done\"}"));
        AgentScopeRuntimeClientHandler handler = handler(httpClient);

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.OUTPUT, AgentExecutionResult.Type.COMPLETED);
        assertThat(results.get(0).outputContent()).isEqualTo("hello");
        assertThat(results.get(1).outputContent()).isEqualTo("done");
    }

    @Test
    void runtimeClientSplitsBareNewlineFramedEvents() {
        CapturingHttpClient httpClient = new CapturingHttpClient(200, List.of(
                "data: {\"status\":\"in_progress\",\"type\":\"text\",\"text\":\"a\"}",
                "data: {\"status\":\"completed\",\"output\":\"done\"}"));
        AgentScopeRuntimeClientHandler handler = handler(httpClient);

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.OUTPUT, AgentExecutionResult.Type.COMPLETED);
        assertThat(results.get(0).outputContent()).isEqualTo("a");
        assertThat(results.get(1).outputContent()).isEqualTo("done");
    }

    @Test
    void runtimeClientSkipsNullDataEvents() {
        CapturingHttpClient httpClient = new CapturingHttpClient(200, List.of(
                "data: null",
                "",
                "data: {\"status\":\"completed\",\"output\":\"done\"}"));
        AgentScopeRuntimeClientHandler handler = handler(httpClient);

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().type()).isEqualTo(AgentExecutionResult.Type.COMPLETED);
        assertThat(results.getFirst().outputContent()).isEqualTo("done");
    }

    @Test
    void runtimeClientMapsMidStreamFailureToStructuredError() {
        Supplier<Stream<String>> failingBody = () -> Stream.of(
                        "data: {\"status\":\"in_progress\",\"type\":\"text\",\"text\":\"partial\"}",
                        "",
                        "FAIL")
                .map(line -> {
                    if ("FAIL".equals(line)) {
                        throw new UncheckedIOException(new IOException("connection reset by peer"));
                    }
                    return line;
                });
        CapturingHttpClient httpClient = new CapturingHttpClient(200, failingBody);
        AgentScopeRuntimeClientHandler handler = handler(httpClient);

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.OUTPUT, AgentExecutionResult.Type.FAILED);
        assertThat(results.get(0).outputContent()).isEqualTo("partial");
        assertThat(results.get(1).errorCode()).isEqualTo("AGENTSCOPE_RUNTIME_IO");
        assertThat(results.get(1).errorMessage()).isEqualTo("connection reset by peer");
    }

    @Test
    void runtimeClientPreservesIoFailureMessage() {
        AgentScopeRuntimeClientHandler handler =
                handler(new FailingHttpClient(new IllegalStateException("connection refused")));

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().type()).isEqualTo(AgentExecutionResult.Type.FAILED);
        assertThat(results.getFirst().errorCode()).isEqualTo("AGENTSCOPE_RUNTIME_IO");
        assertThat(results.getFirst().errorMessage()).contains("connection refused");
    }

    @Test
    void runtimeClientPreservesIoFailureMessageWithControlCharacters() {
        AgentScopeRuntimeClientHandler handler =
                handler(new FailingHttpClient(new IllegalStateException("connect failed\nssl alert \"bad\"")));

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().type()).isEqualTo(AgentExecutionResult.Type.FAILED);
        assertThat(results.getFirst().errorCode()).isEqualTo("AGENTSCOPE_RUNTIME_IO");
        assertThat(results.getFirst().errorMessage()).contains("connect failed\nssl alert \"bad\"");
    }

    @Test
    void runtimeClientTreatsHttp599AsUpstreamHttpFailure() {
        CapturingHttpClient httpClient = new CapturingHttpClient(599, List.of("proxy timeout"));
        AgentScopeRuntimeClientHandler handler = handler(httpClient);

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().type()).isEqualTo(AgentExecutionResult.Type.FAILED);
        assertThat(results.getFirst().errorCode()).isEqualTo("AGENTSCOPE_RUNTIME_HTTP_599");
        assertThat(results.getFirst().errorMessage()).isEqualTo("AgentScope runtime returned HTTP 599");
    }

    /**
     * A data block that partially parses keeps the decoded events but must
     * surface the corrupt remainder as a structured failure — the dropped tail
     * may have been the terminal, and silence would drain into a false
     * COMPLETED.
     */
    @Test
    void runtimeClientSurfacesPartiallyParsedDataBlockAsStructuredFailure() {
        CapturingHttpClient httpClient = new CapturingHttpClient(200, List.of(
                "data: {\"status\":\"in_progress\",\"type\":\"text\",\"text\":\"partial\"}",
                "data: {\"status\":\"failed\",\"error\":"));
        AgentScopeRuntimeClientHandler handler = handler(httpClient);

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.OUTPUT, AgentExecutionResult.Type.FAILED);
        assertThat(results.get(0).outputContent()).isEqualTo("partial");
        assertThat(results.get(1).errorCode()).isEqualTo("AGENTSCOPE_RUNTIME_PARSE");
    }

    /** A hung upstream must not wedge the task forever: time-to-headers is bounded. */
    @Test
    void runtimeClientRequestCarriesHeaderTimeout() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        AgentScopeRuntimeClientHandler handler = handler(httpClient);

        handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(httpClient.request.timeout()).contains(Duration.ofSeconds(60));
    }

    /**
     * stop() must release the SSE transport when the client owns it (created it
     * itself); a borrowed transport belongs to its injector and survives.
     */
    @Test
    void stopClosesClientOwnedHttpTransport() {
        java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        HttpClient recording = new TestHttpClient() {
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
        AgentScopeRuntimeClient client = new AgentScopeRuntimeClient(
                recording,
                new ObjectMapper(),
                new AgentScopeRuntimeClientProperties("http://agentscope-runtime.local", "/process"),
                true);
        AgentScopeRuntimeClientHandler handler = new AgentScopeRuntimeClientHandler("agentscope-rest", client);

        handler.stop();

        assertThat(closed).isTrue();
    }

    private static AgentScopeRuntimeClientHandler handler(HttpClient httpClient) {
        AgentScopeRuntimeClient client = new AgentScopeRuntimeClient(
                httpClient,
                new ObjectMapper(),
                new AgentScopeRuntimeClientProperties("http://agentscope-runtime.local", "/process"));
        return new AgentScopeRuntimeClientHandler("agentscope-rest", client);
    }

    private static AgentExecutionContext context() {
        RuntimeIdentity scope = new RuntimeIdentity("tenant", "user", "session", "task", "agentscope-rest");
        return new AgentExecutionContext(
                scope,
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("ping"), RuntimeMessage.agent("pong")),
                Map.of());
    }

    /** Shared no-op scaffolding for the HttpClient fakes below. */
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

        private CapturingHttpClient() {
            this(200, List.of(
                    "data: {\"status\":\"in_progress\",\"type\":\"text\",\"text\":\"hel\"}",
                    "",
                    "data: {\"status\":\"completed\",\"output\":\"hello\"}"));
        }

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
            // Real HttpClient surfaces connection failures through the future, so
            // production join() sees a CompletionException wrapping this cause.
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static final class CapturingSubscriber implements Flow.Subscriber<ByteBuffer> {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<String> completed = new CompletableFuture<>();

        static String capture(HttpRequest.BodyPublisher publisher) {
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

    private record FixedResponse(HttpRequest request, Supplier<Stream<String>> bodySupplier, int statusCode)
            implements HttpResponse<Stream<String>> {

        @Override
        public Stream<String> body() {
            return bodySupplier.get();
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public Optional<HttpResponse<Stream<String>>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of("Content-Type", List.of("text/event-stream")), (name, value) -> true);
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
