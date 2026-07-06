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
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * EDPAgent 事件流 5 场景验收测试（设计文档第四章 §4.1~§4.5）。
 *
 * <p>严格按照 {@code EDPA_EventFlow_Design.md} 第四章定义的 5 个验收场景，
 * 驱动 EdpaEventRail 的 8 个 hook，捕获事件流并断言。</p>
 *
 * <p>使用 {@code @TestInstance(Lifecycle.PER_CLASS)} 保证 5 个场景按顺序执行且共享 rail 实例。</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EdpaEventRailScenarioTest {

    private List<String> events;
    private List<Map<String, Object>> payloads;
    private Session session;
    private TaskPlanningRail taskPlanningRail;
    private EdpaEventRail rail;
    private final AtomicReference<List<TodoItem>> currentTodos = new AtomicReference<>(List.of());

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        events = new ArrayList<>();
        payloads = new ArrayList<>();
        currentTodos.set(List.of());
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
        when(taskPlanningRail.cachedTodos(any())).thenAnswer(inv -> currentTodos.get());
        DeepAgent deepAgent = mock(DeepAgent.class);
        when(deepAgent.getRegisteredRails()).thenReturn(List.of(taskPlanningRail));
        rail = new EdpaEventRail(deepAgent);
    }

    private void clearEvents() {
        events.clear();
        payloads.clear();
    }

    @Test
    @DisplayName("S1: 你好 — 无工具无 todo，直接 final_answer")
    void scenario1_greeting() {
        currentTodos.set(List.of());
        rail.beforeInvoke(ctx(null));
        rail.beforeModelCall(ctx(modelInputs(msg("用户打招呼，直接回复", "你好！我是理财助手，可以帮您推荐和购买理财产品。", true))));
        rail.afterModelCall(ctx(modelInputs(msg("用户打招呼，直接回复", "你好！我是理财助手，可以帮您推荐和购买理财产品。", true))));
        rail.afterInvoke(ctx(null));

        assertSequence(List.of(
                "conversation_start",
                "think_start", "think_chunk", "think_end",
                "final_answer_start", "final_answer_chunk", "final_answer_end",
                "conversation_end"));
        assertPair("conversation");
        assertPair("think");
        assertPair("final_answer");
        assertEquals(0, count("todolist_start"));
        assertEquals(0, count("tool_start"));
        assertEquals(0, count("interrupt_start"));
        assertEquals(0, count("error_event"));
        assertChunkContent("think_chunk", "用户打招呼，直接回复");
        assertChunkContent("final_answer_chunk", "你好！我是理财助手，可以帮您推荐和购买理财产品。");
    }

    @Test
    @DisplayName("S2: 推荐理财 — todo_create 建表 + call_versatile + todo_modify + final_answer")
    void scenario2_recommendFinance() {
        currentTodos.set(List.of());
        rail.beforeInvoke(ctx(null));

        rail.beforeModelCall(ctx(modelInputs(msg("用户要推荐理财，先读 skill 文档了解流程", "", false))));
        rail.afterModelCall(ctx(modelInputs(msg("用户要推荐理财，先读 skill 文档了解流程", "", false))));

        rail.beforeModelCall(ctx(modelInputs(msg("已了解推荐流程，创建任务列表", "", false))));
        rail.afterModelCall(ctx(modelInputs(msg("已了解推荐流程，创建任务列表", "", false))));
        currentTodos.set(List.of(
                todo("t1", "推荐理财产品", TodoStatus.IN_PROGRESS, List.of()),
                todo("t2", "交互式理财筛选", TodoStatus.PENDING, List.of("t1")),
                todo("t3", "确定购买产品和金额", TodoStatus.PENDING, List.of("t2")),
                todo("t4", "查询理财账户余额,购买理财", TodoStatus.PENDING, List.of("t3"))));
        rail.afterToolCall(ctx(toolInputs("todo_create")));

        rail.beforeModelCall(ctx(modelInputs(msg("任务已创建，t1 进行中，调用推荐工作流", "", false))));
        rail.afterModelCall(ctx(modelInputs(msg("任务已创建，t1 进行中，调用推荐工作流", "", false))));
        rail.beforeToolCall(ctx(toolInputs("call_versatile")));
        rail.afterToolCall(ctx(toolInputs("call_versatile")));

        rail.beforeModelCall(ctx(modelInputs(msg("推荐成功返回，将 t1 标记为已完成", "", false))));
        rail.afterModelCall(ctx(modelInputs(msg("推荐成功返回，将 t1 标记为已完成", "", false))));
        currentTodos.set(List.of(
                todo("t1", "推荐理财产品", TodoStatus.COMPLETED, List.of()),
                todo("t2", "交互式理财筛选", TodoStatus.PENDING, List.of("t1")),
                todo("t3", "确定购买产品和金额", TodoStatus.PENDING, List.of("t2")),
                todo("t4", "查询理财账户余额,购买理财", TodoStatus.PENDING, List.of("t3"))));
        rail.afterToolCall(ctx(toolInputs("todo_modify")));

        rail.beforeModelCall(ctx(modelInputs(msg("t1 已完成，向用户展示推荐产品列表", "为您推荐以下理财产品：...", true))));
        rail.afterModelCall(ctx(modelInputs(msg("t1 已完成，向用户展示推荐产品列表", "为您推荐以下理财产品：...", true))));

        rail.afterInvoke(ctx(null));

        assertSequence(List.of(
                "conversation_start",
                "think_start", "think_chunk", "think_end",
                "think_start", "think_chunk", "think_end",
                "todolist_start", "todolist_item", "todolist_item", "todolist_item", "todolist_item", "todolist_end",
                "todo_start",
                "think_start", "think_chunk", "think_end",
                "tool_start", "tool_end",
                "think_start", "think_chunk", "think_end",
                "todo_end",
                "todolist_start", "todolist_item", "todolist_item", "todolist_item", "todolist_item", "todolist_end",
                "think_start", "think_chunk", "think_end",
                "final_answer_start", "final_answer_chunk", "final_answer_end",
                "conversation_end"));

        assertPair("conversation");
        assertEquals(5, count("think_start"));
        assertEquals(5, count("think_end"));
        assertPair("think");
        assertEquals(2, count("todolist_start"));
        assertTodolistPaired();
        assertEquals(8, count("todolist_item"));
        assertTodolistItemsSingleObject();
        assertNoCrossRoundTodolist();
        assertTodoEndBeforeTodolist();
        assertTodolistBeforeTodoStart();
        assertEquals(1, count("todo_start"));
        assertEquals(1, count("todo_end"));
        assertPair("todo");
        assertPair("tool");
        assertPair("final_answer");
        assertEquals(0, count("interrupt_start"));
        assertEquals(0, count("error_event"));
        assertTodoStartId("t1");
        assertTodoEndId("t1", "completed");
    }

    @Test
    @DisplayName("S3: 购买一号理财 — 路径切换 cancel t2 + call_versatile + ask_user 中断")
    void scenario3_buyProduct_pathSwitch() {
        currentTodos.set(List.of(
                todo("t1", "推荐理财产品", TodoStatus.COMPLETED, List.of()),
                todo("t2", "交互式理财筛选", TodoStatus.PENDING, List.of("t1")),
                todo("t3", "确定购买产品和金额", TodoStatus.PENDING, List.of("t2")),
                todo("t4", "查询理财账户余额,购买理财", TodoStatus.PENDING, List.of("t3"))));
        rail.beforeInvoke(ctx(null));

        rail.beforeModelCall(ctx(modelInputs(msg("用户直接选了一号产品→命中 shortcut_skip_filter→跳过 interact_finance_rec", "", false))));
        rail.afterModelCall(ctx(modelInputs(msg("用户直接选了一号产品→命中 shortcut_skip_filter→跳过 interact_finance_rec", "", false))));
        currentTodos.set(List.of(
                todo("t1", "推荐理财产品", TodoStatus.COMPLETED, List.of()),
                todo("t2", "交互式理财筛选", TodoStatus.CANCELLED, List.of("t1")),
                todo("t3", "确定购买产品和金额", TodoStatus.PENDING, List.of("t2")),
                todo("t4", "查询理财账户余额,购买理财", TodoStatus.PENDING, List.of("t3"))));
        rail.afterToolCall(ctx(toolInputs("todo_modify")));

        rail.beforeModelCall(ctx(modelInputs(msg("t2 已 cancel，直接执行 t3 product_select", "", false))));
        rail.afterModelCall(ctx(modelInputs(msg("t2 已 cancel，直接执行 t3 product_select", "", false))));
        currentTodos.set(List.of(
                todo("t1", "推荐理财产品", TodoStatus.COMPLETED, List.of()),
                todo("t2", "交互式理财筛选", TodoStatus.CANCELLED, List.of("t1")),
                todo("t3", "确定购买产品和金额", TodoStatus.IN_PROGRESS, List.of("t2")),
                todo("t4", "查询理财账户余额,购买理财", TodoStatus.PENDING, List.of("t3"))));
        rail.afterToolCall(ctx(toolInputs("todo_modify")));

        rail.beforeModelCall(ctx(modelInputs(msg("调用 product_select 确定购买一号产品", "", false))));
        rail.afterModelCall(ctx(modelInputs(msg("调用 product_select 确定购买一号产品", "", false))));
        rail.beforeToolCall(ctx(toolInputs("call_versatile")));
        rail.afterToolCall(ctx(toolInputs("call_versatile")));

        rail.beforeModelCall(ctx(modelInputs(msg("VA 返回缺金额→需要问用户购买金额", "", false))));
        rail.afterModelCall(ctx(modelInputs(msg("VA 返回缺金额→需要问用户购买金额", "", false))));
        ToolInterruptException interrupt = new ToolInterruptException(
                com.openjiuwen.core.singleagent.interrupt.InterruptRequest.builder().build(),
                new ToolCall("c1", "function", "ask_user", "{}", 0));
        rail.onToolException(ctxWithException(toolInputs("ask_user"), interrupt));

        rail.afterInvoke(ctx(null));

        assertSequence(List.of(
                "conversation_start",
                "think_start", "think_chunk", "think_end",
                "todolist_start", "todolist_item", "todolist_item", "todolist_item", "todolist_item", "todolist_end",
                "think_start", "think_chunk", "think_end",
                "todolist_start", "todolist_item", "todolist_item", "todolist_item", "todolist_item", "todolist_end",
                "todo_start",
                "think_start", "think_chunk", "think_end",
                "tool_start", "tool_end",
                "think_start", "think_chunk", "think_end",
                "interrupt_start",
                "conversation_end"));

        assertPair("conversation");
        assertEquals(4, count("think_start"));
        assertEquals(4, count("think_end"));
        assertPair("think");
        assertEquals(2, count("todolist_start"));
        assertTodolistPaired();
        assertEquals(8, count("todolist_item"));
        assertTodolistItemsSingleObject();
        assertNoCrossRoundTodolist();
        assertTodolistBeforeTodoStart();
        assertEquals(1, count("todo_start"));
        assertEquals(0, count("todo_end"));
        assertPair("tool");
        assertEquals(1, count("interrupt_start"));
        assertEquals(0, count("error_event"));
        assertTodoStartId("t3");
    }

    @Test
    @DisplayName("S4: 500000 — interrupt_end 恢复 + ask_user 二次中断")
    void scenario4_amountInput_secondInterrupt() {
        rail.beforeInvoke(ctx(null));
        ToolInterruptException interrupt = new ToolInterruptException(
                com.openjiuwen.core.singleagent.interrupt.InterruptRequest.builder().build(),
                new ToolCall("c1", "function", "ask_user", "{}", 0));
        rail.onToolException(ctxWithException(toolInputs("ask_user"), interrupt));
        rail.afterInvoke(ctx(null));
        clearEvents();

        currentTodos.set(List.of(
                todo("t1", "推荐理财产品", TodoStatus.COMPLETED, List.of()),
                todo("t2", "交互式理财筛选", TodoStatus.CANCELLED, List.of("t1")),
                todo("t3", "确定购买产品和金额", TodoStatus.IN_PROGRESS, List.of("t2")),
                todo("t4", "查询理财账户余额,购买理财", TodoStatus.PENDING, List.of("t3"))));
        rail.beforeInvoke(ctx(null));

        AgentCallbackContext askCtx = ctx(toolInputs("ask_user"));
        askCtx.getExtra().put(ScriptConstants.KEY_SKIP_TOOL, Boolean.TRUE);
        rail.afterToolCall(askCtx);

        rail.beforeModelCall(ctx(modelInputs(msg("用户金额50万，购买是敏感操作→需确认后再执行", "", false))));
        rail.afterModelCall(ctx(modelInputs(msg("用户金额50万，购买是敏感操作→需确认后再执行", "", false))));
        ToolInterruptException interrupt2 = new ToolInterruptException(
                com.openjiuwen.core.singleagent.interrupt.InterruptRequest.builder().build(),
                new ToolCall("c2", "function", "ask_user", "{}", 0));
        rail.onToolException(ctxWithException(toolInputs("ask_user"), interrupt2));

        rail.afterInvoke(ctx(null));

        assertSequence(List.of(
                "conversation_start",
                "interrupt_end",
                "think_start", "think_chunk", "think_end",
                "interrupt_start",
                "conversation_end"));

        assertPair("conversation");
        assertEquals(1, count("think_start"));
        assertEquals(1, count("think_end"));
        assertPair("think");
        assertEquals(0, count("todolist_start"));
        assertEquals(0, count("todolist_item"));
        assertNoCrossRoundTodolist();
        assertEquals(0, count("todo_start"));
        assertEquals(0, count("todo_end"));
        assertEquals(1, count("interrupt_start"));
        assertEquals(1, count("interrupt_end"));
        assertPair("interrupt");
        assertEquals(0, count("tool_start"));
        assertEquals(0, count("error_event"));
    }

    @Test
    @DisplayName("S5: 确认 — interrupt_end 恢复 + call_versatile 购买 + 全部完成 + final_answer")
    void scenario5_confirm_complete() {
        rail.beforeInvoke(ctx(null));
        ToolInterruptException interrupt = new ToolInterruptException(
                com.openjiuwen.core.singleagent.interrupt.InterruptRequest.builder().build(),
                new ToolCall("c2", "function", "ask_user", "{}", 0));
        rail.onToolException(ctxWithException(toolInputs("ask_user"), interrupt));
        rail.afterInvoke(ctx(null));
        clearEvents();

        currentTodos.set(List.of(
                todo("t1", "推荐理财产品", TodoStatus.COMPLETED, List.of()),
                todo("t2", "交互式理财筛选", TodoStatus.CANCELLED, List.of("t1")),
                todo("t3", "确定购买产品和金额", TodoStatus.IN_PROGRESS, List.of("t2")),
                todo("t4", "查询理财账户余额,购买理财", TodoStatus.PENDING, List.of("t3"))));
        rail.beforeInvoke(ctx(null));

        AgentCallbackContext askCtx = ctx(toolInputs("ask_user"));
        askCtx.getExtra().put(ScriptConstants.KEY_SKIP_TOOL, Boolean.TRUE);
        rail.afterToolCall(askCtx);

        rail.beforeModelCall(ctx(modelInputs(msg("用户已确认→t3 选品完成→标记 t3 COMPLETED", "", false))));
        rail.afterModelCall(ctx(modelInputs(msg("用户已确认→t3 选品完成→标记 t3 COMPLETED", "", false))));
        currentTodos.set(List.of(
                todo("t1", "推荐理财产品", TodoStatus.COMPLETED, List.of()),
                todo("t2", "交互式理财筛选", TodoStatus.CANCELLED, List.of("t1")),
                todo("t3", "确定购买产品和金额", TodoStatus.COMPLETED, List.of("t2")),
                todo("t4", "查询理财账户余额,购买理财", TodoStatus.PENDING, List.of("t3"))));
        rail.afterToolCall(ctx(toolInputs("todo_modify")));

        rail.beforeModelCall(ctx(modelInputs(msg("t3 完成，开始 t4 fund_planning→置 IN_PROGRESS", "", false))));
        rail.afterModelCall(ctx(modelInputs(msg("t3 完成，开始 t4 fund_planning→置 IN_PROGRESS", "", false))));
        currentTodos.set(List.of(
                todo("t1", "推荐理财产品", TodoStatus.COMPLETED, List.of()),
                todo("t2", "交互式理财筛选", TodoStatus.CANCELLED, List.of("t1")),
                todo("t3", "确定购买产品和金额", TodoStatus.COMPLETED, List.of("t2")),
                todo("t4", "查询理财账户余额,购买理财", TodoStatus.IN_PROGRESS, List.of("t3"))));
        rail.afterToolCall(ctx(toolInputs("todo_modify")));

        rail.beforeModelCall(ctx(modelInputs(msg("调用 fund_planning 执行购买", "", false))));
        rail.afterModelCall(ctx(modelInputs(msg("调用 fund_planning 执行购买", "", false))));
        rail.beforeToolCall(ctx(toolInputs("call_versatile")));
        rail.afterToolCall(ctx(toolInputs("call_versatile")));

        rail.beforeModelCall(ctx(modelInputs(msg("购买成功→标记 t4 COMPLETED", "", false))));
        rail.afterModelCall(ctx(modelInputs(msg("购买成功→标记 t4 COMPLETED", "", false))));
        currentTodos.set(List.of(
                todo("t1", "推荐理财产品", TodoStatus.COMPLETED, List.of()),
                todo("t2", "交互式理财筛选", TodoStatus.CANCELLED, List.of("t1")),
                todo("t3", "确定购买产品和金额", TodoStatus.COMPLETED, List.of("t2")),
                todo("t4", "查询理财账户余额,购买理财", TodoStatus.COMPLETED, List.of("t3"))));
        rail.afterToolCall(ctx(toolInputs("todo_modify")));

        rail.beforeModelCall(ctx(modelInputs(msg("所有任务完成，输出执行总结", "【需求概述】推荐并购买工银理财「添利宝」...", true))));
        rail.afterModelCall(ctx(modelInputs(msg("所有任务完成，输出执行总结", "【需求概述】推荐并购买工银理财「添利宝」...", true))));

        rail.afterInvoke(ctx(null));

        assertSequence(List.of(
                "conversation_start",
                "interrupt_end",
                "think_start", "think_chunk", "think_end",
                "todo_end",
                "todolist_start", "todolist_item", "todolist_item", "todolist_item", "todolist_item", "todolist_end",
                "think_start", "think_chunk", "think_end",
                "todolist_start", "todolist_item", "todolist_item", "todolist_item", "todolist_item", "todolist_end",
                "todo_start",
                "think_start", "think_chunk", "think_end",
                "tool_start", "tool_end",
                "think_start", "think_chunk", "think_end",
                "todo_end",
                "todolist_start", "todolist_item", "todolist_item", "todolist_item", "todolist_item", "todolist_end",
                "think_start", "think_chunk", "think_end",
                "final_answer_start", "final_answer_chunk", "final_answer_end",
                "conversation_end"));

        assertPair("conversation");
        assertEquals(5, count("think_start"));
        assertEquals(5, count("think_end"));
        assertPair("think");
        assertEquals(3, count("todolist_start"));
        assertTodolistPaired();
        assertEquals(12, count("todolist_item"));
        assertTodolistItemsSingleObject();
        assertNoCrossRoundTodolist();
        assertTodoEndBeforeTodolist();
        assertTodolistBeforeTodoStart();
        assertEquals(1, count("todo_start"));
        assertEquals(2, count("todo_end"));
        assertPair("tool");
        assertPair("final_answer");
        assertEquals(1, count("interrupt_end"));
        assertEquals(0, count("interrupt_start"));
        assertEquals(0, count("error_event"));
        assertTodoEndId("t3", "completed");
        assertTodoStartId("t4");
        assertTodoEndId("t4", "completed");
    }

    @Test
    @DisplayName("E1: LLM 调用失败 — think_end + error_event{model} + conversation_end")
    void scenarioE1_modelException() {
        currentTodos.set(List.of());
        rail.beforeInvoke(ctx(null));

        AssistantMessage thinkMsg = mock(AssistantMessage.class);
        when(thinkMsg.getFinishReason()).thenReturn("tool_calls");
        when(thinkMsg.getToolCalls()).thenReturn(List.of(mock(ToolCall.class)));
        when(thinkMsg.getReasoningContent()).thenReturn("任务已创建...");
        rail.afterModelCall(ctx(modelInputs(thinkMsg)));

        rail.onModelException(ctx(modelInputs(null)));

        rail.afterInvoke(ctx(null));

        assertPair("think");
        assertPair("conversation");
        int errorIdx = events.indexOf("error_event");
        assertTrue(errorIdx >= 0, "应发射 error_event");
        assertTrue(payloads.stream().anyMatch(p -> "error_event".equals(p.get("event"))
                && "model".equals(p.get("stage"))), "error_event 应 stage=model");
    }

    @Test
    @DisplayName("E2: 业务工具执行失败 — tool_end{failed} + error_event{tool} + conversation_end")
    void scenarioE2_toolException() {
        currentTodos.set(List.of());
        rail.beforeInvoke(ctx(null));

        rail.beforeModelCall(ctx(modelInputs(msg("调用推荐工作流", "", false))));
        rail.afterModelCall(ctx(modelInputs(msg("调用推荐工作流", "", false))));

        rail.beforeToolCall(ctx(toolInputs("call_versatile")));
        rail.onToolException(ctxWithException(toolInputs("call_versatile"), new RuntimeException("HTTP 500")));

        rail.afterInvoke(ctx(null));

        assertPair("tool");
        assertPair("think");
        assertPair("conversation");
        assertTrue(payloads.stream().anyMatch(p -> "tool_end".equals(p.get("event"))
                && "failed".equals(p.get("status"))), "tool_end 应 status=failed");
        int errorIdx = events.indexOf("error_event");
        assertTrue(errorIdx >= 0, "应发射 error_event");
        assertTrue(payloads.stream().anyMatch(p -> "error_event".equals(p.get("event"))
                && "tool".equals(p.get("stage"))), "error_event 应 stage=tool");
    }

    // ═══════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════

    private int count(String type) {
        int c = 0;
        for (String e : events) {
            if (type.equals(e)) c++;
        }
        return c;
    }

    private void assertSequence(List<String> expected) {
        assertEquals(expected.size(), events.size(),
                "事件数不匹配: 期望 " + expected.size() + " 实际 " + events.size() + "\n期望: " + expected + "\n实际: " + events);
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), events.get(i),
                    "事件序列第 " + i + " 位不匹配: 期望 " + expected.get(i) + " 实际 " + events.get(i) + "\n期望: " + expected + "\n实际: " + events);
        }
    }

    private void assertPair(String prefix) {
        int start = count(prefix + "_start");
        int end = count(prefix + "_end");
        assertEquals(start, end, prefix + "_start(" + start + ") 应 == " + prefix + "_end(" + end + ")");
    }

    private void assertTodolistPaired() {
        assertEquals(count("todolist_start"), count("todolist_end"),
                "todolist_start 应 == todolist_end");
    }

    private void assertTodolistItemsSingleObject() {
        for (Map<String, Object> p : payloads) {
            if ("todolist_item".equals(p.get("event"))) {
                assertFalse(p.containsKey("tasks"),
                        "todolist_item 应为单对象（无 tasks 数组，Rule 12/C9）: " + p);
                assertNotNull(p.get("id"),
                        "todolist_item 应含 id（单条，Rule 12/C9）");
            }
        }
    }

    private void assertNoCrossRoundTodolist() {
        int csIdx = events.indexOf("conversation_start");
        assertTrue(csIdx >= 0, "应存在 conversation_start");
        if (csIdx + 1 < events.size()) {
            assertNotEquals("todolist_start", events.get(csIdx + 1),
                    "conversation_start 后不应紧跟 todolist_start（Rule 9，无跨轮 todolist）");
        }
    }

    private void assertTodoEndBeforeTodolist() {
        boolean found = false;
        for (int i = 0; i + 1 < events.size(); i++) {
            if ("todo_end".equals(events.get(i)) && "todolist_start".equals(events.get(i + 1))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "end 转移应有 todo_end 紧接 todolist_start（Rule 10）");
    }

    private void assertTodolistBeforeTodoStart() {
        boolean found = false;
        for (int i = 0; i + 1 < events.size(); i++) {
            if ("todolist_end".equals(events.get(i)) && "todo_start".equals(events.get(i + 1))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "start 转移应有 todolist_end 紧接 todo_start（Rule 11）");
    }

    private void assertChunkContent(String eventType, String expectedContent) {
        assertTrue(payloads.stream().anyMatch(p -> eventType.equals(p.get("event"))
                && expectedContent.equals(p.get("content"))), eventType + " 应携带 content=" + expectedContent);
    }

    private void assertTodoStartId(String id) {
        assertTrue(payloads.stream().anyMatch(p -> "todo_start".equals(p.get("event"))
                && id.equals(p.get("id"))), "应存在 todo_start{id=" + id + "}");
    }

    private void assertTodoEndId(String id, String status) {
        assertTrue(payloads.stream().anyMatch(p -> "todo_end".equals(p.get("event"))
                && id.equals(p.get("id"))
                && status.equals(p.get("status"))), "应存在 todo_end{id=" + id + ", status=" + status + "}");
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

    private AssistantMessage msg(String reasoning, String content, boolean finalAnswer) {
        AssistantMessage m = mock(AssistantMessage.class);
        when(m.getFinishReason()).thenReturn(finalAnswer ? "stop" : "tool_calls");
        when(m.getToolCalls()).thenReturn(finalAnswer ? List.of() : List.of(mock(ToolCall.class)));
        when(m.getReasoningContent()).thenReturn(reasoning);
        when(m.getContentAsString()).thenReturn(content);
        return m;
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
