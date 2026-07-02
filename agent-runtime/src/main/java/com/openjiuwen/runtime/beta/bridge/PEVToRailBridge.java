package com.openjiuwen.runtime.beta.bridge;

import com.openjiuwen.core.alpha.verifier.NodeResult;
import com.openjiuwen.core.alpha.verifier.ReplanAction;
import com.openjiuwen.core.alpha.verifier.RootCause;
import com.openjiuwen.core.beta.model.GoalSpec;
import com.openjiuwen.core.kernel.model.NodeId;
import com.openjiuwen.runtime.beta.selfheal.RootCauseDiagnoser;
import com.openjiuwen.runtime.beta.selfheal.RootCauseDispatcher;
import com.openjiuwen.runtime.beta.selfheal.RootCauseRail;
import com.openjiuwen.runtime.beta.selfheal.SelfHealAction;

import java.util.HashSet;
import java.util.Set;

/**
 * PEV 引擎 ↔ 1.0 ReActAgent Rails 适配桥。
 *
 * <p>三个桥接点：
 * <ol>
 *   <li><b>RootCauseRail bridge</b>：PregelExecutor 发现 DeviceFailure →
 *       通知 RootCauseRail（让已有 Rails 也能看到 PEV 引擎的故障事件）。</li>
 *   <li><b>CriteriaVerificationRail bridge</b>：PEV verify 通过后 →
 *       可额外过 criteria 校验（defense-in-depth，不替代 PEV 自己的 verify）。</li>
 *   <li><b>ReplanRail bridge</b>：PEV 自己的 retryCount 取代 ReplanRail 计数。
 *       ReplanRail 仅在 1.0 ReActAgent 路径活跃，PEV 路径不触发。</li>
 * </ol>
 *
 * <p>契约：bridge 自身不含决策逻辑——RootCause 诊断委托
 * {@link RootCauseDiagnoser#diagnose}（共享纯函数），dispatch 委托
 * {@link RootCauseDispatcher#dispatch}。bridge 只负责格式转换 + 通知分发。
 */
public final class PEVToRailBridge {

    private final RootCauseRail rootCauseRail;

    /** PEV 路径的 replan 计数（替代 ReplanRail 的 GoalSpec 计数）。 */
    private int pevReplanCount = 0;

    public PEVToRailBridge(RootCauseRail rootCauseRail) {
        this.rootCauseRail = rootCauseRail;
    }

    // ==================== bridge 1: RootCauseRail ====================

    /**
     * PEV 执行器发现节点设备故障 → 诊断 + 通知 RootCauseRail。
     *
     * <p>调用时机：PregelExecutor 的 doOnNext/doOnError 中，每次出现
     * {@link NodeResult.DeviceFailure} 时调用。bridge 将 PEV 的 NodeId 集
     * 转为 String 集，委托共享诊断/dispatch。
     *
     * @param failedNodeIds 执行器返回的失败节点 ID 集（工具超时/异常节点）
     * @param error         错误描述（人类可读）
     * @return 诊断得到的 RootCause（调用方可记录日志/事件）
     */
    public RootCause onExecutorDeviceFailure(Set<NodeId> failedNodeIds, String error) {
        Set<String> failedNodes = new HashSet<>();
        for (NodeId id : failedNodeIds) {
            failedNodes.add(id.value());
        }

        // 诊断：工具失败 → DeviceFailure（replan 无效）
        RootCause cause = RootCauseDiagnoser.diagnose(false, failedNodes, failedNodes);

        // 通知 RootCauseRail（如果已注入）
        if (rootCauseRail != null) {
            // Rail 回调：PEV 的设备故障等价于 1.0 的 onToolException
            rootCauseRail.onDeviceFailure(new NodeResult.DeviceFailure(
                    failedNodes.isEmpty() ? "__pev__" : failedNodes.iterator().next(),
                    error, false));
        }

        return cause;
    }

    /**
     * PEV verify 后诊断设备故障 → 获取 SelfHealAction。
     *
     * <p>用于 PEV 引擎想知道"1.0 Rails 会怎么处理这个故障"时查询。
     */
    public SelfHealAction diagnoseForRails(RootCause cause) {
        return RootCauseDispatcher.dispatch(cause);
    }

    // ==================== bridge 2: CriteriaVerificationRail ====================

    /**
     * Criteria 校验入口——PEV verify 通过后的防御纵深。
     *
     * <p>当前是 hook point：调用方可在 PEV verify 通过后可选地过 criteria 校验。
     * 不需要 CriteriaVerifier 引用——CriteriaVerificationRail 本身包含 verifier。
     * 此方法仅标记桥接可用性；实际 criteria 二次验证由调用方注入 CriteriaVerifier 实例完成。
     *
     * <p>诚实边界：criteria 验证不在本 bridge 中完成——它依赖 LLM judge 通道，
     * 而 PEV verify 已经是 LLM 通道。double-LLM 需调用方显式 opt-in。
     *
     * @return true 如果 bridge 持有可用的 criteria verifier（调用方据此决定是否二次验证）
     */
    public boolean hasCriteriaVerifier() {
        // DEFERRED: criteria verifier 注入（与 per-node ReAct gate 同期落地）
        return false;
    }

    // ==================== bridge 3: ReplanRail ====================

    /**
     * PEV 路径的 Replan 计数递增。
     * PEV 引擎自己的 retryCount 替代 ReplanRail 的 GoalSpec.withReplan() 计数。
     *
     * @return 递增后的 Replan 次数
     */
    public int incrementPevReplanCount() {
        return ++pevReplanCount;
    }

    /** 当前 PEV 路径已执行的 Replan 次数。 */
    public int pevReplanCount() {
        return pevReplanCount;
    }

    /**
     * 检查 PEV Replan 是否已超限。
     *
     * @param maxReplan PEV ExecutionPolicy.maxRetries
     */
    public boolean isPevReplanExceeded(int maxReplan) {
        return pevReplanCount >= maxReplan;
    }
}
