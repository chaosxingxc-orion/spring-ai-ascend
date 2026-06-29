package com.openjiuwen.runtime.beta.replan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.beta.model.GoalSpec;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 嫁接 1.0 ReActAgent 的 Replan 计数/超限护栏——在 {@code afterModelCall} 拦截 {@code __replan__}
 * tool_call，做计数 + 超限 escalate。
 *
 * <p>承重形态（spike 证伪确认）：1.0 ReActAgent 无 2.0 的 LLMDecision.Replan 语义，用 {@link ReplanTool}
 * 虚拟工具承载 Replan 意图。本 rail 在 afterModelCall 看 response.getToolCalls() 含 {@code __replan__} 时：
 * <ul>
 *   <li>未超限（{@code goal.canReplan()}）→ {@code withReplan} 计数（追加 ReplanRecord），放行
 *       （replan 工具执行返固定确认，LLM 下一轮换策略）。</li>
 *   <li>超限 → {@code requestForceFinish(degraded)} 诚实标记终态（offset 700 消费，拦在 replan 工具执行前）。</li>
 * </ul>
 *
 * <p>承重证伪（轮4 spike）：{@code afterToolCall} 的 forceFinish <b>不拦</b> tool_call 循环（offset 878
 * 在 interrupt 路径，正常循环不消费），故用 {@code afterModelCall}（offset 700，callModel 后、toolCalls
 * 终态判定前消费，能拦）——与轮3 CriteriaVerificationRail 同一 afterModelCall forceFinish 路径。
 *
 * <p>承重 IFF（plan V2 轮4）：Replan 计数 ⟺ 超限 escalate。剥 withReplan 计数 → canReplan 永真 →
 * 永不 forceFinish → RED 证非恒真。
 *
 * <p>诚实边界：超限 escalate 是 degraded 终态（诚实标记，不假装能修正——afterModelCall gate 结构上
 * 无法强制修正，同轮3）。replan 后"重新生成计划"（beta 的 planGenerator.generate）在 1.0 无 BetaPlan
 * 对应物，defer 轮5。多 session 并发同一 rail 实例的计数串扰用 synchronized 防御（per-agent rail 一般无此问题）。
 *
 * <p>resource_mgr execute 接线：{@link ReplanTool} 的 execute（invoke 被调）经 {@code Runner.resourceMgr()}，
 * 完整嫁接需 {@code Runner.resourceMgr().addTool} 注册（spike/测试/生产 handler 都用，同 OpenJiuwenRemoteToolInstaller
 * 范式）。本 rail 的承重（afterModelCall 计数/超限 forceFinish）在工具 execute 前发生（offset 700 早于
 * executeToolCall offset 871），承重不依赖 execute invoke——但未超限放行后的工具 execute（LLM 看到 ReplanTool
 * 返回的确认）需 resource_mgr 注册才工作。
 *
 * <p>plan V2 轮4 偏差：原 spec "移植 DecisionParser + LLMDecision sealed 8 态"缩范围——1.0 ReActAgent 用原生
 * tool_call/answer 语义（无 JSON 决策解析落点），DecisionParser defer；LLMDecision sealed 8 态轮3 已全移
 * （criteria/Replan 共用），Replan 经虚拟工具承载（非 DecisionParser JSON 解析）。
 */
public class ReplanRail extends AgentRail {

    public static final String OUTPUT_KEY = "output";
    public static final String REPLAN_EXCEEDED_KEY = "replan_exceeded";
    public static final String DEGRADED_KEY = "degraded";
    public static final String REPLAN_COUNT_KEY = "replan_count";
    public static final String MAX_REPLAN_KEY = "max_replan";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GoalSpec goal;

    public ReplanRail(GoalSpec goal) {
        this.goal = goal;
    }

    /** 当前已计数 Replan 次数（测试观测用）。 */
    public synchronized int replanCount() {
        return goal.replanCount();
    }

    @Override
    public synchronized void afterModelCall(AgentCallbackContext context) {
        ToolCall replanCall = findReplanToolCall(context);
        if (replanCall == null) {
            return;
        }
        if (!goal.canReplan()) {
            // 超限 escalate → forceFinish degraded（offset 700 消费，拦在 replan 工具执行前）
            context.requestForceFinish(degradedResult());
        } else {
            // 未超限 → 计数（withReplan 追加 ReplanRecord）
            goal = goal.withReplan(extractReplanRecord(replanCall));
            // 放行：replan 工具执行（ReplanTool.invoke 返固定确认），LLM 下一轮换策略
        }
    }

    private static ToolCall findReplanToolCall(AgentCallbackContext context) {
        if (!(context.getInputs() instanceof ModelCallInputs inputs)) {
            return null;
        }
        if (!(inputs.getResponse() instanceof AssistantMessage msg)) {
            return null;
        }
        if (msg.getToolCalls() == null) {
            return null;
        }
        return msg.getToolCalls().stream()
                .filter(tc -> ReplanTool.TOOL_NAME.equals(tc.getName()))
                .findFirst()
                .orElse(null);
    }

    private static GoalSpec.ReplanRecord extractReplanRecord(ToolCall replanCall) {
        Map<String, Object> args = parseArgs(replanCall.getArguments());
        String reason = String.valueOf(args.getOrDefault(ReplanTool.ARG_REPLAN_REASON, ""));
        String newApproach = String.valueOf(args.getOrDefault(ReplanTool.ARG_NEW_APPROACH, ""));
        // stepIndex 简化为 0——精确步骤追踪非轮4 承重，defer
        return new GoalSpec.ReplanRecord(0, reason, newApproach);
    }

    private Map<String, Object> degradedResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(OUTPUT_KEY, "Replan 次数已达上限 " + goal.maxReplanCount() + "，escalate 为降级终态");
        result.put(REPLAN_EXCEEDED_KEY, true);
        result.put(DEGRADED_KEY, true);
        result.put(REPLAN_COUNT_KEY, goal.replanCount());
        result.put(MAX_REPLAN_KEY, goal.maxReplanCount());
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
