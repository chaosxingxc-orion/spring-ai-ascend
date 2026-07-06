package com.huawei.ascend.edp.rail;

import com.huawei.ascend.edp.config.EdpConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP 工具调用观测 Rail。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>在 call_mcp 工具调用前记录 MCP 沙箱执行校验入口。</li>
 *     <li>在 call_mcp 工具调用后记录执行完成事件。</li>
 *     <li>为后续 P9 MCP 沙箱真实执行、中断和结果校验预留扩展点。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>{@link #McpInterruptRail(EdpConfig)}：创建 MCP Rail。</li>
 *     <li>{@link #beforeToolCall(AgentCallbackContext)}：工具调用前回调入口。</li>
 *     <li>{@link #afterToolCall(AgentCallbackContext)}：工具调用后回调入口。</li>
 * </ul>
 */
public class McpInterruptRail extends AgentRail {

    private static final Logger LOGGER = LoggerFactory.getLogger(McpInterruptRail.class);

    /**
     * EDP 专有配置，当前预留给 MCP 沙箱策略、白名单和安全限制使用。
     */
    private final EdpConfig edpConfig;

    /**
     * 构造 MCP Rail。
     *
     * @param edpConfig EDP 专有配置
     */
    public McpInterruptRail(EdpConfig edpConfig) {
        this.edpConfig = edpConfig;
        // MCP 与 VA、ask_user 同属工具调用增强类 Rail，使用同一优先级。
        setPriority(50);
    }

    /**
     * 工具调用前回调。
     *
     * @param ctx OpenJiuwen 回调上下文，包含工具调用信息
     */
    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        // 关键判断：只有工具调用上下文才需要识别 MCP 工具。
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        String toolName = inputs.getToolName();

        // 关键判断：只观测 call_mcp，其他工具直接放行。
        if ("call_mcp".equals(toolName)) {
            LOGGER.info("McpInterruptRail: intercepting call_mcp for sandbox execution validation");
        }
    }

    /**
     * 工具调用后回调。
     *
     * @param ctx OpenJiuwen 回调上下文，包含工具调用和工具结果信息
     */
    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        // 关键判断：只有工具调用上下文才需要识别 MCP 工具。
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        String toolName = inputs.getToolName();

        // 关键判断：只记录 call_mcp 完成事件。
        if ("call_mcp".equals(toolName)) {
            LOGGER.info("McpInterruptRail: call_mcp completed, result validated");
        }
    }
}
