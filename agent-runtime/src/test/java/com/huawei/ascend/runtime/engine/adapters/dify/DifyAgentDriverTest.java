package com.huawei.ascend.runtime.engine.adapters.dify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.runtime.common.InvocationRequest;
import com.huawei.ascend.runtime.common.RunEvent;
import com.huawei.ascend.runtime.common.RunEventType;
import com.huawei.ascend.runtime.engine.RunCoordinator;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** R4.2: Dify remote adapter — SSE stream maps to the neutral RunEvent stream via RunCoordinator. */
class DifyAgentDriverTest {

    @Test
    void streamsDifyChatSseAsRunEvents() throws Exception {
        String sse = String.join("\n",
                "data: {\"event\": \"message\", \"answer\": \"po\", \"conversation_id\": \"c1\"}",
                "",
                "data: {\"event\": \"message\", \"answer\": \"ng\", \"conversation_id\": \"c1\"}",
                "",
                "data: {\"event\": \"message_end\", \"conversation_id\": \"c1\"}",
                "");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat-messages", exchange -> {
            byte[] body = sse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            DifyAgentDriver driver =
                    new DifyAgentDriver("dify-flow", "http://127.0.0.1:" + port + "/v1", "test-key");
            RunCoordinator coordinator = new RunCoordinator(driver);
            coordinator.start();

            List<RunEvent> events = collect(
                    coordinator.stream(new InvocationRequest("req-1", "dify-flow", "sess-1", "hi")));

            assertEquals("dify", driver.frameworkId());
            assertTrue(events.stream().anyMatch(e -> e.kind() == RunEventType.ACCEPTED),
                    "first event should be ACCEPTED");
            assertTrue(events.stream().anyMatch(e -> e.kind() == RunEventType.CHUNK),
                    "streaming chunks should surface as CHUNK events");
            RunEvent terminal = events.get(events.size() - 1);
            assertEquals(RunEventType.COMPLETED, terminal.kind());
            assertEquals("pong", terminal.content());
        } finally {
            server.stop(0);
        }
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
        done.await(10, TimeUnit.SECONDS);
        return out;
    }
}
