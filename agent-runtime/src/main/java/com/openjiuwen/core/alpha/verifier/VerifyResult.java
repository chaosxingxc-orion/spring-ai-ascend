package com.openjiuwen.core.alpha.verifier;

import java.util.List;
import java.util.Set;

/**
 * 验证结果——Verifier 的输出。
 *
 * 验证的三种可能结果：
 * - PASSED: 全部通过
 * - FAILED: 部分或全部失败，附带失败原因和需要重做的节点
 * - INCONCLUSIVE: 无法判定（如 LLM 返回模糊结果）
 *
 * @param passed         是否通过
 * @param overallFeedback 总体反馈
 * @param nodeResults    逐节点验证结果
 * @param criteriaResults successCriteria 逐条验证结果
 * @param failedNodes    需要重做的节点 ID 集合
 */
public record VerifyResult(
    boolean passed,
    String overallFeedback,
    List<NodeVerifyResult> nodeResults,
    List<CriteriaVerifyResult> criteriaResults,
    Set<String> failedNodes
) {

    public static VerifyResult passed(String feedback) {
        return new VerifyResult(true, feedback, List.of(), List.of(), Set.of());
    }

    public static VerifyResult failed(String feedback, Set<String> failedNodes) {
        return new VerifyResult(false, feedback, List.of(), List.of(), failedNodes);
    }

    public static VerifyResult failed(String feedback, List<NodeVerifyResult> nodeResults,
                                       Set<String> failedNodes) {
        return new VerifyResult(false, feedback, nodeResults, List.of(), failedNodes);
    }

    /**
     * 逐节点验证结果。
     */
    public record NodeVerifyResult(
        String nodeId,
        boolean passed,
        String feedback,
        VerifyMethod method
    ) {}

    /**
     * successCriteria 逐条验证结果。
     */
    public record CriteriaVerifyResult(
        String criteria,
        boolean passed,
        String evidence
    ) {}

    /**
     * 验证方法。
     */
    public enum VerifyMethod {
        LLM,        // LLM 验证
        RULE,       // 规则验证
        HYBRID      // 混合验证
    }
}
