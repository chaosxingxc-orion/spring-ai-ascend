package com.huawei.ascend.edp.enhancer;

import com.huawei.ascend.edp.config.EdpAgentConfig;
import com.huawei.ascend.edp.config.EdpConfig;
import com.huawei.ascend.edp.rail.CancelRail;
import com.huawei.ascend.edp.rail.LiteTodoRail;
import com.huawei.ascend.edp.rail.ExecutionLimitRail;
import com.huawei.ascend.edp.rail.VersatileInterruptRail;
import com.huawei.ascend.edp.rail.McpInterruptRail;
import com.huawei.ascend.edp.rail.AskUserTemplateRail;
import com.huawei.ascend.edp.rail.LogRail;
import com.huawei.ascend.edp.tools.EdpaBusinessTools;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * EDPAgent 业务增强器。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>从 tools 包获取 EDPAgent spike 阶段内置业务工具。</li>
 *     <li>集中定义并构造 EDPAgent spike 阶段业务 Rails。</li>
 *     <li>把业务工具和 Rails 注册到 DeepAgent，使 A2A 请求执行时具备业务能力。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>{@link #enhance(DeepAgent, EdpConfig)}：一次性注册业务工具和业务 Rails。</li>
 *     <li>{@link #buildBusinessTools(EdpConfig)}：构造内置业务工具列表，供注册和测试复用。</li>
 *     <li>{@link #buildBusinessRails(EdpConfig)}：构造业务 Rails 列表，供注册和运行时复用。</li>
 * </ul>
 */
public class EdpaAgentEnhancer {

    /**
     * 轻量 Todo 写入工具名，对齐 Python EDPAgent 的 lite_todo_write。
     */
    public static final String TOOL_LITE_TODO_WRITE = EdpaBusinessTools.TOOL_LITE_TODO_WRITE;

    /**
     * MCP 沙箱调用工具名。
     */
    public static final String TOOL_CALL_MCP = EdpaBusinessTools.TOOL_CALL_MCP;

    /**
     * Versatile Agent 委托调用工具名。
     */
    public static final String TOOL_CALL_VERSATILE = EdpaBusinessTools.TOOL_CALL_VERSATILE;

    /**
     * 用户追问工具名。该工具需要触发 OpenJiuwen interrupt，而不是普通工具返回。
     */
    public static final String TOOL_ENHANCED_ASK_USER = EdpaBusinessTools.TOOL_ENHANCED_ASK_USER;

    /**
     * 任务取消工具名。
     */
    public static final String TOOL_CANCEL_TASK = EdpaBusinessTools.TOOL_CANCEL_TASK;

    private static final Logger LOGGER = LoggerFactory.getLogger(EdpaAgentEnhancer.class);

    /**
     * 增强 DeepAgent。
     *
     * <p>作用：把 EDPAgent spike 阶段已经迁移的业务工具和业务 Rails 注册到 DeepAgent。</p>
     *
     * @param agent 待增强的 DeepAgent 实例，不能为空
     * @param edpConfig EDP 专有配置，提供工具 Schema 和 Rail 行为需要的业务参数
     */
    public static void enhance(DeepAgent agent, EdpConfig edpConfig) {
        enhance(agent, edpConfig, null);
    }

    public static void enhance(DeepAgent agent, EdpConfig edpConfig, EdpAgentConfig agentConfig) {
        // 关键判断：DeepAgent 是注册工具和 Rail 的目标对象，缺失时直接失败，避免静默启动。
        if (agent == null) {
            throw new IllegalArgumentException("DeepAgent instance must not be null");
        }
        LOGGER.info("EdpaAgentEnhancer.enhance() start, edpConfig scope={}, todolistSteps={}, limits.tasks={}",
                edpConfig.getScope() != null ? edpConfig.getScope().getAllowed() : "null",
                edpConfig.getTodolistSteps() != null ? edpConfig.getTodolistSteps().size() : 0,
                edpConfig.getLimits() != null && edpConfig.getLimits().getTasks() != null ? edpConfig.getLimits().getTasks().size() : "null");

        // 先注册工具，确保模型工具列表和后续 Rail 拦截逻辑具备目标工具。
        registerBusinessTools(agent, edpConfig);

        // 再注册 Rails，确保模型调用、工具调用、记忆、日志等回调进入执行链路。
        registerBusinessRails(agent, edpConfig, agentConfig);

        LOGGER.info("EdpaAgentEnhancer.enhance() completed, registered {} business tools and {} business rails",
                countRegisteredTools(), edpConfig != null ? countRegisteredRails(edpConfig) : 0);
    }

    /**
     * 构造 EDPAgent 内置业务工具列表。
     *
     * <p>作用：委托 tools 包构造 Python EDPAgent 中已识别的核心工具。</p>
     *
     * @param edpConfig EDP 专有配置，用于动态生成 lite_todo_write 的 step_id 枚举
     * @return 工具列表，包含 lite_todo_write、call_mcp、call_versatile、ask_user、cancel_task
     */
    public static List<Tool> buildBusinessTools(EdpConfig edpConfig) {
        return EdpaBusinessTools.build(edpConfig);
    }

    /**
     * 构造 EDPAgent 业务 Rails。
     *
     * <p>作用：为模型调用、工具调用和会话执行过程挂接 EDPAgent 业务回调。</p>
     *
     * @param edpConfig EDP 专有配置，供各 Rail 读取 scope、limits、话术等配置
     * @return Rail 列表，注册顺序即当前 spike 阶段的业务回调顺序
     */
    public static List<AgentRail> buildBusinessRails(EdpConfig edpConfig) {
        return buildBusinessRails(edpConfig, null);
    }

    public static List<AgentRail> buildBusinessRails(EdpConfig edpConfig, EdpAgentConfig agentConfig) {
        List<AgentRail> rails = new ArrayList<>();

        // 取消类 Rail 优先注册，使取消信号尽早生效。
        rails.add(new CancelRail(edpConfig));
        // Todo Rail 负责轻量 Todo 状态相关回调。
        rails.add(new LiteTodoRail(edpConfig));
        // 执行限制 Rail 负责阻断失控循环。
        // 迭代次数限制由 DeepAgent 原生 ReActAgentConfig.maxIterations 提供，无需自定义 Rail。
        rails.add(new ExecutionLimitRail(edpConfig));
        // MCP / VA / ask_user Rail 负责工具调用前后的业务中断和参数增强。
        rails.add(new McpInterruptRail(edpConfig));
        rails.add(new VersatileInterruptRail(edpConfig, agentConfig != null ? agentConfig.getVersatile() : null));
        rails.add(new AskUserTemplateRail(edpConfig));
        // Log Rail 负责观测日志。
        // 记忆功能由 DeepAgent 原生 harness.rails.MemoryRail 提供，通过 DeepAgentConfig 配置启用。
        rails.add(new LogRail(edpConfig));

        return rails;
    }

    /**
     * 注册业务工具到 DeepAgent。
     *
     * @param agent DeepAgent 实例
     * @param edpConfig EDP 专有配置
     */
    private static void registerBusinessTools(DeepAgent agent, EdpConfig edpConfig) {
        List<Tool> tools = buildBusinessTools(edpConfig);
        for (Tool tool : tools) {
            // registerHarnessTool 是当前 OpenJiuwen Harness 暴露的工具注册入口。
            agent.registerHarnessTool(tool);
            LOGGER.info("Registered business tool: {}", tool.getCard().getName());
        }
    }

    /**
     * 注册业务 Rails 到 DeepAgent 底层 BaseAgent。
     *
     * @param agent DeepAgent 实例
     * @param edpConfig EDP 专有配置
     */
    private static void registerBusinessRails(DeepAgent agent, EdpConfig edpConfig, EdpAgentConfig agentConfig) {
        List<AgentRail> rails = buildBusinessRails(edpConfig, agentConfig);
        for (AgentRail rail : rails) {
            // Rail 注册在底层 BaseAgent 上，ReAct 执行循环会按事件和优先级触发回调。
            agent.getAgent().registerRail(rail);
            LOGGER.info("Registered business rail: {} (priority={})",
                    rail.getClass().getSimpleName(), rail.getPriority());
        }
    }

    /**
     * 返回当前 spike 阶段固定注册的业务工具数量。
     *
     * @return 工具数量
     */
    private static int countRegisteredTools() {
        return 5;
    }

    /**
     * 返回当前 spike 阶段固定注册的业务 Rail 数量。
     *
     * @param edpConfig EDP 专有配置，当前仅保留接口一致性
     * @return Rail 数量
     */
    private static int countRegisteredRails(EdpConfig edpConfig) {
        return 7;
    }
}
