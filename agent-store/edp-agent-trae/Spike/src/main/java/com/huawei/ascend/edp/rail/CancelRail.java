package com.huawei.ascend.edp.rail;

import com.huawei.ascend.edp.config.EdpConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 任务取消 Rail。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>在工具调用完成后识别 cancel_task 工具。</li>
 *     <li>把取消工具调用转换为强制结束信号。</li>
 *     <li>返回固定取消话术，避免继续执行后续模型或工具步骤。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>{@link #CancelRail(EdpConfig)}：创建取消 Rail。</li>
 *     <li>{@link #afterToolCall(AgentCallbackContext)}：OpenJiuwen 工具调用后回调入口。</li>
 * </ul>
 *
 * <p>对齐 Python EDPAgent cancel_rail.py 的 after_tool_call 时机：先让 cancel_task
 * 工具函数执行并产生 tool response，再 requestForceFinish，确保消息序列合法
 * （tool_call 有对应 tool_response），避免 forceFinish 后的 LLM 调用因消息序列
 * 不合法而 HTTP 400。</p>
 */
public class CancelRail extends AgentRail {

    private static final Logger LOGGER = LoggerFactory.getLogger(CancelRail.class);

    /**
     * EDP 专有配置，当前预留给后续取消话术模板和取消策略使用。
     */
    private final EdpConfig edpConfig;

    /**
     * 构造取消 Rail。
     *
     * @param edpConfig EDP 专有配置
     */
    public CancelRail(EdpConfig edpConfig) {
        this.edpConfig = edpConfig;
        // 取消优先级较高，尽早拦截 cancel_task，避免继续执行其它业务逻辑。
        setPriority(10);
    }

    /**
     * 工具调用后回调。
     *
     * <p>作用：在 cancel_task 工具函数执行完成后拦截，触发强制结束。
     * 此时工具已返回 tool response，消息序列合法。</p>
     *
     * @param ctx OpenJiuwen 回调上下文，包含工具调用信息
     */
    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        // 关键判断：只有工具调用上下文才需要处理取消逻辑。
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        String toolName = inputs.getToolName();

        // 关键判断：只拦截 cancel_task，其他工具直接放行。
        if ("cancel_task".equals(toolName)) {
            String cancelMessage = "好的，已为您取消当前操作。如需其他帮助，请随时告诉我。";
            LOGGER.info("CancelRail: intercepting cancel_task after execution, force finish with message='{}'",
                    cancelMessage);

            // 关键跳转：请求 ReAct 执行链路强制结束，输出取消话术。
            // 此时 cancel_task 工具函数已执行完毕，tool response 已写入消息序列，
            // forceFinish 后不会再触发 LLM 调用，避免消息序列不合法导致的 HTTP 400。
            ctx.requestForceFinish(Map.of("message", cancelMessage));

            // 标记 checkpoint 清理：下一轮请求开头执行会话重置，对齐 Python
            // EDPAgent 的 checkpoint_to_release 机制，避免取消后上下文残留。
            ctx.getExtra().put("_edp_checkpoint_release", Boolean.TRUE);
        }
    }
}
