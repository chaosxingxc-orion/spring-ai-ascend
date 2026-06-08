package com.huawei.ascend.runtime.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.app.RuntimeWiringConfiguration;
import com.huawei.ascend.runtime.access.config.AccessLayerConfiguration;
import com.huawei.ascend.runtime.access.core.AccessSubmissionService;
import com.huawei.ascend.runtime.access.protocol.a2a.egress.A2aOutput;
import com.huawei.ascend.runtime.access.protocol.a2a.egress.A2aOutputHandle;
import com.huawei.ascend.runtime.access.protocol.a2a.egress.A2aOutputRegistry;
import com.huawei.ascend.runtime.access.protocol.a2a.jsonrpc.A2aJsonRpcHandler;
import com.huawei.ascend.runtime.engine.config.EngineAutoConfiguration;
import com.huawei.ascend.runtime.engine.handler.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.model.InterruptType;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.queue.config.QueueAutoConfiguration;
import com.huawei.ascend.runtime.common.AgentRequest;
import com.huawei.ascend.runtime.common.Message;
import com.huawei.ascend.runtime.session.api.SessionManager;
import com.huawei.ascend.runtime.session.config.SessionManageConfiguration;
import com.huawei.ascend.runtime.control.TaskControlService;
import com.huawei.ascend.runtime.control.TaskState;
import com.huawei.ascend.runtime.control.config.TaskControlAutoConfiguration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test from the access layer's perspective: an A2A request enters,
 * flows access -> task-centric-control -> engine -> fake agent, and the reply
 * comes back out through the access layer's A2A output channel.
 */
@SpringBootTest(classes = AgentServiceEndToEndIT.TestRuntime.class)
class AgentServiceEndToEndIT {

    private static final String TENANT = "tenant-e2e";
    private static final String AGENT = "echo-agent";
    private static final String FAILING_AGENT = "boom-agent";
    private static final String INTERRUPTING_AGENT = "interrupt-agent";
    private static final String RESULT_FAILING_AGENT = "failed-result-agent";

    @Autowired
    private A2aJsonRpcHandler a2aJsonRpcHandler;

    @Autowired
    private A2aOutputRegistry outputRegistry;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessSubmissionService submissionService;

    @Autowired
    private TaskControlService taskControlService;

    @Test
    void a2aRequestRunsThroughTheStackAndRepliesBack() {
        JsonNode accepted = send("session-1", "hello world");

        assertThat(accepted.path("metadata").path("accepted").asBoolean()).isTrue();
        assertThat(accepted.path("taskId").asText()).isNotBlank();
        assertThat(accepted.path("metadata").path("tenantId").asText()).isEqualTo(TENANT);

        A2aOutputHandle handle = new A2aOutputHandle(TENANT, "session-1", accepted.path("taskId").asText());
        List<A2aOutput> outputs = awaitOutputs(handle);

        assertThat(outputs).isNotEmpty();
        assertThat(outputs).anyMatch(o -> "Message".equals(o.kind()));
        assertThat(outputs.get(outputs.size() - 1).terminal()).isTrue();
        assertThat(outputs).anyMatch(o -> String.valueOf(o.body()).contains("hello world"));
        assertThat(sessionManager.get(TENANT, "session-1")).hasValueSatisfying(session ->
                assertThat(session.currentUserInput()).anyMatch(message -> "hello world".equals(message.text())));
    }

    @Test
    void aThrowingAgentStillRepliesWithATerminalError() {
        JsonNode accepted = send(FAILING_AGENT, "session-err", "trigger failure");
        assertThat(accepted.path("metadata").path("accepted").asBoolean()).isTrue();

        A2aOutputHandle handle = new A2aOutputHandle(TENANT, "session-err", accepted.path("taskId").asText());
        List<A2aOutput> outputs = awaitOutputs(handle);

        assertThat(outputs).isNotEmpty();
        assertThat(outputs.get(outputs.size() - 1).terminal()).isTrue();
        assertThat(outputs).anyMatch(o -> "error".equals(o.kind()));
    }

    @Test
    void a2aRequestCanReadIdentityFromParamsMetadata() {
        JsonNode accepted = sendWithParamsMetadataOnly("session-params", "params metadata");

        assertThat(accepted.path("metadata").path("accepted").asBoolean()).isTrue();
        assertThat(accepted.path("metadata").path("tenantId").asText()).isEqualTo(TENANT);

        A2aOutputHandle handle = new A2aOutputHandle(TENANT, "session-params", accepted.path("taskId").asText());
        List<A2aOutput> outputs = awaitOutputs(handle);

        assertThat(outputs).isNotEmpty();
        assertThat(outputs.get(outputs.size() - 1).terminal()).isTrue();
        assertThat(outputs).anyMatch(o -> String.valueOf(o.body()).contains("params metadata"));
    }

