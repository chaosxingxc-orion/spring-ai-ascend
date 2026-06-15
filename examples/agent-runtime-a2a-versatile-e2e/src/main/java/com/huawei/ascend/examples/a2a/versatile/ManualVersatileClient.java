package com.huawei.ascend.examples.a2a.versatile;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Manual console client for testing the versatile adapter.
 *
 * <h3>Usage</h3>
 * <ol>
 *   <li>Start the runtime</li>
 *   <li>Run this main class</li>
 *   <li>Paste the URL line once, then repeatedly paste only the JSON body</li>
 *   <li>Commands: {@code url} to change URL, {@code quit} to exit</li>
 * </ol>
 */
public final class ManualVersatileClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private ManualVersatileClient() {
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        try (Scanner scanner = new Scanner(System.in)) {

            System.out.print("Runtime URL [http://localhost:8080]: ");
            String base = scanner.nextLine().trim();
            if (base.isEmpty()) base = "http://localhost:8080";

            AgentCard card = A2ACardResolver.builder().baseUrl(base).build().getAgentCard();
            System.out.println("Connected: " + card.name() + " | streaming=" + card.capabilities().streaming());

            // ── URL line — entered once ──
            System.out.print("Paste URL line> ");
            String urlLine = scanner.nextLine().trim();
            while (urlLine.isEmpty()) {
                System.out.print("Paste URL line> ");
                urlLine = scanner.nextLine().trim();
            }

            String conversationId = extractConversationId(urlLine);
            if (conversationId.isEmpty()) {
                conversationId = "manual-" + UUID.randomUUID().toString().substring(0, 8);
            }
            System.out.println("  conversation_id: " + conversationId);
            System.out.println();

            while (true) {
                // ── Accept JSON body only ──
                System.out.print("body> ");
                String bodyLine = scanner.nextLine().trim();

                if (bodyLine.isEmpty()) continue;
                if ("quit".equalsIgnoreCase(bodyLine)) break;
                if ("url".equalsIgnoreCase(bodyLine)) {
                    System.out.print("Paste URL line> ");
                    urlLine = scanner.nextLine().trim();
                    if (!urlLine.isEmpty()) {
                        conversationId = extractConversationId(urlLine);
                        if (conversationId.isEmpty()) {
                            conversationId = "manual-" + UUID.randomUUID().toString().substring(0, 8);
                        }
                        System.out.println("  conversation_id: " + conversationId);
                    }
                    continue;
                }

                Map<String, Object> body = MAPPER.readValue(bodyLine, Map.class);
                Map<String, Object> inputs = (Map<String, Object>) body.getOrDefault("inputs", Map.of());
                String query = String.valueOf(inputs.getOrDefault("query", ""));

                Map<String, Object> metadata = new LinkedHashMap<>();
                inputs.forEach((key, value) -> {
                    if (!"query".equals(key)) metadata.put(key, value);
                });

                // ── Stream ──
                JSONRPCTransport transport = new JSONRPCTransport(card);
                CountDownLatch completed = new CountDownLatch(1);

                try {
                    transport.sendMessageStreaming(
                            MessageSendParams.builder()
                                    .message(Message.builder()
                                            .role(Message.Role.ROLE_USER)
                                            .messageId(UUID.randomUUID().toString())
                                            .contextId(conversationId)
                                            .metadata(metadata)
                                            .parts(List.of(new TextPart(query)))
                                            .build())
                                    .build(),
                            event -> {
                                printRaw(event);
                                if (event instanceof TaskStatusUpdateEvent s
                                        && s.status() != null && s.status().state() != null) {
                                    TaskState state = s.status().state();
                                    if (state == TaskState.TASK_STATE_COMPLETED
                                            || state == TaskState.TASK_STATE_FAILED) {
                                        completed.countDown();
                                    }
                                }
                            },
                            error -> completed.countDown(),
                            null);

                    completed.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
                } finally {
                    transport.close();
                }
                System.out.println();
            }
        }
    }

    // ── Raw output — same format as curl ──

    static void printRaw(StreamingEventKind event) {
        if (event instanceof TaskStatusUpdateEvent s && s.status() != null) {
            String text = textFromParts(
                    s.status().message() != null && s.status().message().parts() != null
                            ? s.status().message().parts() : List.of());
            if (!text.isEmpty()) System.out.println(text);
        } else if (event instanceof TaskArtifactUpdateEvent a && a.artifact() != null) {
            String text = textFromParts(a.artifact().parts());
            if (!text.isEmpty()) System.out.println(text);
        } else if (event instanceof Message msg) {
            String text = textFromParts(msg.parts());
            if (!text.isEmpty()) System.out.println(text);
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

    static String extractConversationId(String url) {
        String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        int idx = path.indexOf("/conversations/");
        if (idx < 0) return "";
        String after = path.substring(idx + "/conversations/".length());
        int end = after.indexOf('/');
        return end < 0 ? after : after.substring(0, end);
    }
}
