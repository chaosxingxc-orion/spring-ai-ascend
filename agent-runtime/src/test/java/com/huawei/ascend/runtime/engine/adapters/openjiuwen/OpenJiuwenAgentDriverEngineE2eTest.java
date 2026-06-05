package com.huawei.ascend.runtime.engine.adapters.openjiuwen;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.huawei.ascend.runtime.common.InvocationRequest;
import com.huawei.ascend.runtime.common.RunEvent;
import com.huawei.ascend.runtime.engine.RunCoordinator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Rebuilt-engine-core end-to-end: a real openJiuwen ReAct agent, wrapped by the NEW neutral
 * seam ({@link OpenJiuwenAgentDriver} → {@link RunCoordinator} → {@link OpenJiuwenOutputConverter}),
 * answers {@code ping} → {@code pong} from a live OpenAI-compatible LLM (Ollama/gemma4 by
 * default). Skips automatically when no LLM endpoint is reachable.
 *
 * <p>This proves the rebuilt execution core (RunCoordinator + AgentDriver + OutputConverter,
 * reactive {@code RunEvent} stream) works with a real framework and a real model — independent
 * of the A2A transport, which W4 layers on top.
 */
class OpenJiuwenAgentDriverEngineE2eTest {

    private static final String API_BASE = env("SAA_SAMPLE_OPENJIUWEN_API_BASE", "http://127.0.0.1:11434/v1");
    private static final String MODEL = env("SAA_SAMPLE_LLM_MODEL", "gemma4:latest");
    private static final String PROVIDER = env("SAA_SAMPLE_OPENJIUWEN_MODEL_PROVIDER", "openai");
    private static final String API_KEY = env("SAA_SAMPLE_LLM_API_KEY", "ollama");
    private static final String SYSTEM_PROMPT = """
            You are a concise assistant.
            If the user's message is exactly ping, reply exactly pong and nothing else.
            For all other messages, reply to the user's message directly and briefly.
            """;

    @Test
    void pingPongThroughRebuiltEngineCore() throws Exception {
        assumeTrue(llmReachable(), "LLM endpoint not reachable at " + API_BASE + " — skipping live engine e2e");

        OpenJiuwenAgentDriver driver = new OpenJiuwenAgentDriver(
                "openjiuwen-react-agent", SYSTEM_PROMPT, PROVIDER, API_KEY, API_BASE, MODEL, false);
        RunCoordinator coordinator = new RunCoordinator(driver);
        coordinator.start();

        List<RunEvent> events = collect(coordinator.stream(
                new InvocationRequest("req-1", "openjiuwen-react-agent", "sess-1", "ping")));

        assertTrue(events.size() >= 2, "expected at least ACCEPTED + a terminal event, got " + events);
        assertTrue(events.get(events.size() - 1).terminal(), "stream must end with a terminal event, got " + events);
        String joined = events.stream()
                .map(e -> e.content() == null ? "" : e.content())
                .reduce("", (a, b) -> a + b)
                .toLowerCase();
        assertTrue(joined.contains("pong"),
                "expected 'pong' in the neutral RunEvent stream, got " + events);
    }

    private static boolean llmReachable() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_BASE + "/models"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static List<RunEvent> collect(Flow.Publisher<RunEvent> publisher) throws InterruptedException {
        List<RunEvent> out = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<RunEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(RunEvent item) {
                out.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        assertTrue(done.await(90, TimeUnit.SECONDS), "stream did not complete in time");
        return out;
    }
}