    @Test
    void a2aMultiTurnSameSessionKeepsSessionAndCreatesTaskPerCompletedTurn() {
        JsonNode firstTurn = send("session-multi", "first question");
        String firstTaskId = firstTurn.path("taskId").asText();
        awaitTaskState("session-multi", firstTaskId, TaskState.COMPLETED);
        List<A2aOutput> firstOutputs =
                awaitOutputContaining(new A2aOutputHandle(TENANT, "session-multi", firstTaskId), "first question");

        assertThat(sessionManager.get(TENANT, "session-multi")).hasValueSatisfying(session ->
                assertThat(session.currentUserInput()).anyMatch(message -> "first question".equals(message.text())));

        JsonNode secondTurn = send("session-multi", "second question");
        String secondTaskId = secondTurn.path("taskId").asText();
        awaitTaskState("session-multi", secondTaskId, TaskState.COMPLETED);
        List<A2aOutput> secondOutputs = awaitOutputContaining(
                new A2aOutputHandle(TENANT, "session-multi", secondTaskId),
                "second question");

        assertThat(firstTaskId).isNotBlank();
        assertThat(secondTaskId).isNotBlank();
        assertThat(secondTaskId).isNotEqualTo(firstTaskId);
        // Task-scoped egress: each task's stream holds only its own turn's outputs, so a
        // completed first turn cannot suppress or leak into the second turn's stream.
        assertThat(firstOutputs).anyMatch(output -> String.valueOf(output.body()).contains("first question"));
        assertThat(firstOutputs).noneMatch(output -> String.valueOf(output.body()).contains("second question"));
        assertThat(secondOutputs).anyMatch(output -> String.valueOf(output.body()).contains("second question"));
        assertThat(secondOutputs).noneMatch(output -> String.valueOf(output.body()).contains("first question"));
        assertThat(sessionManager.get(TENANT, "session-multi")).hasValueSatisfying(session -> {
            assertThat(session.sessionId()).isEqualTo("session-multi");
            assertThat(session.currentUserInput()).hasSize(1);
            assertThat(session.currentUserInput().get(0).text()).isEqualTo("second question");
        });
        assertThat(taskControlService.tasks(TENANT, "session-multi"))
                .hasSize(2)
                .allSatisfy(task -> assertThat(task.getState()).isEqualTo(TaskState.COMPLETED));
    }

    @Test
    void a2aInterruptedTaskCanBeResumedThroughAccessAndComplete() {
        JsonNode waitingAccepted = send(INTERRUPTING_AGENT, "session-wait", "weather");
        String taskId = waitingAccepted.path("taskId").asText();
        A2aOutputHandle handle = new A2aOutputHandle(TENANT, "session-wait", taskId);
        List<A2aOutput> waitingOutputs = awaitAtLeastOutputs(handle, 1);

        assertThat(waitingAccepted.path("metadata").path("accepted").asBoolean()).isTrue();
        assertThat(waitingOutputs).isNotEmpty();
        assertThat(waitingOutputs.get(waitingOutputs.size() - 1).terminal()).isFalse();
        assertThat(waitingOutputs).anyMatch(output -> String.valueOf(output.body()).contains("which city"));
        awaitTaskState("session-wait", taskId, TaskState.WAITING);

        AgentRequest resumeRequest = new AgentRequest(
                TENANT,
                "user-1",
                INTERRUPTING_AGENT,
                "session-wait",
                List.of(Message.user("beijing")),
                UUID.randomUUID().toString(),
                Map.of("correlationId", "corr-resume"));
        var resumed = submissionService.resume(resumeRequest).toCompletableFuture().join();
        List<A2aOutput> resumedOutputs = awaitOutputs(handle);

        assertThat(resumed.accepted()).isTrue();
        assertThat(resumed.taskId()).isEqualTo(taskId);
        assertThat(resumedOutputs).anyMatch(output -> String.valueOf(output.body()).contains("answer: beijing"));
        assertThat(resumedOutputs.get(resumedOutputs.size() - 1).terminal()).isTrue();
        awaitTaskState("session-wait", taskId, TaskState.COMPLETED);
    }

    @Test
    void aReturnedFailureResultStillRepliesWithATerminalError() {
        JsonNode accepted = send(RESULT_FAILING_AGENT, "session-failed-result", "return failure");
        String taskId = accepted.path("taskId").asText();
        A2aOutputHandle handle = new A2aOutputHandle(TENANT, "session-failed-result", taskId);
        List<A2aOutput> outputs = awaitOutputs(handle);

        assertThat(accepted.path("metadata").path("accepted").asBoolean()).isTrue();
        assertThat(outputs).isNotEmpty();
        assertThat(outputs.get(outputs.size() - 1).terminal()).isTrue();
        assertThat(outputs).anyMatch(output -> "error".equals(output.kind()));
        assertThat(outputs).anyMatch(output -> String.valueOf(output.body()).contains("planned failure"));
        awaitTaskState("session-failed-result", taskId, TaskState.FAILED);
    }

