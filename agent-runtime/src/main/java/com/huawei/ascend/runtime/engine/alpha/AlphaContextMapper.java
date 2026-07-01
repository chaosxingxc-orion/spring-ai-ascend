package com.huawei.ascend.runtime.engine.alpha;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.openjiuwen.core.dispatch.AutonomyLevel;
import com.openjiuwen.core.kernel.model.*;
import com.openjiuwen.core.meta.AgentDefinition;
import com.openjiuwen.runtime.core.dispatch.TaskContext;
import com.openjiuwen.runtime.core.engine.AgentKernel;

import java.util.HashMap;
import java.util.Map;

/**
 * 将 framework-neutral {@link AgentExecutionContext} 映射为 openjiuwen
 * {@link TaskContext}——AlphaRuntimeHandler 的上下文适配层。
 *
 * <p>纯函数映射，零副作用。所有关键字段来源清楚：
 * <ul>
 *   <li>taskId ← scope.taskId（可选，为空则生成）</li>
 *   <li>userInput ← context.lastUserText()</li>
 *   <li>parameters ← context.getVariables()（按需提取结构化参数）</li>
 *   <li>metadata ← scope 身份信息（tenantId/userId/sessionId）</li>
 *   <li>agentName/agentDefinition/kernel/budget/autonomyLevel ← 外部注入</li>
 * </ul>
 */
public final class AlphaContextMapper {

    private AlphaContextMapper() {}

    /**
     * 从 SPI 执行上下文构造 openjiuwen TaskContext。
     *
     * @param context    SPI 层的执行上下文（用户输入、变量、身份）
     * @param agentId    当前 handler 的 agentId
     * @param kernel     AgentKernel 实例
     * @param agentDef   Agent 定义（工具、自主度、预算等）
     * @param extra      额外的 PEV 配置（Planner/Verifier/Executor 覆盖等），可为空
     * @return 可传入 PEVAlphaStrategy.execute() 的 TaskContext
     */
    public static TaskContext toTaskContext(
            AgentExecutionContext context,
            String agentId,
            AgentKernel kernel,
            AgentDefinition agentDef,
            Map<String, Object> extra) {

        TaskId taskId = resolveTaskId(context);
        AgentName agentName = new AgentName(agentId);

        // Build TaskInput: user text + structured parameters + session metadata
        String userInput = context.lastUserText();
        Map<String, Object> params = extractParameters(context);
        Map<String, String> metadata = buildMetadata(context);

        TaskInput input = TaskInput.of(userInput, params);
        // Merge metadata into input
        if (!metadata.isEmpty()) {
            input = new TaskInput(userInput, params, metadata);
        }

        Budget budget = agentDef != null && agentDef.budget() != null
                ? agentDef.budget()
                : Budget.Fixed.productionDefault();

        AutonomyLevel autonomy = agentDef != null && agentDef.autonomyLevel() != null
                ? agentDef.autonomyLevel()
                : AutonomyLevel.GUIDED;

        return new TaskContext(taskId, agentName, input, agentDef, kernel, budget, autonomy,
                extra != null ? Map.copyOf(extra) : Map.of());
    }

    // ==================== helper methods ====================

    private static TaskId resolveTaskId(AgentExecutionContext context) {
        String runtimeTaskId = context.getScope().taskId();
        if (runtimeTaskId != null && !runtimeTaskId.isBlank()) {
            return new TaskId(runtimeTaskId);
        }
        return TaskId.generate();
    }

    /**
     * 从 AgentExecutionContext 提取结构化参数。
     * variables 中以下 key 被识别为 ExecutionPolicy 覆盖：
     * - "successCriteria" → 放入 parameters（List<String> or String）
     * - "availableTools" → 放入 parameters
     * - "executionPolicy" → 放入 extraContext（非 parameters）
     *
     * <p>其余变量原样保留在 parameters 中。
     */
    private static Map<String, Object> extractParameters(AgentExecutionContext context) {
        Map<String, Object> variables = context.getVariables();
        Map<String, Object> params = new HashMap<>();
        if (variables == null || variables.isEmpty()) return params;

        for (var entry : variables.entrySet()) {
            String key = entry.getKey();
            // Exclude internal runtime keys
            if (key.startsWith("runtime.") || key.equals(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE)) {
                continue;
            }
            params.put(key, entry.getValue());
        }
        return params;
    }

    private static Map<String, String> buildMetadata(AgentExecutionContext context) {
        Map<String, String> meta = new HashMap<>();
        var scope = context.getScope();
        if (scope.tenantId() != null) meta.put("tenantId", scope.tenantId());
        if (scope.userId() != null) meta.put("userId", scope.userId());
        if (scope.sessionId() != null) meta.put("sessionId", scope.sessionId());
        meta.put("inputType", context.getInputType());
        return meta;
    }
}
