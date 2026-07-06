package com.huawei.ascend.edp.rail;

import com.huawei.ascend.edp.config.EdpConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 轻量 Todo 校验 Rail。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>在 lite_todo_write 工具执行后识别 Todo 写入行为。</li>
 *     <li>读取 edp-config.yaml 中声明的 todolist_steps。</li>
 *     <li>为后续 Todo 状态机校验和步骤一致性校验预留扩展点。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>{@link #LiteTodoRail(EdpConfig)}：创建 LiteTodo Rail。</li>
 *     <li>{@link #afterToolCall(AgentCallbackContext)}：OpenJiuwen 工具调用后回调入口。</li>
 * </ul>
 */
public class LiteTodoRail extends AgentRail {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiteTodoRail.class);

    /**
     * EDP 专有配置，提供 todolist_steps 步骤定义。
     */
    private final EdpConfig edpConfig;

    /**
     * 构造 LiteTodo Rail。
     *
     * @param edpConfig EDP 专有配置
     */
    public LiteTodoRail(EdpConfig edpConfig) {
        this.edpConfig = edpConfig;
        // LiteTodo 校验应早于通用日志 Rail，但晚于取消和限制类 Rail。
        setPriority(35);
    }

    /**
     * 工具调用后回调。
     *
     * @param ctx OpenJiuwen 回调上下文，包含工具调用和工具结果信息
     */
    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        // 关键判断：只有工具调用上下文才需要处理 Todo 校验。
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        String toolName = inputs.getToolName();

        // 关键判断：只处理 lite_todo_write，其他工具调用不进入 Todo 校验。
        if ("lite_todo_write".equals(toolName)) {
            // 关键判断：只有配置中声明了 todolist_steps，才执行步骤一致性校验。
            if (edpConfig != null && edpConfig.getTodolistSteps() != null) {
                validateTodoAgainstSteps(ctx);
            }
        }
    }

    /**
     * 校验 Todo 与配置步骤的一致性。
     *
     * <p>当前 spike 阶段只记录配置步骤数量；后续可扩展为真实 step_id/status 校验。</p>
     *
     * @param ctx OpenJiuwen 回调上下文，预留用于读取工具入参和工具结果
     */
    private void validateTodoAgainstSteps(AgentCallbackContext ctx) {
        int declaredSteps = edpConfig.getTodolistSteps().size();
        LOGGER.info("LiteTodoRail: validating todo against {} declared steps", declaredSteps);
    }
}
