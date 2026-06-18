package com.huawei.ascend.examples.a2a.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * E2E test for the Questioner Workflow Agent via A2A.
 *
 * <p>Verifies the full interrupt/resume round-trip:
 * <ol>
 *   <li>Send a message → Workflow starts, Questioner suspends → INPUT_REQUIRED</li>
 *   <li>Send resume with user answer → Workflow resumes → COMPLETED</li>
 *   <li>Result contains "回答正确"</li>
 * </ol>
 *
 * <p>This simulates what a main ReActAgent would do when calling this
 * Workflow Agent as a remote A2A tool.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = QuestionerWorkflowApplication.class)
@Tag("manual")
@EnabledIfEnvironmentVariable(named = "SAA_LLM_API_KEY", matches = ".+")
class QuestionerWorkflowE2eTest {

    @LocalServerPort
    private int port;

    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    @BeforeAll
    static void checkModelReady() {
        // Warm-up: nothing needed — Questioner doesn't call LLM
    }

    @Test
    @DisplayName("Workflow interrupt/resume via A2A round-trip")
    void shouldInterruptAndResumeViaA2a() throws Exception {
        URI baseUri = URI.create("http://localhost:" + port);
        String userId = "e2e-user";
        String sessionId = "e2e-session-" + UUID.randomUUID().toString().substring(0, 8);

        // ── Phase 1: Send message, expect INPUT_REQUIRED ────────────
        A2aClient client = new A2aClient(baseUri, TIMEOUT);
        AgentCard card = client.agentCard();
        assertThat(card.name()).isEqualTo(QuestionerWorkflowConfiguration.AGENT_ID);

        List<StreamingEventKind> events1 = client.streamMessage(
                userId, QuestionerWorkflowConfiguration.AGENT_ID, sessionId, "启动");

        // Should contain INPUT_REQUIRED status
        String taskId = findTaskId(events1);
        assertThat(taskId).as("taskId from INPUT_REQUIRED").isNotNull();

        boolean interrupted = hasState(events1, TaskState.TASK_STATE_INPUT_REQUIRED);
        assertThat(interrupted).as("workflow should interrupt for user input").isTrue();

        String prompt = textFrom(events1);
        assertThat(prompt).contains(QuestionerWorkflowConfiguration.QUESTION);

        // ── Phase 2: Send resume with answer, expect COMPLETED ──────
        List<StreamingEventKind> events2 = client.streamResume(
                userId, QuestionerWorkflowConfiguration.AGENT_ID, sessionId, taskId, "2");

        boolean completed = hasState(events2, TaskState.TASK_STATE_COMPLETED);
        assertThat(completed).as("workflow should complete after resume").isTrue();

        String resultText = textFrom(events2);
        assertThat(resultText)
                .as("result should echo answer and confirm")
                .contains("你的答案是2")
                .contains("回答正确");
    }

    // ── helpers ────────────────────────────────────────────────────

