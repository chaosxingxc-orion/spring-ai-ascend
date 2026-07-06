package com.huawei.ascend.edp.rail;

import com.huawei.ascend.edp.config.SysScriptsConfig;
import com.huawei.ascend.edp.config.ScriptConstants;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * EdpaEventRail 话术承载测试（F3-fix：ask_user 话术在 onToolException 解析）。
 *
 * <p>验证 {@code EdpaEventRail.onToolException} 对 ask_user 中断的话术承载：
 * 解析 {@code response_template_status/keys/vars} → 填 {@code interrupt_start.content}。
 * 承载点为 onToolException（异常处理回调，必定触发），而非 beforeToolCall（被 85 抛异常中断、不可达）。</p>
 */
class EdpaEventRailScriptTest {

    private List<String> events;
    private List<Map<String, Object>> payloads;
    private Session session;
    private DeepAgent deepAgent;
    private EdpaEventRail rail;
    private final AtomicReference<List<com.openjiuwen.harness.tools.TodoItem>> currentTodos =
            new AtomicReference<>(List.of());

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        events = new ArrayList<>();
        payloads = new ArrayList<>();
        currentTodos.set(List.of());
        session = mock(Session.class);
        when(session.getSessionId()).thenReturn("script-conv-001");
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

        TaskPlanningRail taskPlanningRail = mock(TaskPlanningRail.class);
        when(taskPlanningRail.cachedTodos(any())).thenAnswer(inv -> currentTodos.get());
        deepAgent = mock(DeepAgent.class);
        when(deepAgent.getRegisteredRails()).thenReturn(List.of(taskPlanningRail));

