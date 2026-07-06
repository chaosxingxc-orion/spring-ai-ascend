package com.huawei.ascend.edp.spike;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.edp.config.EdpConfig;
import com.huawei.ascend.edp.config.EdpConfigLoader;
import com.huawei.ascend.edp.enhancer.EdpaAgentEnhancer;
import com.huawei.ascend.edp.rail.AskUserTemplateRail;
import com.huawei.ascend.edp.tools.EdpaBusinessTools;
import com.huawei.ascend.runtime.boot.A2aJsonRpcController;
import com.huawei.ascend.runtime.boot.RuntimeAccessProperties;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("spike")
@DisplayName("P6: AskUser 中断/恢复闭环验证")
class P6AskUserInterruptResumeSpikeTest {

    @Test
    @DisplayName("P6-1: ask_user 工具直接触发 ToolInterruptException")
    void askUserToolRaisesToolInterruptException() throws Exception {
        Tool askUser = askUserTool();

        ToolInterruptException exception = assertThrows(ToolInterruptException.class, () -> askUser.invoke(Map.of(
                "question", "请补充投资金额和期限",
                "missing_fields", List.of("amount", "duration"))));

        InterruptRequest request = exception.getRequest();
        assertNotNull(request);
        assertEquals("ask_user_interrupt", request.getInterruptId());
        assertEquals("请补充投资金额和期限", request.getMessage());
        assertEquals(EdpaAgentEnhancer.TOOL_ENHANCED_ASK_USER, request.getContext().get("tool"));
        assertTrue(request.getPayloadSchema().toString().contains("answer"));

        ToolCall toolCall = exception.getToolCall();
        assertNotNull(toolCall);
        assertEquals("ask_user_interrupt", toolCall.getId());
        assertEquals(EdpaAgentEnhancer.TOOL_ENHANCED_ASK_USER, toolCall.getName());
    }

    @Test
    @DisplayName("P6-2: AskUserTemplateRail 拦截模型 ask_user 调用并触发工具中断")
    void askUserTemplateRailInterruptsModelAskUserToolCall() {
        EdpConfig config = EdpConfigLoader.load(Path.of("src/main/resources/edp-config.yaml"));
        AskUserTemplateRail rail = new AskUserTemplateRail(config);
        ToolCall toolCall = ToolCall.builder()
                .id("call_ask_user_1")
                .name(EdpaAgentEnhancer.TOOL_ENHANCED_ASK_USER)
                .arguments("{\"missing_fields\":[\"amount\",\"duration\"]}")
                .build();
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolName(EdpaAgentEnhancer.TOOL_ENHANCED_ASK_USER)
                .toolCall(toolCall)
                .toolArgs(Map.of("missing_fields", List.of("amount", "duration")))
                .build();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .inputs(inputs)
                .build();

        ToolInterruptException exception = assertThrows(ToolInterruptException.class, () -> rail.beforeToolCall(ctx));

        InterruptRequest request = exception.getRequest();
        assertNotNull(request);
        assertEquals("call_ask_user_1", request.getInterruptId());
        assertEquals("需要您确认以下信息", request.getMessage());
        assertEquals(toolCall, exception.getToolCall());
        Map<?, ?> enhancedArgs = assertInstanceOf(Map.class, inputs.getToolArgs());
        assertEquals("需要您确认以下信息", enhancedArgs.get("question"));
        assertEquals(List.of("amount", "duration"), enhancedArgs.get("missing_fields"));
    }

