package com.huawei.ascend.edp.rail;

import com.huawei.ascend.edp.config.ScriptConstants;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.tools.TodoItem;
import com.openjiuwen.harness.tools.TodoStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * EdpaEventRail 全量事件覆盖与成对配对测试。
 */
class EdpaEventRailLoopTest {

    private List<String> events;
    private List<Map<String, Object>> payloads;
    private Session session;
    private TaskPlanningRail taskPlanningRail;
    private EdpaEventRail rail;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        events = new ArrayList<>();
        payloads = new ArrayList<>();
        session = mock(Session.class);
        when(session.getSessionId()).thenReturn("test-conv-001");
        doAnswer(inv -> {
            Object data = inv.getArgument(0);
            if (data instanceof OutputSchema os && "custom".equals(os.getType())
                    && os.getPayload() instanceof Map<?, ?> raw) {
                Map<String, Object> map = new LinkedHashMap<>();
                raw.forEach((k, v) -> map.put(String.valueOf(k), v));
                events.add(String.valueOf(map.get("event")));
                payloads.add(map);
            }
            return null;
        }).when(session).writeStream(any());

        taskPlanningRail = mock(TaskPlanningRail.class);
        DeepAgent deepAgent = mock(DeepAgent.class);
        when(deepAgent.getRegisteredRails()).thenReturn(List.of(taskPlanningRail));
        rail = new EdpaEventRail(deepAgent);
    }

    @Test
    @DisplayName("T-LOOP-01: 完整 agent loop 覆盖全部 Rail 发射事件 + 成对配对")
    void fullLoop_allRailEventsCoveredAndPaired() {
        when(taskPlanningRail.cachedTodos("test-conv-001")).thenReturn(List.of(
                todo("uuid-1", "推荐", TodoStatus.IN_PROGRESS, List.of()),
                todo("uuid-2", "筛选", TodoStatus.PENDING, List.of("uuid-1"))));
        rail.beforeInvoke(ctx(null));

        rail.beforeModelCall(ctx(modelInputs(assistantWithReasoning(false, "我先规划一下步骤"))));
        rail.afterModelCall(ctx(modelInputs(assistantWithReasoning(false, "我先规划一下步骤"))));

        rail.beforeToolCall(ctx(toolInputs("call_mcp")));
        rail.afterToolCall(ctx(toolInputs("call_mcp")));

        when(taskPlanningRail.cachedTodos("test-conv-001")).thenReturn(List.of(
                todo("uuid-1", "推荐", TodoStatus.COMPLETED, List.of()),
                todo("uuid-2", "筛选", TodoStatus.IN_PROGRESS, List.of("uuid-1"))));
        rail.afterToolCall(ctx(toolInputs("todo_modify")));

        ToolInterruptException interrupt = new ToolInterruptException(
                com.openjiuwen.core.singleagent.interrupt.InterruptRequest.builder().build(),
                new ToolCall("c1", "function", "ask_user", "{}", 0));
        rail.onToolException(ctxWithException(toolInputs("ask_user"), interrupt));

        rail.afterInvoke(ctx(null));

        rail.beforeInvoke(ctx(null));

        AgentCallbackContext askCtx = ctx(toolInputs("ask_user"));
        askCtx.getExtra().put(ScriptConstants.KEY_SKIP_TOOL, Boolean.TRUE);
        rail.afterToolCall(askCtx);

        rail.beforeModelCall(ctx(modelInputs(assistantWithReasoning(true, "所有任务完成，输出总结"))));
        rail.afterModelCall(ctx(modelInputs(assistantWithReasoning(true, "所有任务完成，输出总结"))));

        rail.afterInvoke(ctx(null));

        List<String> railEmitted = List.of(
                "conversation_start", "conversation_end",
                "think_start", "think_chunk", "think_end",
                "todolist_start", "todolist_item", "todolist_end",
                "todo_start", "todo_end",
                "tool_start", "tool_end",
                "interrupt_start", "interrupt_end",
                "final_answer_start", "final_answer_chunk", "final_answer_end");
        for (String e : railEmitted) {
            assertTrue(events.contains(e), "缺少 Rail 发射事件: " + e + "，实际: " + events);
        }

        assertEquals(2, count("conversation_start"));
        assertEquals(2, count("conversation_end"));
        assertEquals("conversation_start", events.get(0));
        assertEquals("conversation_end", events.get(events.size() - 1));

        assertEquals(count("tool_start"), count("tool_end"));
        assertTrue(firstIdx("tool_start") < firstIdx("tool_end"));

        assertEquals(count("todolist_start"), count("todolist_end"));
        assertTrue(count("todolist_item") >= 1);
        assertTrue(firstIdx("todolist_start") < firstIdx("todolist_item"));
        assertTrue(firstIdx("todolist_item") < firstIdx("todolist_end"));

        assertTrue(count("todo_start") >= 1);
        assertTrue(count("todo_end") >= 1);

        assertEquals(count("interrupt_start"), count("interrupt_end"));
        assertEquals(1, count("interrupt_start"));
        assertTrue(firstIdx("interrupt_start") < firstIdx("interrupt_end"));

        assertEquals(count("final_answer_start"), count("final_answer_end"));
        assertEquals(1, count("final_answer_start"));
        assertTrue(firstIdx("final_answer_start") < firstIdx("final_answer_end"));

        assertEquals(count("think_end"), count("think_start"));
        assertEquals(2, count("think_start"));

        assertEquals(0, count("todo_status"));
        assertEquals(0, count("tool_status"));
    }

    @Test
    @DisplayName("T-LOOP-02: think_chunk 与 final_answer_chunk 由 Rail 发射并携带内容")
    void chunkEvents_emittedByRailWithContent() {
        AssistantMessage thinkMsg = mock(AssistantMessage.class);
        when(thinkMsg.getFinishReason()).thenReturn("tool_calls");
        when(thinkMsg.getToolCalls()).thenReturn(List.of(mock(ToolCall.class)));
        when(thinkMsg.getReasoningContent()).thenReturn("我先规划一下步骤");
        when(thinkMsg.getContentAsString()).thenReturn("");
        rail.beforeModelCall(ctx(modelInputs(thinkMsg)));
        rail.afterModelCall(ctx(modelInputs(thinkMsg)));
        assertEquals(1, count("think_start"));
        assertEquals(1, count("think_chunk"));
        assertEquals(1, count("think_end"));
        assertTrue(payloads.stream().anyMatch(p -> "think_chunk".equals(p.get("event"))
                        && "我先规划一下步骤".equals(p.get("content"))));
        assertEquals(0, count("final_answer_start"));

        AssistantMessage finalMsg = mock(AssistantMessage.class);
        when(finalMsg.getFinishReason()).thenReturn("stop");
        when(finalMsg.getToolCalls()).thenReturn(List.of());
        when(finalMsg.getReasoningContent()).thenReturn("所有任务完成，输出总结");
        when(finalMsg.getContentAsString()).thenReturn("您的账户余额为 100 元。");
        rail.beforeModelCall(ctx(modelInputs(finalMsg)));
        rail.afterModelCall(ctx(modelInputs(finalMsg)));
        assertEquals(2, count("think_start"));
        assertEquals(2, count("think_end"));
        assertEquals(1, count("final_answer_chunk"));
        assertTrue(payloads.stream().anyMatch(p -> "final_answer_chunk".equals(p.get("event"))
                        && "您的账户余额为 100 元。".equals(p.get("content"))));
        assertEquals(1, count("final_answer_start"));
        assertEquals(1, count("final_answer_end"));
        assertTrue(lastIdx("think_end") < firstIdx("final_answer_start"));
    }

    @Test
    @DisplayName("T-LOOP-02b: 无 reasoning 时 think_chunk 取 content 回退（非推理模型）")
    void noReasoning_thinkChunkFallsBackToContent() {
        AssistantMessage msg = mock(AssistantMessage.class);
        when(msg.getFinishReason()).thenReturn("stop");
        when(msg.getToolCalls()).thenReturn(List.of());
        when(msg.getReasoningContent()).thenReturn(null);
        when(msg.getContentAsString()).thenReturn("你好！");
        rail.beforeModelCall(ctx(modelInputs(msg)));
        rail.afterModelCall(ctx(modelInputs(msg)));
        assertEquals(1, count("think_start"));
        assertEquals(1, count("think_chunk"));
        assertEquals(1, count("think_end"));
        assertTrue(payloads.stream().anyMatch(p -> "think_chunk".equals(p.get("event"))
                && "你好！".equals(p.get("content"))));
        assertEquals(1, count("final_answer_start"));
        assertEquals(1, count("final_answer_chunk"));
        assertEquals(1, count("final_answer_end"));
    }

    @Test
    @DisplayName("T-LOOP-02c: 无 reasoning 且无 content 时发 think 对（无 chunk，保 Rule 2 严格配对）")
    void emptyOutput_thinkPairWithoutChunk() {
        AssistantMessage msg = mock(AssistantMessage.class);
        when(msg.getFinishReason()).thenReturn("stop");
        when(msg.getToolCalls()).thenReturn(List.of());
        when(msg.getReasoningContent()).thenReturn(null);
        when(msg.getContentAsString()).thenReturn(null);
        rail.beforeModelCall(ctx(modelInputs(msg)));
        rail.afterModelCall(ctx(modelInputs(msg)));
        assertEquals(1, count("think_start"));
        assertEquals(0, count("think_chunk"));
        assertEquals(1, count("think_end"));
        assertEquals(count("think_start"), count("think_end"));
        assertEquals(1, count("final_answer_start"));
    }

    @Test
    @DisplayName("T-LOOP-03: todolist_item 为单对象（逐条）且 depends_on 为 UUID（Rule 12 / C9）")
    void todolistItemPayload_singleObjectWithUuidDependsOn() {
        rail.beforeInvoke(ctx(null));
        when(taskPlanningRail.cachedTodos("test-conv-001")).thenReturn(List.of(
                todo("uuid-1", "推荐理财产品", TodoStatus.COMPLETED, List.of()),
                todo("uuid-2", "确定购买", TodoStatus.IN_PROGRESS, List.of("uuid-1"))));
        rail.afterToolCall(ctx(toolInputs("todo_modify")));

        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> p : payloads) {
            if ("todolist_item".equals(p.get("event"))) {
                items.add(p);
            }
        }
        assertEquals(2, items.size());
        for (Map<String, Object> item : items) {
            assertFalse(item.containsKey("tasks"));
            assertNotNull(item.get("id"));
        }
        Map<String, Object> item2 = items.stream()
                .filter(p -> "uuid-2".equals(p.get("id")))
                .findFirst().orElseThrow(() -> new AssertionError("应存在 todolist_item{id=uuid-2}"));
        Object deps = item2.get("depends_on");
        assertInstanceOf(List.class, deps);
        assertEquals(List.of("uuid-1"), deps);
    }

    @Test
    @DisplayName("T-LOOP-04: 无 todo 时 beforeInvoke 不发射 todolist 事件（降级）")
    void noTodos_noTodolistEvents() {
        when(taskPlanningRail.cachedTodos("test-conv-001")).thenReturn(List.of());
        rail.beforeInvoke(ctx(null));
        assertEquals(0, count("todolist_start"));
        assertEquals(1, count("conversation_start"));
    }

    @Test
    @DisplayName("T-LOOP-05: ask_user 中断未激活时不发射 interrupt_end（防御）")
    void interruptEndNotEmittedWithoutActiveStart() {
        AgentCallbackContext askCtx = ctx(toolInputs("ask_user"));
        askCtx.getExtra().put(ScriptConstants.KEY_SKIP_TOOL, Boolean.TRUE);
        rail.afterToolCall(askCtx);
        assertEquals(0, count("interrupt_end"));
    }

    @Test
    @DisplayName("T-LOOP-06: PENDING→CANCELLED 路径切换不发 todo_start/todo_end")
    void pathSwitch_pendingToCancelled_noStartEnd() {
        when(taskPlanningRail.cachedTodos("test-conv-001")).thenReturn(List.of(
                todo("t1", "推荐", TodoStatus.COMPLETED, List.of()),
                todo("t2", "筛选", TodoStatus.PENDING, List.of("t1"))));
        rail.beforeInvoke(ctx(null));
        assertEquals(0, count("todolist_start"));
        assertEquals(0, count("todo_start"));
        assertEquals(0, count("todo_end"));

        when(taskPlanningRail.cachedTodos("test-conv-001")).thenReturn(List.of(
                todo("t1", "推荐", TodoStatus.COMPLETED, List.of()),
                todo("t2", "筛选", TodoStatus.CANCELLED, List.of("t1"))));
        rail.afterToolCall(ctx(toolInputs("todo_modify")));

        assertEquals(0, count("todo_start"));
        assertEquals(0, count("todo_end"));
        assertEquals(1, count("todolist_start"));
    }

    @Test
    @DisplayName("T-LOOP-07: IN_PROGRESS→CANCELLED 发 todo_end(cancelled) 与 todo_start 配对")
    void inProgressToCancelled_emitsTodoEndCancelled() {
        when(taskPlanningRail.cachedTodos("test-conv-001")).thenReturn(List.of(
                todo("t1", "推荐", TodoStatus.IN_PROGRESS, List.of())));
        rail.beforeInvoke(ctx(null));

        when(taskPlanningRail.cachedTodos("test-conv-001")).thenReturn(List.of(
                todo("t1", "推荐", TodoStatus.CANCELLED, List.of())));
        rail.afterToolCall(ctx(toolInputs("todo_modify")));

        assertEquals(0, count("todo_start"));
        assertEquals(1, count("todo_end"));
        assertTrue(payloads.stream().anyMatch(p -> "todo_end".equals(p.get("event"))
                        && "t1".equals(p.get("id"))
                        && "cancelled".equals(p.get("status"))));
    }

    @Test
    @DisplayName("T-LOOP-08: 异常场景 onModelException 先关 think_end 再 error_event 再 conversation_end")
    void onModelException_closesThinkThenErrorThenConversation() {
        AssistantMessage thinkMsg = mock(AssistantMessage.class);
        when(thinkMsg.getFinishReason()).thenReturn("tool_calls");
        when(thinkMsg.getToolCalls()).thenReturn(List.of(mock(ToolCall.class)));
        when(thinkMsg.getReasoningContent()).thenReturn("正在思考");
        rail.beforeInvoke(ctx(null));
        rail.afterModelCall(ctx(modelInputs(thinkMsg)));

        rail.onModelException(ctx(modelInputs(null)));

        int errorIdx = events.indexOf("error_event");
        assertTrue(errorIdx >= 0);
        assertEquals("conversation_end", events.get(errorIdx + 1));
        assertEquals(count("think_start"), count("think_end"));
    }

    @Test
    @DisplayName("T-LOOP-09: 异常场景 onToolException(非中断) 先关 tool_end{failed} 再 error_event 再 conversation_end")
    void onToolException_closesToolThenErrorThenConversation() {
        rail.beforeInvoke(ctx(null));
        rail.beforeToolCall(ctx(toolInputs("call_versatile")));
        rail.onToolException(ctxWithException(toolInputs("call_versatile"), new RuntimeException("HTTP 500")));

        int toolEndIdx = events.indexOf("tool_end");
        int errorIdx = events.indexOf("error_event");
        assertTrue(toolEndIdx >= 0);
        assertTrue(errorIdx >= 0);
        assertTrue(toolEndIdx < errorIdx);
        assertEquals("conversation_end", events.get(errorIdx + 1));
        assertEquals(count("tool_start"), count("tool_end"));
        assertTrue(payloads.stream().anyMatch(p -> "tool_end".equals(p.get("event"))
                        && "failed".equals(p.get("status"))));
    }

    // ═══════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════

    private int count(String type) {
        int c = 0;
        for (String e : events) {
            if (type.equals(e)) {
                c++;
            }
        }
        return c;
    }

    private int firstIdx(String type) {
        int idx = events.indexOf(type);
        return idx < 0 ? Integer.MAX_VALUE : idx;
    }

    private int lastIdx(String type) {
        int idx = events.lastIndexOf(type);
        return idx < 0 ? Integer.MAX_VALUE : idx;
    }

    private AgentCallbackContext ctx(Object inputs) {
        return AgentCallbackContext.builder()
                .session(session)
                .inputs(inputs == null ? null : (com.openjiuwen.core.singleagent.rail.EventInputs) inputs)
                .extra(new LinkedHashMap<>())
                .build();
    }

    private AgentCallbackContext ctxWithException(Object inputs, Exception ex) {
        return AgentCallbackContext.builder()
                .session(session)
                .inputs(inputs == null ? null : (com.openjiuwen.core.singleagent.rail.EventInputs) inputs)
                .exception(ex)
                .extra(new LinkedHashMap<>())
                .build();
    }

    private ModelCallInputs modelInputs(AssistantMessage response) {
        return ModelCallInputs.builder().response(response).build();
    }

    private ToolCallInputs toolInputs(String toolName) {
        return ToolCallInputs.builder().toolName(toolName).toolArgs(Map.of()).build();
    }

    private AssistantMessage assistantWithReasoning(boolean finalAnswer, String reasoning) {
        AssistantMessage msg = mock(AssistantMessage.class);
        when(msg.getFinishReason()).thenReturn(finalAnswer ? "stop" : "tool_calls");
        when(msg.getToolCalls()).thenReturn(finalAnswer ? List.of() : List.of(mock(ToolCall.class)));
        when(msg.getReasoningContent()).thenReturn(reasoning);
        when(msg.getContentAsString()).thenReturn(finalAnswer ? "最终回答内容" : "");
        return msg;
    }

    private static TodoItem todo(String id, String content, TodoStatus status, List<String> dependsOn) {
        TodoItem item = TodoItem.builder()
                .id(id)
                .content(content)
                .activeForm("执行 " + content)
                .description(content)
                .dependsOn(dependsOn)
                .build();
        item.setStatus(status);
        return item;
    }
}
