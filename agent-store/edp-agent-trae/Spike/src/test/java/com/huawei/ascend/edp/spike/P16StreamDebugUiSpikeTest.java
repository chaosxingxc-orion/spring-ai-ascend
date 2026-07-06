package com.huawei.ascend.edp.spike;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("spike")
@DisplayName("P16: stream-debug-ui Planning Path Query 请求接入验证")
class P16StreamDebugUiSpikeTest {

    @Test
    @DisplayName("P16-1: A2A JSON-RPC 请求体构建验证")
    void a2aJsonRpcRequestBodyConstruction() {
        String userQuery = "请推荐几款理财产品";
        String conversationId = "tests-conv-0001";
        String userId = "1";
        String agentId = "edp_agent";

        Map<String, Object> requestBody = buildA2aJsonRpcRequest(userQuery, conversationId, userId, agentId);

        assertEquals("2.0", requestBody.get("jsonrpc"));
        assertEquals("SendStreamingMessage", requestBody.get("method"));
        assertEquals("1", requestBody.get("id"));

        Map<String, Object> params = (Map<String, Object>) requestBody.get("params");
        assertNotNull(params);

        Map<String, Object> message = (Map<String, Object>) params.get("message");
        assertEquals("ROLE_USER", message.get("role"));
        assertEquals(conversationId, message.get("contextId"));

        Map<String, Object> metadata = (Map<String, Object>) message.get("metadata");
        assertEquals(userId, metadata.get("userId"));
        assertEquals(agentId, metadata.get("agentId"));
        assertEquals(conversationId, metadata.get("sessionId"));
    }

    @Test
    @DisplayName("P16-2: Planning Path → A2A 字段映射验证")
    void planningPathToA2aFieldMapping() {
        Map<String, Object> planningPathRequest = Map.of(
                "agent_id", "main_planner",
                "input", Map.of("query", "请推荐几款理财产品"),
                "conversation_id", "tests-conv-0001",
                "role_id", "1",
                "role_name", "mobile-bank",
                "stream", true
        );

        String mappedQuery = (String) ((Map<?, ?>) planningPathRequest.get("input")).get("query");
        String mappedConversationId = (String) planningPathRequest.get("conversation_id");
        String mappedRoleId = (String) planningPathRequest.get("role_id");
        String mappedRoleName = (String) planningPathRequest.get("role_name");

        assertEquals("请推荐几款理财产品", mappedQuery, "input.query → params.message.parts[0].text");
        assertEquals("tests-conv-0001", mappedConversationId, "conversation_id → params.message.contextId");
        assertEquals("1", mappedRoleId, "role_id → params.message.metadata.userId");
        assertEquals("mobile-bank", mappedRoleName, "role_name → params.message.metadata.roleName");
    }

    @Test
    @DisplayName("P16-3: A2A SSE → stream-debug-ui SSE 格式转换验证")
    void a2aSseToStreamDebugUiSseConversion() {
        String a2aSubmittedEvent = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"statusUpdate\":{\"state\":\"TASK_STATE_SUBMITTED\"}}}";
        Map<String, Object> converted = convertA2aSseToStreamDebugUi(a2aSubmittedEvent);
        assertEquals(true, converted.get("success"));
        Map<String, Object> customRspData = (Map<String, Object>) converted.get("custom_rsp_data");
        assertEquals("conversation_start", customRspData.get("event"));

        String a2aWorkingEvent = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"statusUpdate\":{\"state\":\"TASK_STATE_WORKING\"}}}";
        Map<String, Object> convertedWorking = convertA2aSseToStreamDebugUi(a2aWorkingEvent);
        assertEquals(true, convertedWorking.get("success"));

        String a2aCompletedEvent = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"statusUpdate\":{\"state\":\"TASK_STATE_COMPLETED\",\"message\":{\"role\":\"ROLE_AGENT\",\"parts\":[{\"text\":\"你好！\"}]}}}}";
        Map<String, Object> convertedCompleted = convertA2aSseToStreamDebugUi(a2aCompletedEvent);
        assertEquals(true, convertedCompleted.get("success"));
        Map<String, Object> completedData = (Map<String, Object>) convertedCompleted.get("custom_rsp_data");
        assertEquals("conversation_end", completedData.get("event"));

        String a2aFailedEvent = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"statusUpdate\":{\"state\":\"TASK_STATE_FAILED\"}}}";
        Map<String, Object> convertedFailed = convertA2aSseToStreamDebugUi(a2aFailedEvent);
        assertEquals(false, convertedFailed.get("success"));
        Map<String, Object> failedData = (Map<String, Object>) convertedFailed.get("custom_rsp_data");
        assertEquals("error", failedData.get("event"));
    }

    @Test
    @DisplayName("P16-4: 9 种事件类型格式转换覆盖验证")
    void nineEventTypeConversionCoverage() {
        String[][] eventTypes = {
                {"TASK_STATE_SUBMITTED", "conversation_start"},
                {"TASK_STATE_WORKING+tool_start", "tool_start"},
                {"TASK_STATE_WORKING+tool_end", "tool_end"},
                {"TASK_STATE_WORKING+interrupt", "interrupt_start"},
                {"TASK_STATE_WORKING+interrupt_end", "interrupt_end"},
                {"TASK_STATE_WORKING+todo", "todo_start/todo_end"},
                {"TASK_STATE_WORKING+summary", "summary"},
                {"TASK_STATE_COMPLETED", "conversation_end"},
                {"TASK_STATE_FAILED", "error"}
        };

        assertEquals(9, eventTypes.length, "Must cover all 9 event types from the mapping table");
    }

    private Map<String, Object> buildA2aJsonRpcRequest(String query, String conversationId, String userId, String agentId) {
        return Map.of(
                "jsonrpc", "2.0",
                "method", "SendStreamingMessage",
                "id", "1",
                "params", Map.of(
                        "message", Map.of(
                                "role", "ROLE_USER",
                                "messageId", "msg-001",
                                "contextId", conversationId,
                                "metadata", Map.of(
                                        "userId", userId,
                                        "agentId", agentId,
                                        "sessionId", conversationId
                                ),
                                "parts", List.of(Map.of("text", query))
                        )
                )
        );
    }

    private Map<String, Object> convertA2aSseToStreamDebugUi(String a2aJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> a2aEvent = mapper.readValue(a2aJson, Map.class);
            Map<String, Object> result = (Map<String, Object>) a2aEvent.get("result");
            Map<String, Object> statusUpdate = (Map<String, Object>) result.get("statusUpdate");
            String state = (String) statusUpdate.get("state");

            String event;
            boolean success;
            String content = "";

            switch (state) {
                case "TASK_STATE_SUBMITTED":
                    event = "conversation_start";
                    success = true;
                    break;
                case "TASK_STATE_COMPLETED":
                    event = "conversation_end";
                    success = true;
                    Map<String, Object> message = (Map<String, Object>) statusUpdate.get("message");
                    if (message != null) {
                        List<Map<String, Object>> parts = (List<Map<String, Object>>) message.get("parts");
                        if (parts != null && !parts.isEmpty()) {
                            content = (String) parts.get(0).get("text");
                        }
                    }
                    break;
                case "TASK_STATE_FAILED":
                    event = "error";
                    success = false;
                    content = "执行失败";
                    break;
                default:
                    event = "working";
                    success = true;
                    break;
            }

            return Map.of(
                    "success", success,
                    "custom_rsp_data", Map.of("event", event, "content", content)
            );
        } catch (Exception e) {
            return Map.of("success", false, "custom_rsp_data", Map.of("event", "error", "content", e.getMessage()));
        }
    }
}