    @Test
    void a2aCancelRequestCancelsTaskThroughTaskControlAndEngine() {
        JsonNode accepted = send(INTERRUPTING_AGENT, "session-cancel", "book ticket");
        String taskId = accepted.path("taskId").asText();
        A2aOutputHandle handle = new A2aOutputHandle(TENANT, "session-cancel", taskId);
        List<A2aOutput> outputs = awaitAtLeastOutputs(handle, 1);
        awaitTaskState("session-cancel", taskId, TaskState.WAITING);

        JsonNode cancelResult = result(a2aJsonRpcHandler.handleToJson(cancelTaskBody(
                INTERRUPTING_AGENT,
                "session-cancel",
                taskId)));

        assertThat(outputs).anyMatch(output -> String.valueOf(output.body()).contains("which city"));
        assertThat(cancelResult.path("id").asText()).isEqualTo(taskId);
        assertThat(cancelResult.path("status").path("state").asText()).isEqualTo("canceled");
        awaitTaskState("session-cancel", taskId, TaskState.CANCELLED);
    }

    private List<A2aOutput> awaitOutputs(A2aOutputHandle handle) {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        List<A2aOutput> outputs = outputRegistry.list(handle);
        while (System.nanoTime() < deadline
                && (outputs.isEmpty() || !outputs.get(outputs.size() - 1).terminal())) {
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            outputs = outputRegistry.list(handle);
        }
        return outputs;
    }

    private List<A2aOutput> awaitOutputContaining(A2aOutputHandle handle, String text) {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        List<A2aOutput> outputs = outputRegistry.list(handle);
        while (System.nanoTime() < deadline
                && outputs.stream().noneMatch(output -> String.valueOf(output.body()).contains(text))) {
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            outputs = outputRegistry.list(handle);
        }
        return outputs;
    }

    private List<A2aOutput> awaitAtLeastOutputs(A2aOutputHandle handle, int count) {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        List<A2aOutput> outputs = outputRegistry.list(handle);
        while (System.nanoTime() < deadline && outputs.size() < count) {
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            outputs = outputRegistry.list(handle);
        }
        return outputs;
    }

    private void awaitTaskState(String sessionId, String taskId, TaskState state) {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (taskControlService.findTask(TENANT, sessionId, taskId)
                    .filter(task -> task.getState() == state)
                    .isPresent()) {
                return;
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(taskControlService.findTask(TENANT, sessionId, taskId))
                .as("task %s/%s should eventually reach %s", sessionId, taskId, state)
                .hasValueSatisfying(task -> assertThat(task.getState()).isEqualTo(state));
    }

    private JsonNode send(String sessionId, String text) {
        return send(AGENT, sessionId, text);
    }

    private JsonNode send(String agentId, String sessionId, String text) {
        String response = a2aJsonRpcHandler.handleToJson(sendMessageBody(agentId, sessionId, text));
        return result(response);
    }

    private JsonNode sendWithParamsMetadataOnly(String sessionId, String text) {
        String response = a2aJsonRpcHandler.handleToJson(sendMessageBodyWithParamsMetadataOnly(sessionId, text));
        return result(response);
    }

    private JsonNode result(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            assertThat(root.path("error").isMissingNode() || root.path("error").isNull())
                    .as(response)
                    .isTrue();
            JsonNode result = root.path("result");
            assertThat(result.isMissingNode()).as(response).isFalse();
            return result;
        } catch (java.io.IOException ex) {
            throw new AssertionError("Invalid JSON-RPC response: " + response, ex);
        }
    }

