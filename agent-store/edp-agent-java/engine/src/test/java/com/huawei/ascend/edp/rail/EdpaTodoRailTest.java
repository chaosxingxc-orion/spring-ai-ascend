package com.huawei.ascend.edp.rail;

import com.huawei.ascend.edp.config.ActRuleConfig;
import com.huawei.ascend.edp.config.EdpaTodolist;
import com.huawei.ascend.edp.config.EdpaTodolist.DynamicPath;
import com.huawei.ascend.edp.config.ScriptConstants;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.tools.TodoItem;
import com.openjiuwen.harness.tools.TodoStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * EdpaTodoRail 单测（设计 T-D10 / T-R / v2 §10.4 依赖闭环）。
 */
class EdpaTodoRailTest {

    @Test
    void buildPathRulesPrompt_containsAllRules() {
        List<DynamicPath> paths = List.of(
                new DynamicPath("shortcut_skip_filter",
                        "用户首次推荐即明确选择，跳过交互式筛选",
                        "product_recommend 返回结果中用户已明确选择某个产品",
                        List.of("interact_finance_rec"),
                        "product_recommend → product_select"),
                new DynamicPath("skip_purchase_if_no_balance",
                        "账户余额为零，跳过购买步骤",
                        "fund_planning 查询余额结果为 0",
                        List.of("fund_planning"),
                        "product_select → 结束"));

        String prompt = EdpaTodoRail.buildPathRulesPrompt(paths);

        assertTrue(prompt.contains("动态路径选择规则"));
        assertTrue(prompt.contains("shortcut_skip_filter"));
        assertTrue(prompt.contains("interact_finance_rec"));
        assertTrue(prompt.contains("cancelled"));
        assertTrue(prompt.contains("决策原则"));
    }

    @Test
    void buildPathRulesPrompt_empty() {
        assertEquals("", EdpaTodoRail.buildPathRulesPrompt(List.of()));
        assertEquals("", EdpaTodoRail.buildPathRulesPrompt(null));
    }

    @Test
    void priority_is95() {
        EdpaTodoRail rail = new EdpaTodoRail(null, makeEmptyTodolist(), null);
        assertEquals(95, rail.priority());
    }

    @Test
    void resolveDependencyMap_replacesCatalogIdWithUuid() {
        EdpaTodolist todolist = makeTwoEntryTodolist();
        Map<String, String> anchors = Map.of("a", "uuid-a", "b", "uuid-b");
        Map<String, List<String>> depMap = EdpaTodoRail.resolveDependencyMap(anchors, todolist);
        assertEquals(List.of(), depMap.get("uuid-a"));
        assertEquals(List.of("uuid-a"), depMap.get("uuid-b"));
    }

    @Test
    void resolveDependencyMap_failFastWhenDepMissing() {
        EdpaTodolist todolist = makeTwoEntryTodolist();
        Map<String, String> anchors = Map.of("b", "uuid-b");
        assertThrows(IllegalStateException.class,
                () -> EdpaTodoRail.resolveDependencyMap(anchors, todolist));
    }

    @Test
    void applyDependencies_setsUuidDeps() {
        TodoItem a = TodoItem.builder().id("uuid-a").content("a").metaData(Map.of("catalog_id", "a")).build();
        a.setStatus(TodoStatus.PENDING);
        TodoItem b = TodoItem.builder().id("uuid-b").content("b").metaData(Map.of("catalog_id", "b")).build();
        b.setStatus(TodoStatus.PENDING);

        EdpaTodolist todolist = makeTwoEntryTodolist();
        Map<String, String> anchors = EdpaTodoRail.buildAnchors(List.of(a, b));
        Map<String, List<String>> depMap = EdpaTodoRail.resolveDependencyMap(anchors, todolist);

        boolean changed = EdpaTodoRail.applyDependencies(List.of(a, b), depMap);
        assertTrue(changed);
        assertEquals(List.of("uuid-a"), b.getDependsOn());
    }

    @Test
    void buildAnchors_fromMetaData() {
        TodoItem item = TodoItem.builder().id("uuid-x").content("x")
                .metaData(Map.of("catalog_id", "product_recommend")).build();
        Map<String, String> anchors = EdpaTodoRail.buildAnchors(List.of(item));
        assertEquals("uuid-x", anchors.get("product_recommend"));
    }

    @Test
    void guard_blocksBusinessToolWhenNoTodosPlanned() {
        DeepAgent deepAgent = mock(DeepAgent.class);
        TaskPlanningRail tpr = mock(TaskPlanningRail.class);
        when(deepAgent.getRegisteredRails()).thenReturn(List.of(tpr));
        when(tpr.cachedTodos("conv-1")).thenReturn(List.of());
        Session session = mock(Session.class);
        when(session.getSessionId()).thenReturn("conv-1");

        EdpaTodoRail rail = new EdpaTodoRail(deepAgent, makeTwoEntryTodolist(), null);
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolName("call_mcp").toolArgs(Map.of())
                .toolCall(new ToolCall("c1", "function", "call_mcp", "{}", 0)).build();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .session(session).inputs(inputs).extra(new LinkedHashMap<>()).build();

        rail.beforeToolCall(ctx);

        assertEquals(Boolean.TRUE, ctx.getExtra().get(ScriptConstants.KEY_SKIP_TOOL), "无 todo 时业务工具应被拦截");
        assertNotNull(inputs.getToolResult(), "应注入 PLAN_FIRST 合成结果");
        assertTrue(String.valueOf(inputs.getToolResult()).contains("PLAN_FIRST"));
    }

    @Test
    void guard_allowsBusinessToolWhenTodosPlanned() {
        DeepAgent deepAgent = mock(DeepAgent.class);
        TaskPlanningRail tpr = mock(TaskPlanningRail.class);
        when(deepAgent.getRegisteredRails()).thenReturn(List.of(tpr));
        TodoItem planned = TodoItem.builder().id("u1").content("x").build();
        planned.setStatus(TodoStatus.IN_PROGRESS);
        when(tpr.cachedTodos("conv-2")).thenReturn(List.of(planned));
        Session session = mock(Session.class);
        when(session.getSessionId()).thenReturn("conv-2");

        EdpaTodoRail rail = new EdpaTodoRail(deepAgent, makeTwoEntryTodolist(), null);
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolName("call_versatile").toolArgs(Map.of())
                .toolCall(new ToolCall("c2", "function", "call_versatile", "{}", 0)).build();
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .session(session).inputs(inputs).extra(new LinkedHashMap<>()).build();

        rail.beforeToolCall(ctx);

        assertNull(ctx.getExtra().get(ScriptConstants.KEY_SKIP_TOOL), "已规划时业务工具应放行");
        assertNull(inputs.getToolResult());
    }

    private static EdpaTodolist makeEmptyTodolist() {
        ActRuleConfig.TodolistEntry entry = new ActRuleConfig.TodolistEntry();
        entry.setCatalogId("x");
        entry.setContent("x");
        entry.setDependsOn(List.of());
        return new EdpaTodolist(List.of(entry), List.of());
    }

    private static EdpaTodolist makeTwoEntryTodolist() {
        ActRuleConfig.TodolistEntry a = new ActRuleConfig.TodolistEntry();
        a.setCatalogId("a");
        a.setContent("a");
        a.setDependsOn(List.of());
        ActRuleConfig.TodolistEntry b = new ActRuleConfig.TodolistEntry();
        b.setCatalogId("b");
        b.setContent("b");
        b.setDependsOn(List.of("a"));
        return new EdpaTodolist(List.of(a, b), List.of());
    }
}
