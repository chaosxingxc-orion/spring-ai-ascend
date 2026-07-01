package com.openjiuwen.runtime.beta.selfheal;

import com.openjiuwen.core.alpha.verifier.NodeResult;
import com.openjiuwen.core.alpha.verifier.RootCause;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 嫁接 1.0 ReActAgent 的根因诊断+自愈护栏——工具失败（设备故障）时根因诊断 → 降级终态。
 *
 * <p>承重形态（spike 证伪确认）：onToolException 的 requestForceFinish <b>不拦</b> tool_call 循环
 * （offset 878 / exception 路径不消费，同轮4 afterToolCall 发现）。故本 rail 用<b>双钩子协作</b>：
 * <ul>
 *   <li>{@code onToolException}：工具失败信号 → {@link RootCauseDiagnoser} 诊断 {@link RootCause.DeviceFailure}
 *       → {@link RootCauseDispatcher} dispatch 得 {@link SelfHealAction.Degrade} → 标记 pendingAction（不终止）。</li>
 *   <li>{@code afterModelCall}（下一轮，offset 700）：检查 pendingAction Degrade → requestForceFinish(degraded)
 *       拦循环（offset 700 早于 executeToolCall offset 871）。</li>
 * </ul>
 *
 * <p>承重 IFF：工具失败 → DeviceFailure → Degrade → afterModelCall forceFinish。剥 onToolException 诊断
 * （不标记 pendingAction）→ afterModelCall 无 Degrade → 跑 maxIterations → RED（mutation-RED）。
 *
 * <p>诚实边界：轮5 仅承重 DeviceFailure（工具失败）→ Degrade 通道。PerceptionUnreliable（verify 抛异常）/
 * PlanOrAnswerError（verify 失败）需 criteria verify 信号集成（轮3 CriteriaVerificationRail），本轮 defer。
 * Replan（PlanOrAnswerError 自愈）复用轮4 ReplanRail，本 rail 不直接处理 Replan。
 */
public class RootCauseRail extends AgentRail {

    public static final String ROOT_CAUSE_DEGRADED_KEY = "root_cause_degraded";
    public static final String DEGRADED_KEY = "degraded";
    public static final String ROOT_CAUSE_KEY = "root_cause";
    public static final String REASON_KEY = "reason";

    private SelfHealAction pendingAction = null;

    /** 测试观测：是否有待降级的 pendingAction（onToolException 诊断后、afterModelCall 终止前）。 */
    public synchronized boolean hasPendingDegrade() {
        return pendingAction instanceof SelfHealAction.Degrade;
    }

    @Override
    public synchronized void onToolException(AgentCallbackContext context) {
        // 工具失败（设备故障）信号 → 诊断 DeviceFailure → dispatch Degrade（标记，afterModelCall 终止）
        // hit = nodes ∩ nodes = nodes 非空 → DeviceFailure
        String tool = extractToolName(context);
        Set<String> nodes = (tool != null) ? Set.of(tool) : Set.of("__unknown_tool__");
        RootCause cause = RootCauseDiagnoser.diagnose(false, nodes, nodes);
        pendingAction = RootCauseDispatcher.dispatch(cause);
    }

    /**
     * PEV bridge hook：接收 PregelExecutor 检测到的设备故障事件。
     *
     * <p>与 {@link #onToolException} 的区别：本方法接收 PEV 引擎的
     * {@link NodeResult.DeviceFailure}（带 nodeId/error/isTimeout），
     * 而非 1.0 ReActAgent 的 {@link AgentCallbackContext}。两者共享同一
     * pendingAction → afterModelCall forceFinish 路径。
     *
     * <p>调用方：{@code PEVToRailBridge.onExecutorDeviceFailure()}。
     */
    public synchronized void onDeviceFailure(NodeResult.DeviceFailure failure) {
        Set<String> nodes = (failure.nodeId() != null) ? Set.of(failure.nodeId()) : Set.of("__pev__");
        RootCause cause = RootCauseDiagnoser.diagnose(false, nodes, nodes);
        pendingAction = RootCauseDispatcher.dispatch(cause);
    }

    @Override
    public synchronized void afterModelCall(AgentCallbackContext context) {
        // onToolException 标记的 Degrade 在 afterModelCall 终止（offset 700 forceFinish 拦循环）
        if (pendingAction instanceof SelfHealAction.Degrade degrade) {
            context.requestForceFinish(degradedMap(degrade));
            pendingAction = null;
        }
    }

    private static String extractToolName(AgentCallbackContext context) {
        if (context.getInputs() instanceof ToolCallInputs inputs) {
            return inputs.getToolName();
        }
        return null;
    }

    private static Map<String, Object> degradedMap(SelfHealAction.Degrade degrade) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(ROOT_CAUSE_DEGRADED_KEY, true);
        result.put(DEGRADED_KEY, true);
        result.put(ROOT_CAUSE_KEY, "DeviceFailure");
        result.put(REASON_KEY, degrade.reason());
        return result;
    }
}
