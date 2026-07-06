package com.huawei.ascend.edp.enhancer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Todo sessionId 来源解析器（替代 Core TaskPlanningRail 默认的 "default" 兜底）。
 *
 * <p>问题背景：Core {@code TaskPlanningRail} 的 sessionId 来源是
 * {@code toolArgs.session_id}（LLM 传的），LLM 不传则兜底 "default"，
 * 导致所有会话共用 {@code .todo/default/} 互相覆盖。</p>
 *
 * <p>本类更改 sessionId 来源：从 {@link AgentCallbackContext#getSession()} 获取真实 sessionId，
 * 转义为合法路径段后注入到 toolArgs，使 Core 落盘按真实会话隔离。</p>
 *
 * <p>使用方：</p>
 * <ul>
 *   <li>{@code EdpaTodoRail.beforeToolCall}：调用 {@link #injectFromContext} 注入 session_id</li>
 *   <li>{@code EdpaTodoRail.afterToolCall}：调用 {@link #resolveFromInputs} 解析 session_id</li>
 *   <li>{@code EdpaEventRail}：调用 {@link #sanitizeSessionId} 转义 sessionId</li>
 * </ul>
 */
public final class TodoSessionResolver {

    private TodoSessionResolver() {}

    /** sessionId 为空时的兜底值。 */
    private static final String DEFAULT_SESSION_ID = "default";

    /** toolArgs 中 session_id 字段名。 */
    private static final String FIELD_SESSION_ID = "session_id";

    /** 解析 LLM 原始 JSON 字符串形式 toolArgs（Core 经 ToolCallInputs 暴露给 rail 的是 String）。 */
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * 从 ctx 获取真实 sessionId，转义后注入到 args.session_id（仅当原值为空时）。
     *
     * @param ctx 回调上下文（提供真实 sessionId）
     * @param args 已解析的 toolArgs（可变 Map，原地修改）
     * @return true 表示 session_id 被注入（有变化），false 表示无需注入
     */
    public static boolean injectFromContext(AgentCallbackContext ctx, Map<String, Object> args) {
        String realSid = sanitizeSessionId(
                ctx.getSession() != null ? ctx.getSession().getSessionId() : null);
        Object prevSid = args.get(FIELD_SESSION_ID);
        if ((prevSid == null || String.valueOf(prevSid).isBlank()) && realSid != null) {
            args.put(FIELD_SESSION_ID, realSid);
            return true;
        }
        return false;
    }

    /**
     * 从 ToolCallInputs 解析 session_id（afterToolCall 用，与 Core 落盘键一致）。
     *
     * @param inputs 工具调用输入
     * @return 转义后的 session_id，未配置时返回 "default"
     */
    public static String resolveFromInputs(ToolCallInputs inputs) {
        Map<String, Object> args = normalizeArgs(inputs.getToolArgs());
        Object value = args.get(FIELD_SESSION_ID);
        if (value != null && !String.valueOf(value).isBlank()) {
            return String.valueOf(value);
        }
        return DEFAULT_SESSION_ID;
    }

    /**
     * 把真实 sessionId（A2A 适配层注入的 {@code state:default:edp_agent:<uuid>}，含冒号）
     * 转义为 Windows 合法路径段，供 TodoTool 落盘与读取共用同一键。
     *
     * <p>必须用真实 sessionId 而非 "default"，否则多会话共用 {@code .todo/default/} 互相覆盖。</p>
     *
     * @param sessionId 原始 sessionId（可能含冒号等非法路径字符）
     * @return 转义后的合法路径段
     */
    public static String sanitizeSessionId(String sessionId) {
        String safe = sessionId == null || sessionId.isBlank()
                ? DEFAULT_SESSION_ID : sessionId;
        return safe.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /**
     * 解析 LLM 原始 JSON 字符串或 Map 形式的 toolArgs 为可变 Map。
     *
     * <p>Core 通过 ToolCallInputs 暴露给 rail 的 toolArgs 是 LLM 原始 JSON 字符串（非 Map），
     * 必须解析才能取到 session_id / tasks 等字段。</p>
     *
     * @param rawArgs 原始参数（String 或 Map）
     * @return 可变 Map（空 Map 表示解析失败或空参数）
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalizeArgs(Object rawArgs) {
        if (rawArgs instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        if (rawArgs instanceof String s && !s.isBlank()) {
            try {
                Map<String, Object> parsed = JSON_MAPPER.readValue(s, Map.class);
                return parsed != null ? parsed : new LinkedHashMap<>();
            } catch (Exception ignored) {
                // 解析失败则按空参数处理
            }
        }
        return new LinkedHashMap<>();
    }
}