    private static String sendMessageBody(String agentId, String sessionId, String text) {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": "%s",
                  "method": "SendMessage",
                  "params": {
                    "tenant": "%s",
                    "message": {
                      "role": "ROLE_USER",
                      "messageId": "%s",
                      "contextId": "%s",
                      "parts": [
                        {
                          "kind": "text",
                          "text": "%s"
                        }
                      ],
                      "metadata": {
                        "tenantId": "%s",
                        "userId": "user-1",
                        "agentId": "%s",
                        "sessionId": "%s",
                        "idempotencyKey": "%s",
                        "correlationId": "corr-1"
                      }
                    }
                  }
                }
                """.formatted(
                UUID.randomUUID(),
                TENANT,
                UUID.randomUUID(),
                sessionId,
                text,
                TENANT,
                agentId,
                sessionId,
                UUID.randomUUID());
    }

    private static String sendMessageBodyWithParamsMetadataOnly(String sessionId, String text) {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": "%s",
                  "method": "SendMessage",
                  "params": {
                    "tenant": "%s",
                    "metadata": {
                      "tenantId": "%s",
                      "userId": "user-1",
                      "agentId": "%s",
                      "sessionId": "%s",
                      "idempotencyKey": "%s",
                      "correlationId": "corr-params"
                    },
                    "message": {
                      "role": "ROLE_USER",
                      "messageId": "%s",
                      "contextId": "%s",
                      "parts": [
                        {
                          "kind": "text",
                          "text": "%s"
                        }
                      ]
                    }
                  }
                }
                """.formatted(
                UUID.randomUUID(),
                TENANT,
                TENANT,
                AGENT,
                sessionId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                sessionId,
                text);
    }

    private static String cancelTaskBody(String agentId, String sessionId, String taskId) {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": "%s",
                  "method": "CancelTask",
                  "params": {
                    "tenant": "%s",
                    "id": "%s",
                    "metadata": {
                      "tenantId": "%s",
                      "userId": "user-1",
                      "agentId": "%s",
                      "sessionId": "%s"
                    }
                  }
                }
                """.formatted(
                UUID.randomUUID(),
                TENANT,
                taskId,
                TENANT,
                agentId,
                sessionId);
    }

    @SpringBootConfiguration
    @Import({
            QueueAutoConfiguration.class,
            TaskControlAutoConfiguration.class,
            RuntimeWiringConfiguration.class,
            AccessLayerConfiguration.class,
            SessionManageConfiguration.class,
            EngineAutoConfiguration.class
    })
    static class TestRuntime {

        @Bean
        AgentRuntimeHandler echoAgentHandler() {
            return new EchoAgentHandler();
        }

        @Bean
        AgentRuntimeHandler boomAgentHandler() {
            return new ThrowingAgentHandler();
        }

        @Bean
        AgentRuntimeHandler interruptingAgentHandler() {
            return new InterruptingAgentHandler();
        }

        @Bean
        AgentRuntimeHandler failedResultAgentHandler() {
            return new FailedResultAgentHandler();
        }
    }

    static final class EchoAgentHandler implements AgentRuntimeHandler {

        @Override
        public String agentId() {
            return AGENT;
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            String userText = context.getInput() == null || context.getInput().messages().isEmpty()
                    ? "" : context.getInput().messages().get(0).text();
            return Stream.of(
                    java.util.Map.of("result_type", "output", "output", "echo: " + userText),
                    java.util.Map.of("result_type", "answer", "output", "echo: " + userText));
        }

        @Override
        public StreamAdapter resultAdapter() {
            return AgentServiceEndToEndIT::adaptRawResults;
        }
    }

    static final class ThrowingAgentHandler implements AgentRuntimeHandler {

        @Override
        public String agentId() {
            return FAILING_AGENT;
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            throw new IllegalStateException("boom");
        }

        @Override
        public StreamAdapter resultAdapter() {
            return AgentServiceEndToEndIT::adaptRawResults;
        }
    }

    static final class InterruptingAgentHandler implements AgentRuntimeHandler {

        @Override
        public String agentId() {
            return INTERRUPTING_AGENT;
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            String inputType = context.getInput() == null ? "" : context.getInput().inputType();
            if ("RESUME_SIGNAL".equals(inputType)) {
                String userText = context.getInput().messages().isEmpty()
                        ? "" : context.getInput().messages().get(0).text();
                return Stream.of(AgentExecutionResult.completed("answer: " + userText));
            }
            return Stream.of(AgentExecutionResult.interrupted(InterruptType.HUMAN_INPUT, "which city?"));
        }

        @Override
        public StreamAdapter resultAdapter() {
            return rawResults -> rawResults.map(result -> (AgentExecutionResult) result);
        }
    }

    static final class FailedResultAgentHandler implements AgentRuntimeHandler {

        @Override
        public String agentId() {
            return RESULT_FAILING_AGENT;
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            return Stream.of(AgentExecutionResult.failed("PLANNED_FAILURE", "planned failure"));
        }

        @Override
        public StreamAdapter resultAdapter() {
            return rawResults -> rawResults.map(result -> (AgentExecutionResult) result);
        }
    }

    @SuppressWarnings("unchecked")
    private static Stream<AgentExecutionResult> adaptRawResults(Stream<?> rawResults) {
        return rawResults.map(rawResult -> {
            java.util.Map<String, Object> result = (java.util.Map<String, Object>) rawResult;
            String output = String.valueOf(result.get("output"));
            if ("answer".equals(result.get("result_type"))) {
                return AgentExecutionResult.completed(output);
            }
            return AgentExecutionResult.output(output);
        });
    }
}
