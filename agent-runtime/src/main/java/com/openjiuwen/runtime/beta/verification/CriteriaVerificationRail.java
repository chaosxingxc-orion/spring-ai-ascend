package com.openjiuwen.runtime.beta.verification;

import com.openjiuwen.core.beta.model.GoalSpec;
import com.openjiuwen.core.beta.model.LLMDecision;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.kernel.model.ToolName;
import com.openjiuwen.core.kernel.model.Violation;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 嫁接 1.0 ReActAgent 的 criteria 验证护栏——在 {@code afterModelCall} 对最终答案做
 * successCriteria 验证，用 {@code requestForceFinish} 双向标记终态。
 *
 * <p>形态 a（承重确认）：spike 1 证 afterModelCall 的 {@code requestForceFinish(Map)} 被
 * ReActAgent.invoke 消费（callModel 返回后、toolCalls 终态判定前 consumeForceFinish 短路），
 * 直接返回 forced Map（跳过自然 answer 路径）。spike 2 证 {@code pushSteering} 拦不住纯答案终态
 * （drainSteering 注入下一轮 ModelContext 但 agent getToolCalls 空→终止，下一轮不发生）——
 * 故 steering 修正循环砍（afterModelCall gate 结构上无法强制修正，承重发现），改 forceFinish 双向 gate：
 * <ul>
 *   <li>criteria 通过 → {@code requestForceFinish(verified=true)}：锁定正确终态（防 agent 后续迭代退化）。</li>
 *   <li>criteria 不通过 → {@code requestForceFinish(verified=false, degraded=true, unmet=[...])}：
 *       诚实标记终态未达标（强制修正需外层壳，defer）。</li>
 * </ul>
 *
 * <p>数据适配：1.0 ReActAgent 的 trajectory（AssistantMessage）≠ 2.0 LLMDecision。本 rail 把
 * tool_call 轮积累为 {@link LLMDecision.CallTool}（reasoning 取 AssistantMessage content，args 解析
 * ToolCall.getArguments JSON），最终答案转 {@link LLMDecision.Complete}（output 取 content），
 * 供 {@link CriteriaVerifier} 做输出覆盖 + 历史覆盖规则判断。
 *
 * <p>诚实边界（承重分层）：
 * <ul>
 *   <li>tool_call → LLMDecision 适配是近似（reasoning 用 content 非结构化 thought），完整决策语义适配 defer 轮4。</li>
 *   <li>LLM judge 通道在 verifier 层已承重（{@code DecisionHistoryCriteriaVerifierTest} 注入 StubKernel
 *       IFF + budget 门双向 + mutation-RED）；本 rail 默认注入无 kernel 的
 *       {@link DecisionHistoryCriteriaVerifier}（→ ASSUME_FAIL fallback），故轮3 rail 嫁接承重
 *       output/history 规则路径，LLM judge 经 rail 的 e2e 接线 defer 轮4/轮9。若需 rail 层 judge，
 *       调用方注入已配 kernel+budget 的 verifier 并在 verify 前 setBudgetLimits。</li>
 * </ul>
 */
public class CriteriaVerificationRail extends AgentRail {

    /** forced result Map 键——承重断言 + 终态标记契约。 */
    public static final String OUTPUT_KEY = "output";
    public static final String VERIFIED_KEY = "criteria_verified";
    public static final String RESULT_KEY = "criteria_result";
    public static final String DEGRADED_KEY = "degraded";
    public static final String UNMET_KEY = "unmet_criteria";

    private final CriteriaVerifier verifier;
    private final GoalSpec goal;
    private final List<LLMDecision> decisionHistory = new ArrayList<>();

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    public CriteriaVerificationRail(CriteriaVerifier verifier, GoalSpec goal) {
        this.verifier = verifier;
        this.goal = goal;
    }

    @Override
    public void afterModelCall(AgentCallbackContext context) {
        if (!(context.getInputs() instanceof ModelCallInputs inputs)) {
            return;
        }
        if (!(inputs.getResponse() instanceof AssistantMessage msg)) {
            return;
        }

        if (isFinalAnswer(msg)) {
            LLMDecision.Complete complete = new LLMDecision.Complete(contentOf(msg), 1.0, "");
            List<Violation> violations = verifier.verify(goal, List.copyOf(decisionHistory), complete);
            if (violations.isEmpty()) {
                context.requestForceFinish(verifiedResult(msg));
            } else {
                context.requestForceFinish(degradedResult(msg, violations));
            }
        } else {
            accumulateToolCalls(msg);
        }
    }

    /** 暴露积累的决策历史（测试观测用）。 */
    List<LLMDecision> decisionHistory() {
        return List.copyOf(decisionHistory);
    }

    private static boolean isFinalAnswer(AssistantMessage msg) {
        return msg.getToolCalls() == null || msg.getToolCalls().isEmpty();
    }

    private void accumulateToolCalls(AssistantMessage msg) {
        String reasoning = contentOf(msg);
        for (var toolCall : msg.getToolCalls()) {
            decisionHistory.add(new LLMDecision.CallTool(
                    new ToolName(toolCall.getName()), parseArgs(toolCall.getArguments()), reasoning));
        }
    }

    private static String contentOf(AssistantMessage msg) {
        String content = msg.getContentAsString();
        return content != null ? content : "";
    }

    private static Map<String, Object> verifiedResult(AssistantMessage msg) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(OUTPUT_KEY, contentOf(msg));
        result.put(VERIFIED_KEY, true);
        result.put(RESULT_KEY, "PASS");
        return result;
    }

    private static Map<String, Object> degradedResult(AssistantMessage msg, List<Violation> violations) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(OUTPUT_KEY, contentOf(msg));
        result.put(VERIFIED_KEY, false);
        result.put(RESULT_KEY, "FAIL");
        result.put(DEGRADED_KEY, true);
        result.put(UNMET_KEY, violations.stream().map(Violation::message).toList());
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