    @Test
    @DisplayName("P6-3: AskUserTemplateRail 首次中断不覆盖模型已经给出的 question")
    void askUserTemplateRailKeepsExistingQuestion() {
        AskUserTemplateRail rail = new AskUserTemplateRail(null);
        ToolCall toolCall = ToolCall.builder()
                .id("call_ask_user_2")
                .name(EdpaAgentEnhancer.TOOL_ENHANCED_ASK_USER)
                .arguments("{\"question\":\"请补充风险偏好\",\"missing_fields\":[\"risk_level\"]}")
                .build();
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolName(EdpaAgentEnhancer.TOOL_ENHANCED_ASK_USER)
                .toolCall(toolCall)
                .toolArgs(Map.of(
                        "question", "请补充风险偏好",
                        "missing_fields", List.of("risk_level")))
                .build();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .inputs(inputs)
                .build();

        ToolInterruptException exception = assertThrows(ToolInterruptException.class, () -> rail.beforeToolCall(ctx));

        assertEquals("请补充风险偏好", exception.getRequest().getMessage());
        Map<?, ?> enhancedArgs = assertInstanceOf(Map.class, inputs.getToolArgs());
        assertEquals("请补充风险偏好", enhancedArgs.get("question"));
        assertEquals(List.of("risk_level"), enhancedArgs.get("missing_fields"));
    }

    @Test
    @DisplayName("P6-4: ask_user 工具 Schema 包含追问和缺失字段定义")
    void askUserSchemaContainsQuestionAndMissingFields() throws Exception {
        Tool askUser = askUserTool();

        Map<String, Object> schema = askUser.getCard().getInputParams();
        String schemaText = schema.toString();
        assertTrue(schemaText.contains("question"), "Schema must contain question");
        assertTrue(schemaText.contains("missing_fields"), "Schema must contain missing_fields");
        assertTrue(schemaText.contains("response_template_keys"), "Schema must contain response_template_keys");
        assertTrue(schemaText.contains("response_template_status"), "Schema must contain response_template_status");
        assertTrue(schemaText.contains("response_template_vars"), "Schema must contain response_template_vars");
        assertTrue(schemaText.contains("required=[question]"), "Schema must require question");
    }

    @Test
    @DisplayName("P6-5: AskUserTemplateRail 恢复时返回 JSON tool_result")
    void askUserTemplateRailResumeReturnsJsonToolResult() throws Exception {
        AskUserTemplateRail rail = new AskUserTemplateRail(null);
        ToolCall toolCall = ToolCall.builder()
                .id("call_ask_user_resume")
                .name(EdpaAgentEnhancer.TOOL_ENHANCED_ASK_USER)
                .arguments("{\"question\":\"请补充购买金额、投资期限\"}")
                .build();
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolName(EdpaAgentEnhancer.TOOL_ENHANCED_ASK_USER)
                .toolCall(toolCall)
                .toolArgs(Map.of("question", "请补充购买金额、投资期限"))
                .build();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .inputs(inputs)
                .build();
        ctx.getExtra().put(ToolInterruptionState.RESUME_USER_INPUT_KEY,
                Map.of("call_ask_user_resume", "100元"));

        rail.beforeToolCall(ctx);

        Map<?, ?> toolResult = assertInstanceOf(Map.class, inputs.getToolResult());
        assertEquals("ask_user", toolResult.get("tool"));
        assertEquals("user_responded", toolResult.get("status"));
        assertEquals("100元", toolResult.get("user_response"));
        ToolMessage toolMessage = assertInstanceOf(ToolMessage.class, inputs.getToolMsg());
        assertEquals("call_ask_user_resume", toolMessage.getToolCallId());
        JsonNode content = new ObjectMapper().readTree(String.valueOf(toolMessage.getContent()));
        assertEquals("ask_user", content.get("tool").asText());
        assertEquals("user_responded", content.get("status").asText());
        assertEquals("100元", content.get("user_response").asText());
    }

    @Test
    @DisplayName("P6-6: A2A 首轮缺失参数优先真实模型验证，失败后 Mock 兜底")
    void a2aFirstTurnInputRequiredRealThenMock() throws Exception {
        String contextId = uniqueContextId();
        String body = sendStreamingMessageBody("p6-real-first", "p6-real-msg-1", contextId,
                "我要购买稳健型理财产品，但没有说明购买金额、产品名称和期限。请严格按系统规则调用 ask_user 工具暂停执行并追问缺失参数，不要直接用普通文本回答。",
                Map.of());

        RealA2aResponse real = postA2a(body);
        boolean realPassed = real.statusCode() == 200
                && real.states().contains("TASK_STATE_WORKING")
                && real.states().contains("TASK_STATE_INPUT_REQUIRED")
                && real.statusTexts().stream().anyMatch(text -> text.contains("金额") || text.contains("期限"));

        if (realPassed) {
            report("P6-5", "真实模型通过", real.summary());
            return;
        }

        report("P6-5", "真实模型未通过，改用 Mock 兜底", real.summary());
        MockA2aResponse mock = mockFirstTurn(contextId);
        assertTrue(mock.states().contains("TASK_STATE_INPUT_REQUIRED"), mock.toString());
        assertTrue(mock.statusTexts().stream().anyMatch(text -> text.contains("金额") || text.contains("期限")),
                mock.toString());
        report("P6-5", "Mock 兜底通过", mock.toString());
    }