        rail = new EdpaEventRail(deepAgent, scriptsConfig());
    }

    /** 本期话术配置（与 scenarios/wealth-demo/ScriptsConfig.yaml 一致）。 */
    private SysScriptsConfig scriptsConfig() {
        Map<String, String> flat = new LinkedHashMap<>();
        flat.put("interrupt_start", "需要您确认以下信息");
        flat.put("product_select_confirm", "确认购买以下产品：{productName}，金额 {amount} 元。");
        flat.put("product_select_missing_amount", "请告诉我您想购买的金额。");
        flat.put("product_select_missing_product", "请问您想购买哪款理财产品？");
        flat.put("out_of_scope", "当前请求暂不在可处理范围内。");
        flat.put("tool_start", "正在调用：{tool_name}");
        flat.put("tool_end", "{tool_name} 执行完成");
        SysScriptsConfig cfg = new SysScriptsConfig();
        cfg.mergeSkillScripts(flat);
        return cfg;
    }

    // ═══════════════════════════════════════════════════
    // S-A01 选品确认（status=confirm → product_select_confirm，变量渲染）
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("S-A01: ask_user confirm → interrupt_start.content = product_select_confirm 渲染")
    void askUserConfirm_rendersBusinessScript() {
        rail.beforeInvoke(ctx(null));
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("response_template_status", "confirm");
        args.put("response_template_keys", Map.of("confirm", "product_select_confirm"));
        args.put("response_template_vars", Map.of("productName", "一号产品", "amount", "500000"));
        ToolInterruptException ex = askUserInterrupt();
        rail.onToolException(ctxWithException(toolInputs("ask_user", args), ex));
        rail.afterInvoke(ctx(null));

        assertEquals(1, count("interrupt_start"), "应发 1 次 interrupt_start");
        assertEventContent("interrupt_start",
                "确认购买以下产品：一号产品，金额 500000 元。");
    }

    // ═══════════════════════════════════════════════════
    // S-A02 缺金额（status=missing_amount → product_select_missing_amount）
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("S-A02: ask_user missing_amount → interrupt_start.content = 缺金额话术")
    void askUserMissingAmount_rendersScript() {
        rail.beforeInvoke(ctx(null));
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("response_template_status", "missing_amount");
        args.put("response_template_keys", Map.of("missing_amount", "product_select_missing_amount"));
        rail.onToolException(ctxWithException(toolInputs("ask_user", args), askUserInterrupt()));
        rail.afterInvoke(ctx(null));

        assertEventContent("interrupt_start", "请告诉我您想购买的金额。");
    }

    // ═══════════════════════════════════════════════════
    // S-A03 超范围（status=out_of_scope → out_of_scope 文案）
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("S-A03: ask_user out_of_scope → interrupt_start.content = 超范围话术")
    void askUserOutOfScope_rendersScript() {
        rail.beforeInvoke(ctx(null));
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("response_template_status", "out_of_scope");
        args.put("response_template_keys", Map.of("out_of_scope", "out_of_scope"));
        rail.onToolException(ctxWithException(toolInputs("ask_user", args), askUserInterrupt()));
        rail.afterInvoke(ctx(null));

        assertEventContent("interrupt_start", "当前请求暂不在可处理范围内。");
    }

    // ═══════════════════════════════════════════════════
    // S-A04 无话术参数（缺 response_template_* → 回落 interrupt_start 兜底）
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("S-A04: ask_user 无 response_template 参数 → 回落 interrupt_start 兜底文案")
    void askUserNoTemplate_fallsBackToDefault() {
        rail.beforeInvoke(ctx(null));
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("question", "请提供金额");
        rail.onToolException(ctxWithException(toolInputs("ask_user", args), askUserInterrupt()));
        rail.afterInvoke(ctx(null));

        assertEquals(1, count("interrupt_start"));
        assertEventContent("interrupt_start", "需要您确认以下信息");
    }

    // ═══════════════════════════════════════════════════
    // S-A05 配置缺位（keys 命中配置外 key → 回落兜底，不误发）
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("S-A05: ask_user 命中配置外 key → 回落 interrupt_start 兜底（不抛异常）")
    void askUserConfigMissing_fallsBackSafely() {
        rail.beforeInvoke(ctx(null));
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("response_template_status", "confirm");
        args.put("response_template_keys", Map.of("confirm", "not_in_config_xyz"));
        rail.onToolException(ctxWithException(toolInputs("ask_user", args), askUserInterrupt()));
        rail.afterInvoke(ctx(null));

        assertEquals(1, count("interrupt_start"), "配置缺位仍发 interrupt_start（兜底文案）");
        assertEventContent("interrupt_start", "需要您确认以下信息");
    }

    // ═══════════════════════════════════════════════════
    // S-A06 容错（response_template_keys 为非法 JSON 含中文引号 → coerceJsonMap 命中）
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("S-A06: response_template_keys 含中文引号非法 JSON → coerceJsonMap 容错命中")
    void askUserMalformedKeys_coercedAndResolved() {
        rail.beforeInvoke(ctx(null));
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("response_template_status", "confirm");
        args.put("response_template_keys", "\u201cconfirm\u201d\uff1a\u201cproduct_select_confirm\u201d");
        args.put("response_template_vars", Map.of("productName", "一号产品", "amount", "500000"));
        rail.onToolException(ctxWithException(toolInputs("ask_user", args), askUserInterrupt()));
        rail.afterInvoke(ctx(null));

        assertEquals(1, count("interrupt_start"));
        assertEventContent("interrupt_start",
                "确认购买以下产品：一号产品，金额 500000 元。");
    }

    // ═══════════════════════════════════════════════════
    // S-A07 scripts=null 退化安全（content=空串，不抛异常）
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("S-A07: scripts=null → interrupt_start.content=空串，不抛异常（退化安全）")
    void askUserNullScripts_degradesSafely() {
        rail = new EdpaEventRail(deepAgent, null);
        rail.beforeInvoke(ctx(null));
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("response_template_status", "confirm");
        args.put("response_template_keys", Map.of("confirm", "product_select_confirm"));
        rail.onToolException(ctxWithException(toolInputs("ask_user", args), askUserInterrupt()));
        rail.afterInvoke(ctx(null));

        assertEquals(1, count("interrupt_start"));
        assertEventContent("interrupt_start", "");
    }

    // ═══════════════════════════════════════════════════
    // S-A08 interrupt_start/interrupt_end 跨轮配对（话术不破坏配对）
    // ═══════════════════════════════════════════════════

    @Test
    @DisplayName("S-A08: interrupt_start(话术) → 恢复 interrupt_end 配对不破坏")
    void interruptPair_preservedWithScript() {
        rail.beforeInvoke(ctx(null));
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("response_template_status", "confirm");
        args.put("response_template_keys", Map.of("confirm", "product_select_confirm"));
        args.put("response_template_vars", Map.of("productName", "一号产品", "amount", "500000"));
        rail.onToolException(ctxWithException(toolInputs("ask_user", args), askUserInterrupt()));
        rail.afterInvoke(ctx(null));
        events.clear();
        payloads.clear();

        // 第2轮：ask_user 恢复 → interrupt_end
        rail.beforeInvoke(ctx(null));
        AgentCallbackContext resumeCtx = ctx(toolInputs("ask_user", Map.of()));
        resumeCtx.getExtra().put(ScriptConstants.KEY_SKIP_TOOL, Boolean.TRUE);
        rail.afterToolCall(resumeCtx);
        rail.afterInvoke(ctx(null));

        assertEquals(1, count("interrupt_end"), "恢复应发 interrupt_end");
        assertEquals(0, count("interrupt_start"), "恢复轮不发 interrupt_start");
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

    private void assertEventContent(String eventType, String expected) {
        assertTrue(payloads.stream().anyMatch(p -> eventType.equals(p.get("event"))
                        && expected.equals(p.get("content"))),
                eventType + " 应携带 content=" + expected + "，实际 payloads=" + payloads);
    }

    private ToolInterruptException askUserInterrupt() {
        return new ToolInterruptException(
                InterruptRequest.builder().build(),
                new ToolCall("c1", "function", "ask_user", "{}", 0));
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

    private ToolCallInputs toolInputs(String toolName, Map<String, Object> args) {
        return ToolCallInputs.builder().toolName(toolName).toolArgs(args).build();
    }
}
