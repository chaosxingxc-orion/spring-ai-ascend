package com.huawei.ascend.runtime.engine.agentscope;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.io.ByteArrayOutputStream;
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
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

class AgentScopeRuntimeClientHandlerTest {

    @Test
    void runtimeClientHandlerPostsAgentScopeRequestAndMapsSseResponse() {
        CapturingHttpClient httpClient = new CapturingHttpClient();
        AgentScopeRuntimeClient client = new AgentScopeRuntimeClient(
                httpClient,
                new ObjectMapper(),
                new AgentScopeRuntimeClientProperties("http://agentscope-runtime.local", "/process"));
        AgentScopeRuntimeClientHandler handler = new AgentScopeRuntimeClientHandler("agentscope-rest", client);

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
        assertThat(httpClient.body).contains("\"session_id\":\"session\"");
        assertThat(httpClient.body).contains("\"user_id\":\"user\"");
        assertThat(httpClient.body).contains("\"stream\":true");
        assertThat(httpClient.body).contains("\"role\":\"user\"");
        assertThat(httpClient.body).contains("\"role\":\"assistant\"");
        assertThat(httpClient.body).doesNotContain("ROLE_USER", "ROLE_AGENT");
        assertThat(httpClient.body).contains("\"content\":[{");
        assertThat(httpClient.body).contains("\"type\":\"text\"");
        assertThat(httpClient.body).contains("\"text\":\"ping\"");
    }

    @Test
    void runtimeClientCombinesMultiLineSseDataBlocks() {
        CapturingHttpClient httpClient = new CapturingHttpClient(200, Stream.of(
                "data: {\"status\":\"in_progress\",",
                "data: \"text\":\"hello\"}",
                "",
                "data: {\"status\":\"completed\",\"output\":\"done\"}"));
        AgentScopeRuntimeClient client = new AgentScopeRuntimeClient(
                httpClient,
                new ObjectMapper(),
                new AgentScopeRuntimeClientProperties("http://agentscope-runtime.local", "/process"));
        AgentScopeRuntimeClientHandler handler = new AgentScopeRuntimeClientHandler("agentscope-rest", client);

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).extracting(AgentExecutionResult::type)
                .containsExactly(AgentExecutionResult.Type.OUTPUT, AgentExecutionResult.Type.COMPLETED);
        assertThat(results.get(0).outputContent()).isEqualTo("hello");
        assertThat(results.get(1).outputContent()).isEqualTo("done");
    }

    @Test
    void runtimeClientPreservesIoFailureMessage() {
        AgentScopeRuntimeClient client = new AgentScopeRuntimeClient(
                new FailingHttpClient(new IllegalStateException("connection refused")),
                new ObjectMapper(),
                new AgentScopeRuntimeClientProperties("http://agentscope-runtime.local", "/process"));
        AgentScopeRuntimeClientHandler handler = new AgentScopeRuntimeClientHandler("agentscope-rest", client);

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().type()).isEqualTo(AgentExecutionResult.Type.FAILED);
        assertThat(results.getFirst().errorCode()).isEqualTo("AGENTSCOPE_RUNTIME_IO");
        assertThat(results.getFirst().errorMessage()).contains("connection refused");
    }

    @Test
    void runtimeClientPreservesIoFailureMessageWithControlCharacters() {
        AgentScopeRuntimeClient client = new AgentScopeRuntimeClient(
                new FailingHttpClient(new IllegalStateException("connect failed\nssl alert \"bad\"")),
                new ObjectMapper(),
                new AgentScopeRuntimeClientProperties("http://agentscope-runtime.local", "/process"));
        AgentScopeRuntimeClientHandler handler = new AgentScopeRuntimeClientHandler("agentscope-rest", client);

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().type()).isEqualTo(AgentExecutionResult.Type.FAILED);
        assertThat(results.getFirst().errorCode()).isEqualTo("AGENTSCOPE_RUNTIME_IO");
        assertThat(results.getFirst().errorMessage()).contains("connect failed\nssl alert \"bad\"");
    }

    @Test
    void runtimeClientTreatsHttp599AsUpstreamHttpFailure() {
        CapturingHttpClient httpClient = new CapturingHttpClient(599, Stream.of("proxy timeout"));
        AgentScopeRuntimeClient client = new AgentScopeRuntimeClient(
                httpClient,
                new ObjectMapper(),
                new AgentScopeRuntimeClientProperties("http://agentscope-runtime.local", "/process"));
        AgentScopeRuntimeClientHandler handler = new AgentScopeRuntimeClientHandler("agentscope-rest", client);

        List<AgentExecutionResult> results = handler.resultAdapter().adapt(handler.execute(context())).toList();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().type()).isEqualTo(AgentExecutionResult.Type.FAILED);
        assertThat(results.getFirst().errorCode()).isEqualTo("AGENTSCOPE_RUNTIME_HTTP_599");
        assertThat(results.getFirst().errorMessage()).isEqualTo("AgentScope runtime returned HTTP 599");
    }

    private static AgentExecutionContext context() {
        RuntimeIdentity scope = new RuntimeIdentity("tenant", "user", "session", "task", "agentscope-rest");
        return new AgentExecutionContext(
                scope,
                "USER_MESSAGE",
                List.of(message(Message.Role.ROLE_USER, "ping"), message(Message.Role.ROLE_AGENT, "pong")),
                Map.of());
    }

    private static Message message(Message.Role role, String text) {
        return Message.builder()
                .role(role)
                .parts(List.<Part<?>>of(new TextPart(text)))
                .build();
    }

    private static final class CapturingHttpClient extends HttpClient {
        private final int responseStatusCode;
        private final Stream<String> responseBody;
        private HttpRequest request;
        private String body;

        private CapturingHttpClient() {
            this(200, Stream.of(
                    "data: {\"status\":\"in_progress\",\"type\":\"text\",\"text\":\"hel\"}",
                    "",
                    "data: {\"status\":\"completed\",\"output\":\"hello\"}"));
        }

        private CapturingHttpClient(int responseStatusCode, Stream<String> responseBody) {
            this.responseStatusCode = responseStatusCode;
            this.responseBody = responseBody;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return sendAsync(request, responseBodyHandler).join();
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

        private static String body(HttpRequest request) {
            return request.bodyPublisher()
                    .map(CapturingSubscriber::capture)
                    .orElse("");
        }
    }

    private static final class FailingHttpClient extends HttpClient {
        private final RuntimeException failure;

        private FailingHttpClient(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw failure;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw failure;
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

    private record FixedResponse(HttpRequest request, Stream<String> body, int statusCode)
            implements HttpResponse<Stream<String>> {

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