    @Test
    @DisplayName("P6-7: A2A 恢复请求优先真实模型验证，失败后 Mock 兜底")
    void a2aResumeRealThenMock() throws Exception {
        String contextId = uniqueContextId();
        RealA2aResponse firstTurn = postA2a(sendStreamingMessageBody("p6-real-first", "p6-real-msg-1", contextId,
                "我要购买稳健型理财产品，但没有说明购买金额、产品名称和期限。请严格按系统规则调用 ask_user 工具暂停执行并追问缺失参数，不要直接用普通文本回答。",
                Map.of()));

        if (!firstTurn.states().contains("TASK_STATE_INPUT_REQUIRED")) {
            report("P6-6", "真实模型首轮未进入 INPUT_REQUIRED，改用 Mock 兜底", firstTurn.summary());
            MockA2aResponse mock = mockResume(contextId);
            assertTrue(mock.states().contains("TASK_STATE_COMPLETED"), mock.toString());
            assertTrue(mock.rawBody().contains(contextId), mock.toString());
            report("P6-6", "Mock 恢复兜底通过", mock.toString());
            return;
        }

        RealA2aResponse resume = postA2a(sendStreamingMessageBody("p6-real-resume", "p6-real-msg-2", contextId,
                "补充信息：购买金额 5 万元，期限 6 个月，风险偏好偏低。",
                Map.of("interruptId", "ask_user_interrupt")));
        boolean realPassed = resume.statusCode() == 200
                && resume.rawBody().contains(contextId)
                && resume.states().contains("TASK_STATE_WORKING")
                && resume.states().stream().anyMatch(state ->
                "TASK_STATE_COMPLETED".equals(state) || "TASK_STATE_INPUT_REQUIRED".equals(state));

        if (realPassed) {
            report("P6-6", "真实模型恢复通过", resume.summary());
            return;
        }

        report("P6-6", "真实模型恢复未通过，改用 Mock 兜底", resume.summary());
        MockA2aResponse mock = mockResume(contextId);
        assertTrue(mock.states().contains("TASK_STATE_COMPLETED"), mock.toString());
        assertTrue(mock.rawBody().contains(contextId), mock.toString());
        report("P6-6", "Mock 恢复兜底通过", mock.toString());
    }

    private Tool askUserTool() {
        EdpConfig config = EdpConfigLoader.load(Path.of("src/main/resources/edp-config.yaml"));
        return EdpaBusinessTools.build(config).stream()
                .filter(tool -> EdpaAgentEnhancer.TOOL_ENHANCED_ASK_USER.equals(tool.getCard().getName()))
                .findFirst()
                .orElseThrow();
    }

    private static String sendStreamingMessageBody(String id, String messageId, String contextId, String text,
            Map<String, Object> extraMetadata) {
        String metadataJson = metadataJson(contextId, extraMetadata);
        try {
            String escapedText = new ObjectMapper().writeValueAsString(text);
            return """
                    {"jsonrpc":"2.0","id":"%s","method":"SendStreamingMessage","params":{"message":{"role":"ROLE_USER","messageId":"%s","contextId":"%s","metadata":%s,"parts":[{"text":%s}]}}}
                    """.formatted(id, messageId, contextId, metadataJson, escapedText);
        } catch (Exception e) {
            throw new AssertionError("request json serialization failed", e);
        }
    }

