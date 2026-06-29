package com.openjiuwen.runtime.beta.verification;

import com.openjiuwen.core.beta.model.GoalSpec;
import com.openjiuwen.core.beta.model.LLMDecision;
import com.openjiuwen.core.kernel.model.Violation;

import java.util.List;

/**
 * 成功标准验证器——在 FinalAnswer 前逐条验证 successCriteria。
 *
 * 验证策略分为两类：
 * 1. 规则判断（Rule-based）：确定性检查，不依赖 LLM
 *    - 决策历史中是否提到了该标准的关键词
 *    - 该标准对应的数据/文件是否已生成
 *    - 工具调用链是否覆盖了该标准所需的操作
 *
 * 2. LLM 判断（LLM-as-Judge）：用 LLM 评估标准是否满足
 *    - 标准涉及"质量"、"完整性"等主观判断
 *    - 标准涉及推理链的正确性
 *    - 规则判断无法确定时降级到 LLM 判断
 *
 * 验证流程：
 * 1. 逐条检查每个 successCriteria
 * 2. 先尝试规则判断
 * 3. 规则判断无法确定 → 用 LLM 判断
 * 4. 收集所有未通过的标准 → 返回 Violation 列表
 * 5. 全部通过 → 返回空列表
 */
public interface CriteriaVerifier {

    /**
     * 验证所有 successCriteria 是否被满足。
     *
     * @param goal            当前目标（含 successCriteria）
     * @param decisionHistory 决策历史
     * @param finalDecision   LLM 的 Complete 决策
     * @return 未满足的标准的 Violation 列表。空列表 = 全部通过
     */
    List<Violation> verify(
        GoalSpec goal,
        List<LLMDecision> decisionHistory,
        LLMDecision finalDecision
    );

    /**
     * 单条标准的验证结果。
     *
     * @param criterion  标准文本
     * @param passed     是否通过
     * @param method     验证方法：RULE_BASED 或 LLM_JUDGE
     * @param evidence   验证证据（从决策历史中提取的支撑信息）
     * @param reason     验证原因
     */
    record CriterionVerification(
        String criterion,
        boolean passed,
        VerificationMethod method,
        String evidence,
        String reason
    ) {}

    /**
     * 验证方法。
     */
    enum VerificationMethod {
        /** 规则判断（确定性） */
        RULE_BASED,
        /** LLM 判断（概率性） */
        LLM_JUDGE
    }
}
