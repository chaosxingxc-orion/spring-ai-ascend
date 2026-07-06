package com.huawei.ascend.edp.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ScriptResolver} 单元测试。
 *
 * <p>覆盖：SafeFormat 渲染、生命周期快捷方法、{@link ScriptResolver#coerceJsonMap(Object)}
 * 容错（中文引号/冒号、含 {@code "} 商品名）、Prompt 文本生成。</p>
 */
class ScriptResolverTest {

    @Test
    void resolve_nullTemplate_returnsEmpty() {
        assertEquals("", ScriptResolver.resolve(null, Map.of("a", "b")));
        assertEquals("", ScriptResolver.resolve("", Map.of("a", "b")));
    }

    @Test
    void resolve_missingVar_becomesEmptyString() {
        // 缺失变量 → 空串，不抛异常（对齐 Python _SafeDict）
        assertEquals("hello ", ScriptResolver.resolve("hello {name}", Map.of()));
        assertEquals("hello ", ScriptResolver.resolve("hello {name}", null));
    }

    @Test
    void resolve_replacesVars() {
        assertEquals("购买：一号产品，金额 500000 元",
                ScriptResolver.resolve("购买：{product}，金额 {amount} 元",
                        Map.of("product", "一号产品", "amount", "500000")));
    }

    @Test
    void resolve_valueContainingSpecialRegexChars_isLiteral() {
        // 变量值含 $ \ 等正则元字符，必须按字面量替换（Matcher.quoteReplacement）
        assertEquals("cost=$1.00", ScriptResolver.resolve("cost={p}", Map.of("p", "$1.00")));
    }

    @Test
    void resolveByConfig_missingKey_returnsEmpty() {
        SysScriptsConfig cfg = new SysScriptsConfig();
        assertEquals("", ScriptResolver.resolve(cfg, "no_such_key", Map.of()));
    }

    @Test
    void lifecycleShortcuts_renderFromConfig() {
        SysScriptsConfig cfg = loadCfg(Map.of(
                EdpaEventType.TOOL_START.wireName(), "正在调用：{tool_name}",
                EdpaEventType.TOOL_END.wireName(), "{tool_name} 执行完成",
                EdpaEventType.TODO_START.wireName(), "开始执行：{title}",
                EdpaEventType.TODO_END.wireName(), "{title} 已完成",
                EdpaEventType.TODOLIST_START.wireName(), "已生成任务规划",
                EdpaEventType.TODOLIST_END.wireName(), "任务规划完成",
                EdpaEventType.INTERRUPT_START.wireName(), "需要您确认以下信息"));
        assertEquals("正在调用：call_versatile", ScriptResolver.toolStart(cfg, "call_versatile"));
        assertEquals("call_versatile 执行完成", ScriptResolver.toolEnd(cfg, "call_versatile"));
        assertEquals("开始执行：推荐理财", ScriptResolver.todoStart(cfg, "推荐理财"));
        assertEquals("推荐理财 已完成", ScriptResolver.todoEnd(cfg, "推荐理财"));
        assertEquals("已生成任务规划", ScriptResolver.todolistStart(cfg));
        assertEquals("任务规划完成", ScriptResolver.todolistEnd(cfg));
        assertEquals("需要您确认以下信息", ScriptResolver.interruptStart(cfg));
    }

    @Test
    void lifecycleShortcuts_nullConfig_returnsEmpty() {
        assertEquals("", ScriptResolver.toolStart(null, "x"));
        assertEquals("", ScriptResolver.todolistStart(null));
        assertEquals("", ScriptResolver.interruptStart(null));
    }

    @Test
    void coerceJsonMap_alreadyMap() {
        Map<String, String> out = ScriptResolver.coerceJsonMap(Map.of("confirm", "product_select_confirm", "amount", "500000"));
        assertEquals("product_select_confirm", out.get("confirm"));
        assertEquals("500000", out.get("amount"));
    }

    @Test
    void coerceJsonMap_validJsonString() {
        Map<String, String> out = ScriptResolver.coerceJsonMap("{\"confirm\":\"product_select_confirm\"}");
        assertEquals("product_select_confirm", out.get("confirm"));
    }

    @Test
    void coerceJsonMap_chineseQuotesAndColon() {
        // LLM 常输出中文引号/冒号，必须归一后解析
        Map<String, String> out = ScriptResolver.coerceJsonMap("\u201cconfirm\u201d\uff1a\u201cproduct_select_confirm\u201d");
        assertEquals("product_select_confirm", out.get("confirm"));
    }

    @Test
    void coerceJsonMap_valueWithEmbeddedDoubleQuote_regexFallback() {
        // 商品名含 "，JSON 非法 → 正则兜底逐对抓取
        Map<String, String> out = ScriptResolver.coerceJsonMap("{\"productName\":\"\u4e00\u53f7\"\u7406\u8d22\"\u4ea7\u54c1\",\"amount\":\"500000\"}");
        assertEquals("500000", out.get("amount"));
        assertFalse(out.get("productName").isBlank(), "\u6b63\u5219\u5151\u5e95\u5e94\u6293\u5230 productName");
    }

    @Test
    void coerceJsonMap_nullOrEmpty_returnsEmptyMap() {
        assertTrue(ScriptResolver.coerceJsonMap(null).isEmpty());
        assertTrue(ScriptResolver.coerceJsonMap("").isEmpty());
    }

    @Test
    void promptGeneration_onlyIncludesConfiguredKeys() {
        SysScriptsConfig withCancel = loadCfg(Map.of(
                ScriptConstants.SCRIPT_TASK_CANCELLED, "\u5df2\u53d6\u6d88",
                ScriptConstants.SCRIPT_OUT_OF_SCOPE, "\u8d85\u8303\u56f4"));
        String prompt = ScriptResolver.cancelRulesPrompt(withCancel);
        assertTrue(prompt.contains(ScriptConstants.SCRIPT_TASK_CANCELLED));
        assertTrue(prompt.contains(ScriptConstants.SCRIPT_OUT_OF_SCOPE));
        assertFalse(prompt.contains(ScriptConstants.SCRIPT_CANCEL_CONFIRM), "\u672a\u914d\u7f6e\u7684 key \u4e0d\u5e94\u51fa\u73b0");

        // 空配置 → Prompt 段仍可生成（无 key 列表）
        assertTrue(ScriptResolver.cancelRulesPrompt(new SysScriptsConfig()).startsWith("## 取消/超范围话术规则"));
        // null 配置 → 空串
        assertEquals("", ScriptResolver.cancelRulesPrompt(null));
        assertEquals("", ScriptResolver.businessRulesPrompt(null));
    }

    // ═══════════════════════════════════════════════════
    // resolveAskUser（F3-fix 核心逻辑，供 EdpaEventRail.onToolException 调用）
    // ═══════════════════════════════════════════════════

    private SysScriptsConfig askUserCfg() {
        return loadCfg(new LinkedHashMap<>(Map.of(
                ScriptConstants.SCRIPT_PRODUCT_SELECT_CONFIRM, "确认购买以下产品：{productName}，金额 {amount} 元。",
                ScriptConstants.SCRIPT_PRODUCT_SELECT_MISSING_AMOUNT, "请告诉我您想购买的金额。",
                ScriptConstants.SCRIPT_OUT_OF_SCOPE, "当前请求暂不在可处理范围内。",
                EdpaEventType.INTERRUPT_START.wireName(), "需要您确认以下信息")));
    }

    @Test
    void resolveAskUser_confirm_rendersAndWritesExtra() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put(ScriptConstants.PARAM_RESPONSE_TEMPLATE_STATUS, ScriptConstants.STATUS_CONFIRM);
        args.put(ScriptConstants.PARAM_RESPONSE_TEMPLATE_KEYS, Map.of(ScriptConstants.STATUS_CONFIRM, ScriptConstants.SCRIPT_PRODUCT_SELECT_CONFIRM));
        args.put(ScriptConstants.PARAM_RESPONSE_TEMPLATE_VARS, Map.of("productName", "一号产品", "amount", "500000"));
        Map<String, Object> extra = new LinkedHashMap<>();

        boolean hit = ScriptResolver.resolveAskUser(askUserCfg(), args, extra);

        assertTrue(hit);
        assertEquals("确认购买以下产品：一号产品，金额 500000 元。", extra.get(ScriptConstants.KEY_RESPONSE_TEMPLATE));
        assertEquals(ScriptConstants.SCRIPT_PRODUCT_SELECT_CONFIRM, extra.get(ScriptConstants.KEY_LAST_SCRIPT));
        assertNotNull(extra.get(ScriptConstants.KEY_SELECTED_PRODUCT), "confirm 应落 _edp_selected_product");
    }

    @Test
    void resolveAskUser_missingAmount_writesScript() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put(ScriptConstants.PARAM_RESPONSE_TEMPLATE_STATUS, ScriptConstants.STATUS_MISSING_AMOUNT);
        args.put(ScriptConstants.PARAM_RESPONSE_TEMPLATE_KEYS, Map.of(ScriptConstants.STATUS_MISSING_AMOUNT, ScriptConstants.SCRIPT_PRODUCT_SELECT_MISSING_AMOUNT));
        Map<String, Object> extra = new LinkedHashMap<>();

        assertTrue(ScriptResolver.resolveAskUser(askUserCfg(), args, extra));
        assertEquals("请告诉我您想购买的金额。", extra.get(ScriptConstants.KEY_RESPONSE_TEMPLATE));
    }

    @Test
    void resolveAskUser_outOfScope_writesScript() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put(ScriptConstants.PARAM_RESPONSE_TEMPLATE_STATUS, ScriptConstants.STATUS_OUT_OF_SCOPE);
        args.put(ScriptConstants.PARAM_RESPONSE_TEMPLATE_KEYS, Map.of(ScriptConstants.STATUS_OUT_OF_SCOPE, ScriptConstants.SCRIPT_OUT_OF_SCOPE));
        Map<String, Object> extra = new LinkedHashMap<>();

        assertTrue(ScriptResolver.resolveAskUser(askUserCfg(), args, extra));
        assertEquals("当前请求暂不在可处理范围内。", extra.get(ScriptConstants.KEY_RESPONSE_TEMPLATE));
    }

    @Test
    void resolveAskUser_noTemplateParams_returnsFalse() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("question", "请提供金额"); // 无 response_template_*
        Map<String, Object> extra = new LinkedHashMap<>();

        assertFalse(ScriptResolver.resolveAskUser(askUserCfg(), args, extra));
        assertNull(extra.get(ScriptConstants.KEY_RESPONSE_TEMPLATE), "无话术参数不应写入");
    }

    @Test
    void resolveAskUser_configMissingKey_returnsFalse() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put(ScriptConstants.PARAM_RESPONSE_TEMPLATE_STATUS, ScriptConstants.STATUS_CONFIRM);
        args.put(ScriptConstants.PARAM_RESPONSE_TEMPLATE_KEYS, Map.of(ScriptConstants.STATUS_CONFIRM, "not_in_config_xyz"));
        Map<String, Object> extra = new LinkedHashMap<>();

        assertFalse(ScriptResolver.resolveAskUser(askUserCfg(), args, extra));
        assertNull(extra.get(ScriptConstants.KEY_RESPONSE_TEMPLATE), "配置缺位不写入（A 面回落兜底）");
    }

    @Test
    void resolveAskUser_malformedKeysChineseQuotes_coerced() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put(ScriptConstants.PARAM_RESPONSE_TEMPLATE_STATUS, ScriptConstants.STATUS_CONFIRM);
        // LLM 非法 JSON（中文引号）→ coerceJsonMap 归一后解析
        args.put(ScriptConstants.PARAM_RESPONSE_TEMPLATE_KEYS, "\u201cconfirm\u201d\uff1a\u201cproduct_select_confirm\u201d");
        args.put(ScriptConstants.PARAM_RESPONSE_TEMPLATE_VARS, Map.of("productName", "一号产品", "amount", "500000"));
        Map<String, Object> extra = new LinkedHashMap<>();

        assertTrue(ScriptResolver.resolveAskUser(askUserCfg(), args, extra));
        assertEquals("确认购买以下产品：一号产品，金额 500000 元。", extra.get(ScriptConstants.KEY_RESPONSE_TEMPLATE));
    }

    @Test
    void resolveAskUser_jsonStringArgs_parsed() {
        // toolArgs 可能是 JSON 字符串（normalizeArgs 内部解析）
        Map<String, Object> extra = new LinkedHashMap<>();
        String jsonArgs = "{\"response_template_status\":\"confirm\","
                + "\"response_template_keys\":{\"confirm\":\"product_select_confirm\"},"
                + "\"response_template_vars\":{\"productName\":\"一号产品\",\"amount\":\"500000\"}}";

        assertTrue(ScriptResolver.resolveAskUser(askUserCfg(), jsonArgs, extra));
        assertEquals("确认购买以下产品：一号产品，金额 500000 元。", extra.get(ScriptConstants.KEY_RESPONSE_TEMPLATE));
    }

    @Test
    void resolveAskUser_nullScriptsOrExtra_returnsFalse() {
        assertFalse(ScriptResolver.resolveAskUser(null, Map.of(), new LinkedHashMap<>()));
        assertFalse(ScriptResolver.resolveAskUser(askUserCfg(), Map.of(), null));
    }

    /** 用 mergeSkillScripts 直接平铺构造 SysScriptsConfig（与生产 YAML flatten 后形态一致）。 */
    private SysScriptsConfig loadCfg(Map<String, String> flatTemplates) {
        SysScriptsConfig cfg = new SysScriptsConfig();
        Map<String, String> copy = new LinkedHashMap<>(flatTemplates);
        cfg.mergeSkillScripts(copy);
        return cfg;
    }
}
