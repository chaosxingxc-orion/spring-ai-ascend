package com.huawei.ascend.edp.rail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.edp.config.EdpaEventType;
import com.huawei.ascend.edp.config.ScriptConstants;
import com.huawei.ascend.edp.config.ScriptResolver;
import com.huawei.ascend.edp.config.SysScriptsConfig;
import com.huawei.ascend.edp.config.ToolConstants;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 话术出口 Rail（B 面，priority=50）。
 *
 * <p>负责 {@link EdpaEventRail} 不发的话术：首轮开场、业务话术解析、出口发射、合规把关、Prompt 注入。
 * 最终出口，仅高于 LogRail(10)。话术规则集中在共享 {@link ScriptResolver}，本 Rail 保持薄。</p>
 *
 * <p>话术载体 = {@code ctx.getExtra()} 内的 {@code _edp_response_template} 键（与现有
 * {@code _skip_tool}/{@code _plan_first_block}/{@code _edp_checkpoint_release} 同通道，本工程无
 * session.state API）。在同一 HTTP 请求内写 + 读 + 发射，不跨轮持久化。</p>
 *
 * <p>六职责：</p>
 * <ol>
 *     <li>{@link #beforeInvoke}：首轮开场（仅 {@code request_start}，无条件）。</li>
 *     <li>{@link #beforeToolCall}：ask_user 解析 {@code response_template_*} / cancel_task 写 reason → {@code _edp_response_template}。</li>
 *     <li>{@link #afterToolCall}：call_versatile/call_mcp 结果话术兜底。</li>
 *     <li>{@link #afterInvoke}：出口发射 {@code _edp_response_template}（对齐 Python 流末出口）。</li>
 *     <li>{@link #complianceGate}：配置外话术替换为 {@code out_of_scope}。</li>
 *     <li>{@link #init}：注入 cancel/business Prompt section（调 {@link ScriptResolver}）。</li>
 * </ol>
 */
public class ScriptsRail extends DeepAgentRail {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptsRail.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Prompt section 名 + 优先级（对齐 EdpaTodoRail 的 addPromptBuilderSection 模式）。 */
    private static final String CANCEL_RULES_SECTION = "edpa_scripts_cancel_rules";
    private static final String BUSINESS_RULES_SECTION = "edpa_scripts_business_rules";
    private static final int CANCEL_RULES_PRIORITY = 30;
    private static final int BUSINESS_RULES_PRIORITY = 40;

    /** 话术配置，可为 null（退化为不填话术，等价现状）。 */
    private final SysScriptsConfig scripts;

    public ScriptsRail(SysScriptsConfig scripts) {
        this.scripts = scripts;
    }

    @Override
    public int priority() {
        return 50;
    }

    // ═══════════════════════════════════════════════════
    // ① 首轮开场（对齐 Python agent.py:505-512）
    // ═══════════════════════════════════════════════════

    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        if (scripts == null || !isFirstTurn(ctx)) {
            return;
        }
        // 仅发 request_start（首轮开场，无条件，UC-C05 A2：非法/空请求仍送 request_start）。
        // planning_start 解耦到 beforeToolCall：仅在 Agent 真正进入规划（todo_create / PLAN_FIRST
        // 拦截）时发射，避免「你好」等无规划请求误发 planning_start（UC-C05：planning_start
        // 语义=进入规划阶段，无配对事件）。
        emitScript(ctx, EdpaEventType.REQUEST_START.wireName(),
                ScriptResolver.resolve(scripts, EdpaEventType.REQUEST_START.wireName(), Map.of()));
    }

    // ═══════════════════════════════════════════════════
    // ② 业务话术解析（cancel_task only；ask_user 由 EdpaEventRail.onToolException 解析）
    // ═══════════════════════════════════════════════════

    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (scripts == null || !(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        String tool = inputs.getToolName();
        Map<String, Object> args = normalizeArgs(inputs.getToolArgs());

        // ask_user 话术解析已迁移至 EdpaEventRail.onToolException
        // （AskUserTemplateRail p=85 抛 ToolInterruptException 后此 Rail p=50 不可达）
        if (ToolConstants.CANCEL_TASK.equals(tool) && readResponseTemplate(ctx) == null) {
            resolveCancelScript(ctx, args);
        }
    }

    /**
     * cancel_task：写 {@code _edp_response_template}（forceFinish 由 CancelRail 中段执行，payload 非话术）。
     */
    private void resolveCancelScript(AgentCallbackContext ctx, Map<String, Object> args) {
        String reason = str(args.get("reason"));
        if (isBlank(reason)) {
            reason = ScriptConstants.SCRIPT_TASK_CANCELLED;
        }
        String text = scripts.getOrDefault(reason, "");
        if (isBlank(text)) {
            return;
        }
        ctx.getExtra().put(ScriptConstants.KEY_RESPONSE_TEMPLATE, text);
        ctx.getExtra().put(ScriptConstants.KEY_LAST_SCRIPT, reason);
        ctx.getExtra().put(ScriptConstants.KEY_CANCEL_REASON, reason);
        LOGGER.info("[EDPA-SCRIPT] cancel_task resolved reason={} -> response_template", reason);
    }

    // ═══════════════════════════════════════════════════
    // ③ 业务结果话术兜底（call_versatile / call_mcp 结果）
    // ═══════════════════════════════════════════════════
    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        if (scripts == null || !(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        if (readResponseTemplate(ctx) != null) {
            return; // 已写不兜底
        }
        String tool = inputs.getToolName();
        if (!ToolConstants.CALL_VERSATILE.equals(tool) && !ToolConstants.CALL_MCP.equals(tool)) {
            return;
        }
        String key = pickResultScriptKey(tool, inputs.getToolResult());
        if (isBlank(key) || !scripts.has(key)) {
            return; // 配置缺位不补，不触碰 VersatileRail/McpRail 内部逻辑
        }
        String text = scripts.getOrDefault(key, "");
        if (!isBlank(text)) {
            ctx.getExtra().put(ScriptConstants.KEY_RESPONSE_TEMPLATE, text);
            ctx.getExtra().put(ScriptConstants.KEY_LAST_SCRIPT, key);
            LOGGER.info("[EDPA-SCRIPT] {} result fallback key={} -> response_template", tool, key);
        }
    }

    /**
     * 按 tool + result content 业务字段映射结果话术 key（配置缺位返回 null，不兜底）。
     *
     * <p>call_versatile 是通用工具，推荐/查余额/转账/购买都走它，仅靠 status 无法区分业务。
     * 按 content JSON 中的业务字段（productList / productBuyResponse / balance / node_name）细分。</p>
     */
    @SuppressWarnings("unchecked")
    private String pickResultScriptKey(String tool, Object toolResult) {
        Map<String, Object> result = null;
        String status = null;
        String content = null;
        if (toolResult instanceof Map<?, ?> map) {
            result = (Map<String, Object>) map;
            status = map.get("status") == null ? null : String.valueOf(map.get("status"));
            content = map.get("content") == null ? null : String.valueOf(map.get("content"));
        } else if (toolResult instanceof String s) {
            try {
                Object parsed = OBJECT_MAPPER.readValue(s, Object.class);
                if (parsed instanceof Map<?, ?> m) {
                    result = (Map<String, Object>) m;
                    status = m.get("status") == null ? null : String.valueOf(m.get("status"));
                    content = m.get("content") == null ? null : String.valueOf(m.get("content"));
                }
            } catch (Exception ignore) {
                // 非 JSON，按空处理
            }
        }
        if (ToolConstants.CALL_VERSATILE.equals(tool)) {
            // 缺参走 ask_user 话术，不在此兜底
            if (ScriptConstants.STATUS_MISSING_AMOUNT.equalsIgnoreCase(status)
                    || ScriptConstants.STATUS_MISSING_PRODUCT.equalsIgnoreCase(status)) {
                return null;
            }
            // 解析 content JSON 中的业务字段
            Map<String, Object> contentJson = parseContentJson(content);
            if (contentJson != null) {
                // 购买：productBuyResponse
                Object buyResp = contentJson.get("productBuyResponse");
                if (buyResp instanceof Map<?, ?> br) {
                    String buyStatus = br.get("buyStatus") == null ? null : String.valueOf(br.get("buyStatus"));
                    if ("1".equals(buyStatus)) {
                        return ScriptConstants.SCRIPT_FUND_PLANNING_SUCCESS;
                    }
                    return ScriptConstants.SCRIPT_FUND_PLANNING_FAILED;
                }
                // 推荐理财：productList
                if (contentJson.containsKey("productList")) {
                    return ScriptConstants.SCRIPT_PRODUCT_RECOMMEND_SUCCESS;
                }
                // 查余额 / 转账：无对应话术，不兜底
                if (contentJson.containsKey("balance") || contentJson.containsKey("node_name")) {
                    return null;
                }
            }
            // status 非 success/completed 但非空 → failed
            if (status != null
                    && !ScriptConstants.RESULT_SUCCESS.equalsIgnoreCase(status)
                    && !ScriptConstants.RESULT_COMPLETED.equalsIgnoreCase(status)) {
                return ScriptConstants.SCRIPT_FUND_PLANNING_FAILED;
            }
            // 未知业务，不兜底
            return null;
        }
        if (ToolConstants.CALL_MCP.equals(tool)) {
            return ScriptConstants.SCRIPT_MCP_RESULT_EMPTY;
        }
        return null;
    }

    /** 尝试解析 content 字段为 JSON Map，失败返回 null。 */
    private Map<String, Object> parseContentJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            Object parsed = OBJECT_MAPPER.readValue(content, Object.class);
            if (parsed instanceof Map<?, ?> m) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) m;
                return result;
            }
        } catch (Exception ignore) {
            // 非 JSON
        }
        return null;
    }

    // ═══════════════════════════════════════════════════
    // ④ 出口发射 + ⑤ 合规把关（对齐 Python agent.py:551-556，修复 D2）
    // ═══════════════════════════════════════════════════

    @Override
    public void afterInvoke(AgentCallbackContext ctx) {
        // 出口 request_start 已由 EdpaEventRail.afterInvoke（priority=80）在 conversation_end 之前发射。
        // 本 Rail（priority=50）不再重复发射，仅执行合规把关（若 _edp_response_template 仍存在）。
        String text = readResponseTemplate(ctx);
        if (isBlank(text)) {
            return;
        }
        complianceGate(ctx);
        ctx.getExtra().remove(ScriptConstants.KEY_RESPONSE_TEMPLATE); // 清本请求残留
        LOGGER.info("[EDPA-SCRIPT] afterInvoke compliance gate done, response_template cleared");
    }

    /**
     * 合规把关：配置外话术（{@code _edp_last_script_key} 不在配置内）→ 替换为 {@code out_of_scope}。
     */
    private void complianceGate(AgentCallbackContext ctx) {
        Object k = ctx.getExtra().get(ScriptConstants.KEY_LAST_SCRIPT);
        String key = k == null ? null : String.valueOf(k);
        if (scripts != null && key != null && !scripts.has(key)) {
            ctx.getExtra().put(ScriptConstants.KEY_RESPONSE_TEMPLATE,
                    scripts.getOrDefault(ScriptConstants.SCRIPT_OUT_OF_SCOPE, ""));
            LOGGER.info("[EDPA-SCRIPT] compliance gate replaced out-of-config key={} -> out_of_scope", key);
        }
    }

    // ═══════════════════════════════════════════════════
    // ⑥ Prompt 注入（init 回调，调 ScriptResolver 静态方法）
    // ═══════════════════════════════════════════════════

    @Override
    public void init(Object agent) {
        if (scripts == null || !(agent instanceof ReActAgent reActAgent)) {
            return;
        }
        reActAgent.addPromptBuilderSection(CANCEL_RULES_SECTION,
                ScriptResolver.cancelRulesPrompt(scripts), CANCEL_RULES_PRIORITY);
        reActAgent.addPromptBuilderSection(BUSINESS_RULES_SECTION,
                ScriptResolver.businessRulesPrompt(scripts), BUSINESS_RULES_PRIORITY);
        LOGGER.info("[EDPA-SCRIPT] injected prompt sections: {}, {}", CANCEL_RULES_SECTION, BUSINESS_RULES_SECTION);
    }

    @Override
    public void uninit(Object agent) {
        if (!(agent instanceof ReActAgent reActAgent)) {
            return;
        }
        try {
            reActAgent.getPromptBuilder().removeSection(CANCEL_RULES_SECTION);
        } catch (Exception e) {
            LOGGER.debug("[EDPA-SCRIPT] uninit removeSection '{}' ignored: {}", CANCEL_RULES_SECTION, e.getMessage());
        }
        try {
            reActAgent.getPromptBuilder().removeSection(BUSINESS_RULES_SECTION);
        } catch (Exception e) {
            LOGGER.debug("[EDPA-SCRIPT] uninit removeSection '{}' ignored: {}", BUSINESS_RULES_SECTION, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════
    // 私有辅助
    // ═══════════════════════════════════════════════════

    /**
     * 首轮判定（待联调）：当前实现为「无 interrupt_end 恢复信号即视为首轮」。
     *
     * <p>Python 用 checkpoint/cascade；EventFlow 首轮信号来源待确认。本处保守判定：当
     * {@code ctx.getExtra()} 无 resume 标记时视为首轮。生产联调后可改为基于 session 历史。</p>
     */
    private boolean isFirstTurn(AgentCallbackContext ctx) {
        try {
            Object resume = ctx.getExtra().get(com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState.RESUME_USER_INPUT_KEY);
            return resume == null;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 发射话术事件（北向 SSE，复用 EdpaEventRail.emit 同款格式）。
     */
    private void emitScript(AgentCallbackContext ctx, String eventType, String text) {
        if (isBlank(text)) {
            return;
        }
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event", eventType);
            event.put("timestamp", System.currentTimeMillis());
            event.put("conversation_id", sessionId(ctx));
            event.put("content", text);
            ctx.getSession().writeStream(new OutputSchema("custom", 0, event));
        } catch (Exception e) {
            LOGGER.warn("[EDPA-SCRIPT] emitScript failed: {}", e.getMessage());
        }
    }

    private String readResponseTemplate(AgentCallbackContext ctx) {
        Object v = ctx.getExtra().get(ScriptConstants.KEY_RESPONSE_TEMPLATE);
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeArgs(Object rawArgs) {
        if (rawArgs instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        if (rawArgs instanceof String s && !s.isBlank()) {
            try {
                Map<String, Object> parsed = OBJECT_MAPPER.readValue(s, new TypeReference<LinkedHashMap<String, Object>>() {
                });
                return parsed != null ? parsed : new LinkedHashMap<>();
            } catch (Exception e) {
                LOGGER.debug("[EDPA-SCRIPT] failed to parse toolArgs as JSON: {}", e.getMessage());
            }
        }
        return new LinkedHashMap<>();
    }

    private static String sessionId(AgentCallbackContext ctx) {
        try {
            return ctx.getSession().getSessionId();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