    private static String textFrom(List<StreamingEventKind> events) {
        StringBuilder sb = new StringBuilder();
        for (StreamingEventKind event : events) {
            if (event instanceof Message msg
                    && (msg.metadata() == null
                        || !Boolean.TRUE.equals(msg.metadata().get("accepted")))) {
                for (var part : msg.parts()) {
                    if (part instanceof TextPart tp && !tp.text().isBlank()) {
                        sb.append(tp.text());
                    }
                }
            } else if (event instanceof TaskStatusUpdateEvent ts
                    && ts.status() != null && ts.status().message() != null) {
                for (var part : ts.status().message().parts()) {
                    if (part instanceof TextPart tp && !tp.text().isBlank()) {
                        sb.append(tp.text());
                    }
                }
            } else if (event instanceof TaskArtifactUpdateEvent ta
                    && ta.artifact() != null) {
                for (var part : ta.artifact().parts()) {
                    if (part instanceof TextPart tp && !tp.text().isBlank()) {
                        sb.append(tp.text());
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String findTaskId(List<StreamingEventKind> events) {
        for (StreamingEventKind event : events) {
            if (event instanceof TaskStatusUpdateEvent ts
                    && ts.status() != null
                    && ts.status().state() == TaskState.TASK_STATE_INPUT_REQUIRED) {
                return ts.taskId();
            }
        }
        return null;
    }

    private static boolean hasState(List<StreamingEventKind> events, TaskState state) {
        for (StreamingEventKind event : events) {
            if (event instanceof TaskStatusUpdateEvent ts
                    && ts.status() != null
                    && ts.status().state() == state) {
                return true;
            }
        }
        return false;
    }

    // ── Inline minimal A2A client ──────────────────────────────────

    static final class A2aClient {
        private final URI baseUri;
        private final Duration timeout;

        A2aClient(URI baseUri, Duration timeout) {
            this.baseUri = baseUri;
            this.timeout = timeout;
        }

        AgentCard agentCard() throws Exception {
            return A2ACardResolver.builder()
                    .baseUrl(baseUri.toString()).build().getAgentCard();
        }

        List<StreamingEventKind> streamMessage(
                String userId, String agentId, String sessionId, String text)
                throws Exception {
            return stream(userId, agentId, sessionId, null, text);
        }

        List<StreamingEventKind> streamResume(
                String userId, String agentId, String sessionId,
                String taskId, String text) throws Exception {
            return stream(userId, agentId, sessionId, taskId, text);
        }

        private List<StreamingEventKind> stream(
                String userId, String agentId, String sessionId,
                String taskId, String text) throws Exception {
            AgentCard card = agentCard();
            List<StreamingEventKind> events = new ArrayList<>();
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean sawTerminal = new AtomicBoolean(false);
            JSONRPCTransport transport = new JSONRPCTransport(card);
            try {
                transport.sendMessageStreaming(
                        buildParams(userId, agentId, sessionId, taskId, text),
                        event -> {
                            events.add(event);
                            if (isTerminal(event)) {
                                sawTerminal.set(true);
                                completed.countDown();
                            }
                        },
                        error -> {
                            if (isFailureError(error, sawTerminal.get())) {
                                failure.set(error);
                            }
                            completed.countDown();
                        },
                        new ClientCallContext(Map.of(), Map.of()));
                if (!completed.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException(
                            "A2A stream timed out after " + timeout.toMillis() + "ms");
                }
            } finally {
                transport.close();
            }
            if (failure.get() != null) {
                throw new IllegalStateException("A2A stream failed", failure.get());
            }
            return List.copyOf(events);
        }

        private static MessageSendParams buildParams(
                String userId, String agentId, String sessionId,
                String taskId, String text) {
            Message message = Message.builder()
                    .role(Message.Role.ROLE_USER)
                    .messageId(UUID.randomUUID().toString())
                    .taskId(taskId)
                    .contextId(sessionId)
                    .metadata(Map.of(
                            "userId", userId,
                            "agentId", agentId,
                            "sessionId", sessionId))
                    .parts(List.of(new TextPart(text)))
                    .build();
            return MessageSendParams.builder().message(message).build();
        }

        private static boolean isTerminal(StreamingEventKind event) {
            if (event instanceof TaskStatusUpdateEvent ts
                    && ts.status() != null && ts.status().state() != null) {
                TaskState state = ts.status().state();
                return state == TaskState.TASK_STATE_COMPLETED
                        || state == TaskState.TASK_STATE_FAILED
                        || state == TaskState.TASK_STATE_CANCELED
                        || state == TaskState.TASK_STATE_REJECTED;
            }
            if (event instanceof Message msg && msg.metadata() != null) {
                String rs = String.valueOf(msg.metadata().get("runStatus"));
                return "completed".equals(rs) || "failed".equals(rs)
                        || "canceled".equals(rs) || "rejected".equals(rs);
            }
            return false;
        }

        private static boolean isFailureError(Throwable error, boolean sawTerminal) {
            return !(causedByCancellation(error) && sawTerminal);
        }

        private static boolean causedByCancellation(Throwable error) {
            for (Throwable t = error; t != null; t = t.getCause()) {
                if (t instanceof java.util.concurrent.CancellationException) {
                    return true;
                }
            }
            return false;
        }
    }
}
