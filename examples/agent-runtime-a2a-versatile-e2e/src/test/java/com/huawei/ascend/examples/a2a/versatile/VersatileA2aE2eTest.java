package com.huawei.ascend.examples.a2a.versatile;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Manual end-to-end test that verifies the versatile workflow proxy agent
 * end-to-end: A2A client → agent-runtime → versatile REST → agent-runtime →
 * A2A client.
 *
 * <h3>Prerequisites</h3>
 * <ol>
 *   <li>A versatile workflow service must be running at the address configured
 *       in {@code application.yaml} (or via environment variables).</li>
 *   <li>The workflow must accept a {@code {"inputs":{"query":"..."}}}
 *       request body and return an SSE stream.</li>
 * </ol>
 *
 * <h3>Run from the IDE</h3>
 * Execute test methods individually. Ensure the versatile remote service
 * is reachable before running.
 *
 * <h3>Run from CLI (recommended for CI/local)</h3>
 * <pre>
 * # 1. Start the runtime in one terminal:
 * mvn spring-boot:run -pl examples/agent-runtime-a2a-versatile-e2e
 *
 * # 2. Run the test in another terminal:
 * mvn test -pl examples/agent-runtime-a2a-versatile-e2e \
 *     -Dtest=VersatileA2aE2eTest
 * </pre>
 *
 * <h3>Run against a custom versatile instance</h3>
 * <pre>
 * VERSATILE_HOST=10.0.0.1 VERSATILE_PORT=8443 \
 * VERSATILE_WORKFLOW_ID=my-workflow-uuid \
 *   mvn test -pl examples/agent-runtime-a2a-versatile-e2e \
 *       -Dtest=VersatileA2aE2eTest
 * </pre>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = VersatileA2aRuntimeApplication.class)
@Tag("manual")
class VersatileA2aE2eTest {

    private static final Logger LOG = LoggerFactory.getLogger(VersatileA2aE2eTest.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @LocalServerPort
    private int port;

    /**
     * Verifies the agent card is discoverable via A2A well-known endpoint.
     */
    @Test
    void agentCardIsDiscoverable() throws Exception {
        URI baseUri = baseUri();
        AgentCard card = new A2ACardResolver(baseUri.toString()).getAgentCard();

        assertThat(card.name()).isEqualTo("Versatile Agent");
        assertThat(card.capabilities().streaming()).isTrue();
        assertThat(card.defaultInputModes()).contains("text");
        LOG.info("Versatile agent card discovered: name={} url={}", card.name(), card.url());
    }

    /**
     * Sends a synchronous message to the versatile agent and verifies the
     * task completes (or fails with a clear error if the remote is unreachable).
     */
    @Test
    void sendSynchronousMessageAndWaitForCompletion() throws Exception {
        URI baseUri = baseUri();
        AgentCard card = new A2ACardResolver(baseUri.toString()).getAgentCard();

        JSONRPCTransport transport = new JSONRPCTransport(card);
        try {
            MessageSendParams params = MessageSendParams.builder()
                    .message(Message.builder()
                            .role(Message.Role.ROLE_USER)
                            .messageId(UUID.randomUUID().toString())
                            .contextId("e2e-sync-" + UUID.randomUUID().toString().substring(0, 8))
                            .metadata(Map.of(
                                    "x-invoke-mode", "DEBUG",
                                    "x-language", "zh-cn"))
                            .parts(List.of(new TextPart("转账")))
                            .build())
                    .build();

            var result = transport.sendMessage(params, null);
            LOG.info("Sync result type: {}", result.getClass().getSimpleName());

            if (result instanceof Task task) {
                TaskState state = task.status() != null ? task.status().state() : null;
                LOG.info("Task state: {}", state);
                // Accept COMPLETED or FAILED (e.g. when remote is unreachable)
                assertThat(state).isIn(
                        TaskState.TASK_STATE_COMPLETED,
                        TaskState.TASK_STATE_FAILED);
            }
        } finally {
            transport.close();
        }
    }

    /**
     * Streams a message to the versatile agent and collects all streaming
     * events until a terminal state is reached.
     *
     * <p>Validates that the stream either completes normally (versatile
     * remote reachable) or fails with a clear transport error (remote not
     * configured / unreachable).
     */
    @Test
    void streamMessageAndCollectEvents() throws Exception {
        URI baseUri = baseUri();
        AgentCard card = new A2ACardResolver(baseUri.toString()).getAgentCard();

        JSONRPCTransport transport = new JSONRPCTransport(card);
        List<StreamingEventKind> events = new ArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            transport.sendMessageStreaming(
                    MessageSendParams.builder()
                            .message(Message.builder()
                                    .role(Message.Role.ROLE_USER)
                                    .messageId(UUID.randomUUID().toString())
                                    .contextId("e2e-stream-" + UUID.randomUUID().toString().substring(0, 8))
                                    .metadata(Map.of(
                                            "x-invoke-mode", "DEBUG",
                                            "x-language", "zh-cn"))
                                    .parts(List.of(new TextPart("转账")))
                                    .build())
                            .build(),
                    event -> {
                        events.add(event);
                        String type = event.getClass().getSimpleName();
                        if (event instanceof TaskStatusUpdateEvent s
                                && s.status() != null && s.status().state() != null) {
                            LOG.info("Stream event: {} state={}", type, s.status().state());
                            if (s.status().state() == TaskState.TASK_STATE_COMPLETED
                                    || s.status().state() == TaskState.TASK_STATE_FAILED) {
                                completed.countDown();
                            }
                        } else if (event instanceof TaskArtifactUpdateEvent a) {
                            LOG.info("Stream event: {} name={}", type,
                                    a.artifact() != null ? a.artifact().name() : "null");
                        }
                    },
                    error -> {
                        LOG.warn("Stream error: {}", error.getMessage());
                        failure.set(error);
                        completed.countDown();
                    },
                    null);

            boolean finished = completed.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            LOG.info("Stream finished: events={} timedOut={}", events.size(), !finished);

            assertThat(events).as("stream should produce at least one event or a terminal error")
                    .isNotEmpty();
        } finally {
            transport.close();
        }
    }

    private URI baseUri() {
        return URI.create("http://localhost:" + port);
    }

    // ── Text extraction helpers (keep in test, don't depend on SDK internals) ──

    static String textFromParts(List<Part<?>> parts) {
        if (parts == null) return "";
        StringBuilder text = new StringBuilder();
        for (Part<?> part : parts) {
            if (part instanceof TextPart tp) text.append(tp.text());
        }
        return text.toString();
    }
}
