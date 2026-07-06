package com.huawei.ascend.edp.rail;

import com.huawei.ascend.edp.config.ActRuleConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具执行次数限制 Rail。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>在工具调用前统计每个工具的调用次数。</li>
 *     <li>读取 actrule.yaml 中 tool_limits 的单工具调用上限。</li>
 *     <li>当工具调用次数超过限制时请求强制结束，避免工具循环失控。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>{@link #ExecutionLimitRail(ActRuleConfig)}：创建工具执行限制 Rail。</li>
 *     <li>{@link #beforeToolCall(AgentCallbackContext)}：工具调用前回调入口。</li>
 * </ul>
 */
public class ExecutionLimitRail extends AgentRail {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionLimitRail.class);

    /**
     * 行为治理配置，提供 tool_limits 工具调用次数上限。
     */
    private final ActRuleConfig actrule;

    /**
     * 工具调用次数计数器，外层 key 为 sessionId，内层 key 为工具名。
     * 通过 sessionId 隔离不同 A2A 请求，避免跨会话计数污染。
     */
    private final Map<String, Map<String, Integer>> toolCallCounts = new ConcurrentHashMap<>();

    /**
     * 构造工具执行次数限制 Rail。
     *
     * @param actrule 行为治理配置
     */
    public ExecutionLimitRail(ActRuleConfig actrule) {
        this.actrule = actrule;
        // 与迭代限制保持同一优先级，在工具真正执行前完成次数判断。
        setPriority(70);
    }

    /**
     * 工具调用前回调。
     *
     * @param ctx OpenJiuwen 回调上下文，包含工具调用信息
     */
    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        // 关键判断：只有工具调用上下文才需要统计工具执行次数。
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        String toolName = inputs.getToolName();
        int limit = getToolLimit(toolName);

        // 关键跳转：以 sessionId 隔离，再以工具名为维度累加调用次数。
        String sessionId = ctx.getSession() != null ? ctx.getSession().getSessionId() : "_default";
        Map<String, Integer> sessionCounts = toolCallCounts.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
        int currentCount = sessionCounts.merge(toolName, 1, Integer::sum);

        // 关键判断：超过配置上限时请求强制结束。
        if (currentCount > limit) {
            LOGGER.info("ExecutionLimitRail: tool {} call count {} > limit {}, forcing finish",
                    toolName, currentCount, limit);
            ctx.requestForceFinish(Map.of("message", "工具调用次数已达上限"));
        }
    }

    /**
     * 获取指定工具的调用次数上限。
     *
     * @param toolName 工具名
     * @return 工具调用上限；未配置时返回默认值 100
     */
    private int getToolLimit(String toolName) {
        // 关键判断：优先读取 actrule.yaml 中 tool_limits.<toolName> 的配置值。
        if (actrule != null && actrule.getToolLimits() != null) {
            Integer limit = actrule.getToolLimits().get(toolName);
            if (limit != null) {
                return limit;
            }
        }
        return 100;
    }
}
