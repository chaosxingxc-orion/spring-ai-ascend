package com.huawei.ascend.service.access.protocol.a2a.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.huawei.ascend.service.access.core.AccessSubmissionService;
import com.huawei.ascend.service.access.model.AccessAcceptedResponse;
import com.huawei.ascend.service.access.protocol.a2a.A2aAccessProperties;
import com.huawei.ascend.service.access.protocol.a2a.egress.A2aOutput;
import com.huawei.ascend.service.access.protocol.a2a.egress.A2aOutputHandle;
import com.huawei.ascend.service.access.protocol.a2a.egress.A2aOutputRegistry;
import com.huawei.ascend.service.access.protocol.a2a.egress.A2aTaskMapper;
import com.huawei.ascend.service.schema.AgentRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendStreamingMessageResponse;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class A2aJsonRpcHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final AccessSubmissionService submissionService = mock(AccessSubmissionService.class);
    private final ArgumentCaptor<AgentRequest> requestCaptor = ArgumentCaptor.forClass(AgentRequest.class);
    private final A2aOutputRegistry outputRegistry = new A2aOutputRegistry();
    private final A2aAccessProperties properties = new A2aAccessProperties();
    private final A2aJsonRpcHandler handler =
            new A2aJsonRpcHandler(submissionService, outputRegistry, objectMapper, properties);

    @BeforeEach
    void setUp() {
        properties.setDefaultTenantId("tenant-default");
        properties.setDefaultAgentId("agent-default");
        when(submissionService.run(requestCaptor.capture())).thenAnswer(invocation -> {
            AgentRequest request = invocation.getArgument(0);
            return CompletableFuture.completedStage(new AccessAcceptedResponse(
                    request.tenantId(),
                    request.userId(),
                    request.agentId(),
                    request.sessionId() == null ? "session-generated" : request.sessionId(),
                    "task-1",
                    true,
                    "accepted"));
        });
    }

    @Test
    void messageSendAcceptsAgentScopeMethodNameAndDefaultTenantAndAgent() throws Exception {
        String response = handler.handleToJson(objectMapper.writeValueAsString(Map.of(
                "jsonrpc", "2.0",
                "id", "request-1",
                "method", "message/send",
                "params", Map.of(
                        "message", Map.of(
                                "role", "user",
                                "kind", "message",
                                "contextId", "session-1",
                                "messageId", UUID.randomUUID().toString(),
                                "metadata", Map.of(
                                        "userId", "user-1",
                                        "sessionId", "session-1"),
                                "parts", List.of(Map.of(
                                        "kind", "text",
                                        "text", "ping")))))));

        JsonNode json = objectMapper.readTree(response);

        assertThat(json.path("error").isMissingNode() || json.path("error").isNull()).as(response).isTrue();
        assertThat(json.path("result").isMissingNode()).as(response).isFalse();
        assertThat(requestCaptor.getValue().tenantId()).isEqualTo("tenant-default");
        assertThat(requestCaptor.getValue().agentId()).isEqualTo("agent-default");
        assertThat(requestCaptor.getValue().userId()).isEqualTo("user-1");
        assertThat(requestCaptor.getValue().input().get(0).text()).isEqualTo("ping");
    }

    @Test
    void messageSendDoesNotWriteMissingOptionalContextValues() throws Exception {
        String response = handler.handleToJson(objectMapper.writeValueAsString(Map.of(
                "jsonrpc", "2.0",
                "id", "request-optional-context",
                "method", "message/send",
                "params", Map.of(
                        "message", Map.of(
                                "role", "user",
                                "kind", "message",
                                "messageId", UUID.randomUUID().toString(),
                                "metadata", Map.of(
                                        "userId", "user-1",
                                        "sessionId", "session-1"),
                                "parts", List.of(Map.of(
                                        "kind", "text",
                                        "text", "ping")))))));

        JsonNode json = objectMapper.readTree(response);

        assertThat(json.path("error").isMissingNode() || json.path("error").isNull()).as(response).isTrue();
        assertThat(json.path("result").isMissingNode()).as(response).isFalse();
        assertThat(requestCaptor.getValue().metadata()).doesNotContainKey("correlationId");
        assertThat(requestCaptor.getValue().metadata()).doesNotContainKey("contextId");
    }

    @Test
    void tasksGetAcceptsAgentScopeMethodName() throws Exception {
        String response = handler.handleToJson(objectMapper.writeValueAsString(Map.of(
                "jsonrpc", "2.0",
                "id", "request-2",
                "method", "tasks/get",
                "params", Map.of(
                        "id", "task-1",
                        "metadata", Map.of(
                                "tenantId", "tenant-1",
                                "sessionId", "session-1")))));

        JsonNode json = objectMapper.readTree(response);

        assertThat(json.at("/result/id").asText()).isEqualTo("task-1");
    }

    @Test
    void tasksGetUsesA2aWireValuesForTaskStatusAndMessageParts() throws Exception {
        Message message = A2aTaskMapper.agentMessage("session-1", "task-1", "pong", Map.of("type", "final_response"));
        outputRegistry.append(
                new A2aOutputHandle("tenant-1", "session-1"),
                new A2aOutput(
                        "TaskStatus",
                        "task-1",
                        new TaskStatusUpdateEvent(
                                "task-1",
                                new TaskStatus(TaskState.TASK_STATE_COMPLETED, message, null),
                                "session-1",
                                Map.of()),
                        null,
                        true,
                        Map.of()));

        String response = handler.handleToJson(objectMapper.writeValueAsString(Map.of(
                "jsonrpc", "2.0",
                "id", "request-2",
                "method", "tasks/get",
                "params", Map.of(
                        "id", "task-1",
                        "metadata", Map.of(
                                "tenantId", "tenant-1",
                                "sessionId", "session-1")))));

        JsonNode json = objectMapper.readTree(response);

        assertThat(json.at("/result/status/state").asText()).isEqualTo("completed");
        assertThat(json.at("/result/status/message/role").asText()).isEqualTo("agent");
        assertThat(json.at("/result/status/message/parts/0/kind").asText()).isEqualTo("text");
        assertThat(json.at("/result/status/message/parts/0/text").asText()).isEqualTo("pong");
    }

    @Test
    void streamingSuccessResponseOmitsNullJsonRpcErrorFieldAndUsesA2aEventShape() throws Exception {
        Message acceptedMessage = A2aTaskMapper.agentMessage(
                "session-1",
                "task-1",
                "accepted",
                Map.of("accepted", Boolean.TRUE));
        SendStreamingMessageResponse response =
                new SendStreamingMessageResponse("request-stream", acceptedMessage);

        String json = handler.toJson(response);

        assertThat(json).contains("\"result\"");
        assertThat(json).contains("\"message\"");
        assertThat(json).doesNotContain("\"error\"");
        assertThat(JSONRPCUtils.parseResponseEvent(json).hasMessage()).isTrue();
    }
}