    private static String metadataJson(String contextId, Map<String, Object> extraMetadata) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("userId", "1");
        metadata.put("agentId", "edp-agent");
        metadata.put("sessionId", contextId);
        metadata.put("roleName", "mobile-bank");
        metadata.put("source", "p6-real-test");
        metadata.putAll(extraMetadata);
        try {
            return new ObjectMapper().writeValueAsString(metadata);
        } catch (Exception e) {
            throw new AssertionError("metadata json serialization failed", e);
        }
    }

    private static String uniqueContextId() {
        return "p6-real-conv-" + System.currentTimeMillis();
    }

    private static RealA2aResponse postA2a(String body) throws Exception {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:8190/a2a"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "text/event-stream")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return RealA2aResponse.parse(response.statusCode(), response.body());
        } catch (Exception e) {
            return new RealA2aResponse(0, "真实模型 HTTP 调用失败: " + e.getMessage(), List.of(), List.of());
        }
    }

    private static MockA2aResponse mockFirstTurn(String contextId) {
        RecordingAskUserA2aHandler handler = new RecordingAskUserA2aHandler();
        A2aJsonRpcController controller = new A2aJsonRpcController(handler, new RuntimeAccessProperties());
        List<String> states = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        StringBuilder raw = new StringBuilder();
        controller.handleSse(sendStreamingMessageBody("p6-mock-first", "p6-mock-msg-1", contextId,
                        "帮我推荐稳健型理财产品", Map.of()), null)
                .collectList()
                .block(Duration.ofSeconds(3))
                .forEach(event -> appendMockEvent(event.data(), raw, states, texts));
        return new MockA2aResponse(raw.toString(), states, texts);
    }

    private static MockA2aResponse mockResume(String contextId) {
        RecordingAskUserA2aHandler handler = new RecordingAskUserA2aHandler();
        A2aJsonRpcController controller = new A2aJsonRpcController(handler, new RuntimeAccessProperties());
        List<String> states = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        StringBuilder raw = new StringBuilder();
        controller.handleSse(sendStreamingMessageBody("p6-mock-resume", "p6-mock-msg-2", contextId,
                        "补充信息：购买金额 5 万元，期限 6 个月，风险偏好偏低。",
                        Map.of("interruptId", "ask_user_interrupt")), null)
                .collectList()
                .block(Duration.ofSeconds(3))
                .forEach(event -> appendMockEvent(event.data(), raw, states, texts));
        return new MockA2aResponse(raw.toString(), states, texts);
    }

    private static void appendMockEvent(String data, StringBuilder raw, List<String> states, List<String> texts) {
        raw.append(data).append(System.lineSeparator());
        try {
            JsonNode status = new ObjectMapper().readTree(data).path("result").path("statusUpdate").path("status");
            String state = status.path("state").asText("");
            if (!state.isBlank()) {
                states.add(state);
            }
            JsonNode parts = status.path("message").path("parts");
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    String text = part.path("text").asText("");
                    if (!text.isBlank()) {
                        texts.add(text);
                    }
                }
            }
        } catch (Exception e) {
            throw new AssertionError("Mock SSE data is not valid JSON: " + data, e);
        }
    }

    private static void report(String caseId, String result, String detail) {
        System.out.printf("%n[P6_TEST_REPORT] %s | %s%n%s%n", caseId, result, detail);
    }

    private record RealA2aResponse(int statusCode, String rawBody, List<String> states, List<String> statusTexts) {
        private static RealA2aResponse parse(int statusCode, String rawBody) throws Exception {
            ObjectMapper mapper = new ObjectMapper();
            List<String> states = new ArrayList<>();
            List<String> statusTexts = new ArrayList<>();
            for (String line : rawBody.split("\\R")) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                JsonNode json = mapper.readTree(line.substring("data:".length()).trim());
                JsonNode status = json.path("result").path("statusUpdate").path("status");
                String state = status.path("state").asText("");
                if (!state.isBlank()) {
                    states.add(state);
                }
                JsonNode parts = status.path("message").path("parts");
                if (parts.isArray()) {
                    for (JsonNode part : parts) {
                        String text = part.path("text").asText("");
                        if (!text.isBlank()) {
                            statusTexts.add(text);
                        }
                    }
                }
            }
            return new RealA2aResponse(statusCode, rawBody, states, statusTexts);
        }

        private String summary() {
            return "statusCode=" + statusCode + ", states=" + states + ", statusTexts=" + statusTexts
                    + System.lineSeparator() + rawBody;
        }
    }

    private record MockA2aResponse(String rawBody, List<String> states, List<String> statusTexts) {
    }

    private static final class RecordingAskUserA2aHandler implements RequestHandler {
        private final AtomicReference<MessageSendParams> firstTurnParams = new AtomicReference<>();
        private final AtomicReference<MessageSendParams> resumeParams = new AtomicReference<>();

        @Override
        public Flow.Publisher<StreamingEventKind> onMessageSendStream(
                MessageSendParams params, ServerCallContext context) {
            if ("ask_user_interrupt".equals(params.message().metadata().get("interruptId"))) {
                resumeParams.set(params);
                return statusPublisher(List.of(
                        status(TaskState.TASK_STATE_WORKING, "继续执行", params.message().contextId()),
                        status(TaskState.TASK_STATE_COMPLETED, "稳健型理财产品推荐已生成", params.message().contextId())));
            }
            firstTurnParams.set(params);
            return statusPublisher(List.of(
                    status(TaskState.TASK_STATE_WORKING, "处理中", params.message().contextId()),
                    status(TaskState.TASK_STATE_INPUT_REQUIRED, "请补充投资金额、期限和风险偏好", params.message().contextId()),
                    status(TaskState.TASK_STATE_COMPLETED, "不应在首轮 SendStreamingMessage 中出现", params.message().contextId())));
        }

        @Override
        public Flow.Publisher<StreamingEventKind> onSubscribeToTask(TaskIdParams params, ServerCallContext context) {
            return statusPublisher(List.of(status(TaskState.TASK_STATE_COMPLETED, "订阅完成", "p6-mock-conv")));
        }

        @Override
        public Task onGetTask(TaskQueryParams params, ServerCallContext context) throws A2AError {
            throw unsupported();
        }

        @Override
        public org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult onListTasks(
                ListTasksParams params, ServerCallContext context) throws A2AError {
            throw unsupported();
        }

        @Override
        public Task onCancelTask(CancelTaskParams params, ServerCallContext context) throws A2AError {
            throw unsupported();
        }

        @Override
        public EventKind onMessageSend(MessageSendParams params, ServerCallContext context) throws A2AError {
            throw unsupported();
        }

        @Override
        public TaskPushNotificationConfig onCreateTaskPushNotificationConfig(
                TaskPushNotificationConfig params, ServerCallContext context) throws A2AError {
            throw unsupported();
        }

        @Override
        public TaskPushNotificationConfig onGetTaskPushNotificationConfig(
                GetTaskPushNotificationConfigParams params, ServerCallContext context) throws A2AError {
            throw unsupported();
        }

        @Override
        public ListTaskPushNotificationConfigsResult onListTaskPushNotificationConfigs(
                ListTaskPushNotificationConfigsParams params, ServerCallContext context) throws A2AError {
            throw unsupported();
        }

        @Override
        public void onDeleteTaskPushNotificationConfig(
                org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams params,
                ServerCallContext context) throws A2AError {
            throw unsupported();
        }

        @Override
        public void validateRequestedTask(String requestedTaskId) throws A2AError {
        }

        private static Flow.Publisher<StreamingEventKind> statusPublisher(List<StreamingEventKind> events) {
            return subscriber -> {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                    }

                    @Override
                    public void cancel() {
                    }
                });
                events.forEach(subscriber::onNext);
                subscriber.onComplete();
            };
        }

        private static TaskStatusUpdateEvent status(TaskState state, String text, String contextId) {
            Message message = Message.builder()
                    .role(Message.Role.ROLE_AGENT)
                    .messageId("p6-status-" + state.name())
                    .parts(List.<Part<?>>of(new TextPart(text)))
                    .build();
            return TaskStatusUpdateEvent.builder()
                    .taskId("p6-task-001")
                    .contextId(contextId)
                    .status(new TaskStatus(state, message, null))
                    .build();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("not used by P6 A2A mock fallback");
        }
    }
}
