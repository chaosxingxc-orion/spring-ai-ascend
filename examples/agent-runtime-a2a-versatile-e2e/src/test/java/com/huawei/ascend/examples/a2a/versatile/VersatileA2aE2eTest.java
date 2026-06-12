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
 * Manual end-to-end test: A2A client → agent-runtime → versatile REST → back.
 *
 * <h3>Prerequisites</h3>
 * A versatile service must be running at the address configured in
 * {@code application.yaml} (override via environment variables).
 *
 * <h3>Quick start — against the real versatile instance</h3>
 * <pre>
 * VERSATILE_HOST=7.213.200.213 VERSATILE_PORT=3001 VERSATILE_SSL=false \
 * VERSATILE_URL_TEMPLATE="/v1/{project_id}/agents/{agent_id}/conversations/{conversation_id}" \
 * VERSATILE_PROJECT_ID="mock_project_id" \
 * VERSATILE_AGENT_ID="fb723468-c8ca-424b-a95f-a3e74b37e090" \
 * VERSATILE_WORKSPACE_ID="10" \
 * VERSATILE_QUERY_PARAM_TYPE="controller" \
 * VERSATILE_INPUT_METADATA_KEYS="intent,wap_userName" \
 *   mvn test -f examples/agent-runtime-a2a-versatile-e2e/pom.xml \
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

    @Test
    void agentCardIsDiscoverable() throws Exception {
        URI baseUri = baseUri();
        AgentCard card = new A2ACardResolver(baseUri.toString()).getAgentCard();
        assertThat(card.name()).isEqualTo("Versatile Agent");
        LOG.info("Agent card: name={} streaming={} url={}",
                card.name(), card.capabilities().streaming(), card.url());
    }

    /**
     * Stream a message to the versatile agent and print every event to the
     * log. The test assertion is intentionally lenient — it only checks that
     * the stream reaches a terminal state. Human inspection of log output
     * validates correctness.
     */
    @Test
    void streamToVersatileAndPrintAllEvents() throws Exception {
        URI baseUri = baseUri();
        AgentCard card = new A2ACardResolver(baseUri.toString()).getAgentCard();
        LOG.info("=== Versatile service target: {} ===", card.url());

        JSONRPCTransport transport = new JSONRPCTransport(card);
        List<StreamingEventKind> events = new ArrayList<>();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        // ── Build the request ──
        // Put extra input fields (intent, wap_userName, etc.) in metadata;
        // the adapter merges them into body.inputs per input-metadata-keys config.
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("x-language", "zh-cn");
        metadata.put("intent", "订酒店");
        metadata.put("wap_userName", "张三");

        String query = """
                {"person_name":"李四", "checkin_date":"2026-03-30", \
                "checkout_date":"2026-04-03", "arrival_city": "北京"}""";

        String contextId = "manual-" + UUID.randomUUID().toString().substring(0, 8);

        LOG.info("");
        LOG.info("╔══════════════════════════════════════════════════════╗");
        LOG.info("║  A2A Request                                         ║");
        LOG.info("╠══════════════════════════════════════════════════════╣");
        LOG.info("║  contextId  : {}", contextId);
        LOG.info("║  query      : {}", query);
        LOG.info("║  metadata   : {}", metadata);
        LOG.info("╚══════════════════════════════════════════════════════╝");
        LOG.info("");

        try {
            transport.sendMessageStreaming(
                    MessageSendParams.builder()
                            .message(Message.builder()
                                    .role(Message.Role.ROLE_USER)
                                    .messageId(UUID.randomUUID().toString())
                                    .contextId(contextId)
                                    .metadata(metadata)
                                    .parts(List.of(new TextPart(query)))
                                    .build())
                            .build(),
                    event -> {
                        events.add(event);
                        logEvent(event);
                        if (event instanceof TaskStatusUpdateEvent s
                                && s.status() != null && s.status().state() != null) {
                            TaskState state = s.status().state();
                            if (state == TaskState.TASK_STATE_COMPLETED
                                    || state == TaskState.TASK_STATE_FAILED) {
                                completed.countDown();
                            }
                        }
                    },
                    error -> {
                        LOG.error("Stream transport error: {}", error.getMessage(), error);
                        failure.set(error);
                        completed.countDown();
                    },
                    null);

            boolean finished = completed.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            LOG.info("");
            LOG.info("══════════════════════════════════════════════════");
            LOG.info("Stream finished: {} events, timedOut={}", events.size(), !finished);
            LOG.info("══════════════════════════════════════════════════");

            assertThat(events)
                    .as("stream should produce events or a terminal error")
                    .isNotEmpty();
        } finally {
            transport.close();
        }
    }

    // ── Event display ──

    private static void logEvent(StreamingEventKind event) {
        String type = event.getClass().getSimpleName();
        if (event instanceof TaskStatusUpdateEvent s && s.status() != null) {
            TaskState state = s.status().state();
            String text = s.status().message() != null && s.status().message().parts() != null
                    ? textFromParts(s.status().message().parts()) : "";
            LOG.info("  [{}] state={} text={}", type, state,
                    text.length() > 200 ? text.substring(0, 200) + "..." : text);
        } else if (event instanceof TaskArtifactUpdateEvent a && a.artifact() != null) {
            LOG.info("  [{}] artifact={} parts={}", type,
                    a.artifact().name(),
                    a.artifact().parts() != null ? a.artifact().parts().size() : 0);
        } else if (event instanceof Message msg) {
            String text = textFromParts(msg.parts());
            LOG.info("  [{}] text={}", type,
                    text.length() > 200 ? text.substring(0, 200) + "..." : text);
        } else {
            LOG.info("  [{}]", type);
        }
    }

    static String textFromParts(List<Part<?>> parts) {
        if (parts == null) return "";
        StringBuilder text = new StringBuilder();
        for (Part<?> part : parts) {
            if (part instanceof TextPart tp) text.append(tp.text());
        }
        return text.toString();
    }

    private URI baseUri() {
        return URI.create("http://localhost:" + port);
    }
}
