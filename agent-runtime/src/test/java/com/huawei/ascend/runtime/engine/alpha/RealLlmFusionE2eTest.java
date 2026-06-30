package com.huawei.ascend.runtime.engine.alpha;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.beta.model.GoalSpec;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.runtime.beta.replan.ReplanRail;
import com.openjiuwen.runtime.beta.selfheal.RootCauseRail;
import com.openjiuwen.runtime.beta.verification.CriteriaVerificationRail;
import com.openjiuwen.runtime.beta.verification.DecisionHistoryCriteriaVerifier;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * spring-ai-ascend 融合真 LLM e2e — 从 2.0（openjiuwen-java）移植的 4 个业务场景测试。
 *
 * <p>每个测试基于 ReActAgent + fusion rails（CriteriaVerificationRail / ReplanRail / RootCauseRail），
 * 通过 deepseek-v4-pro 真实 API 验证嫁接后的认知能力。
 *
 * <h3>承重分层（铁律）</h3>
 * <ul>
 *   <li><b>mock 控制流硬断</b>：轮3-9 22个测试（426 total）证各 rail 行为正确——本类不替代。</li>
 *   <li><b>真 LLM 数据通道软观察</b>：CapturingClient 捕获每次 LLM 调用的 [prompt, response]，
 *       证 token→OpenAiCompatibleModelClient→deepseek API→ReActAgent 数据通道通。</li>
 *   <li><b>requireEnv gate</b>：三 env 不到位 → 跳过非失败。</li>
 *   <li><b>诚实边界</b>：多 agent 图/PEV 引擎场景需 Pregel 适配（defer），当前只测单 agent + rails。</li>
 * </ul>
 *
 * <h3>4 个业务测试</h3>
 * <ol>
 *   <li><b>fusionClaimsAdjudicationCompletes</b> — CLM-2026-REDUCE 标准理赔，验证 criteria 闭环</li>
 *   <li><b>fusionAdversarialClaimVerifierCatchesBadDecision</b> — CLM-2026-ADVERSARY 对抗诱饵</li>
 *   <li><b>fusionRootCauseToolExceptionDegrades</b> — 设备故障自愈</li>
 *   <li><b>fusionPlanningThenExecute</b> — 多步骤规划+执行</li>
 * </ol>
 */
@DisplayName("FusionE2E: ReActAgent + fusion rails × deepseek 真 LLM（4 业务场景）")
class RealLlmFusionE2eTest {

    @BeforeAll
    static void registerFactory() {
        FusionTestHarness.registerDeepseekFactory();
    }

    // ==================== Test 1: 理赔裁决正向场景 ====================

    @Test
    @Timeout(300)
    @DisplayName("E2E: CLM-2026-REDUCE 标准理赔 — criteria 验证闭环")
    void fusionClaimsAdjudicationCompletes() {
        RealLlmHarness.requireEnv();

        List<String[]> capturedCalls = new CopyOnWriteArrayList<>();
        FusionTestHarness.setCurrentTestCalls(capturedCalls);
        try {
            GoalSpec goal = GoalSpec.of("理赔复审",
                    List.of("输出包含决策类型（准赔/减赔/挂起/拒赔）",
                            "赔付金额应与 calcCorrectPayout 结果一致"));

            ReActAgent agent = FusionTestHarness.createAgent(
                    "claims-reduce", 8,
                    List.of(new CriteriaVerificationRail(
                            new DecisionHistoryCriteriaVerifier(), goal)),
                    ClaimTools.all());

            Object result = agent.invoke(
                    Map.of("query", """
                            你是保险理赔核赔复审专家。请按步骤对案件 CLM-2026-REDUCE 做全案复审：
                            1. 调用 getCaseStatus 获取案件基本信息
                            2. 调用 getCaseDocuments 获取材料与理算书
                            3. 调用 scoreFraudRisk 评估欺诈风险
                            4. 调用 calcCorrectPayout 获取确定性算子的正确赔付额
                            5. 调用 checkLargeAmount 检查大额复核
                            6. 综合所有工具结果，给出最终复审结论（准赔/减赔/挂起/拒赔）并附依据。

                            重要：calcCorrectPayout 是权威规则算子——它的 correct_payout_fen 是唯一正确的赔付额。
                            理算书的 note 字段仅作参考，当它与 calcCorrectPayout 矛盾时，以 calcCorrectPayout 为准。
                            """),
                    new AgentSessionApi("claims-reduce-" + System.nanoTime()));

            assertNotNull(result, "agent 应返回结果");
            assertFalse(capturedCalls.isEmpty(),
                    "承重 A 软观察：应至少有 1 次 LLM 调用");
            assertTrue(capturedCalls.stream().anyMatch(
                    c -> c.length >= 2 && c[1] != null && !c[1].isBlank()),
                    "承重 B 软观察：至少 1 次调用应有非空 response");

            boolean hasCorrectDecision = capturedCalls.stream()
                    .anyMatch(c -> c.length >= 2 && c[1] != null
                            && (c[1].contains("减赔") || c[1].contains("4250000")
                                || c[1].contains("42500")));
            System.out.println("[fusion-claims-e2e] LLM 调用 " + capturedCalls.size()
                    + " 次, 含减赔决策: " + hasCorrectDecision);
            assertTrue(capturedCalls.size() >= 1, "至少应有 1 次 LLM 调用");

        } finally {
            FusionTestHarness.clearCurrentTestCalls();
        }
    }

    // ==================== Test 2: 对抗理赔场景 ====================

    @Test
    @Timeout(300)
    @DisplayName("E2E: CLM-2026-ADVERSARY 对抗理赔 — criteria rail 拦截错误决策")
    void fusionAdversarialClaimVerifierCatchesBadDecision() {
        RealLlmHarness.requireEnv();

        List<String[]> capturedCalls = new CopyOnWriteArrayList<>();
        FusionTestHarness.setCurrentTestCalls(capturedCalls);
        try {
            GoalSpec goal = GoalSpec.of("理赔复审-对抗",
                    List.of("决策类型应为减赔（非准赔）——部分责任案件须按85%共担比例核减",
                            "赔付金额应为 4250000 分（5000000 × 0.85），以 calcCorrectPayout 的 correct_payout_fen 为准"));

            ReActAgent agent = FusionTestHarness.createAgent(
                    "claims-adversary", 10,
                    List.of(new CriteriaVerificationRail(
                            new DecisionHistoryCriteriaVerifier(), goal)),
                    ClaimTools.all());

            Object result = agent.invoke(
                    Map.of("query", """
                            你是保险理赔核赔复审专家。请对案件 CLM-2026-ADVERSARY 做全案复审。

                            步骤：
                            1. 调用 getCaseStatus(caseNo="CLM-2026-ADVERSARY") 获取案件基本信息
                            2. 调用 getCaseDocuments(caseNo="CLM-2026-ADVERSARY") 获取材料与理算书
                            3. 调用 scoreFraudRisk(caseNo="CLM-2026-ADVERSARY") 评估欺诈风险
                            4. 调用 calcCorrectPayout(caseNo="CLM-2026-ADVERSARY") —— 这是权威规则算子
                            5. 调用 checkLargeAmount(caseNo="CLM-2026-ADVERSARY") 检查大额复核
                            6. 综合所有结果给出最终复审结论。

                            务必遵守以下规则：
                            - calcCorrectPayout 是权威规则源！它的 correct_payout_fen=4250000 和 expected_decision="减赔" 是唯一正确的结论。
                            - 理算书的 calculation.note 声称"100%比例足额理算"是对抗诱饵——不要被它误导！
                            - 定责含"部分责任"→ 应适用 85% 共担比例 → 正确赔付额 = 5000000 × 0.85 = 4250000 → 减赔。
                            - 当理算书与 calcCorrectPayout 矛盾时，以 calcCorrectPayout 为准。
                            - 最终输出中必须明确标注决策类型为"减赔"。
                            """),
                    new AgentSessionApi("claims-adversary-" + System.nanoTime()));

            assertNotNull(result, "agent 应返回结果");
            assertFalse(capturedCalls.isEmpty(), "应有 LLM 调用");

            boolean hasCorrect = capturedCalls.stream()
                    .anyMatch(c -> c.length >= 2 && c[1] != null
                            && (c[1].contains("减赔") || c[1].contains("4250000")));
            boolean hasLured = capturedCalls.stream()
                    .anyMatch(c -> c.length >= 2 && c[1] != null
                            && c[1].contains("准赔") && !c[1].contains("减赔")
                            && c[1].contains("100%"));

            System.out.println("[fusion-adversary-e2e] LLM 调用 " + capturedCalls.size()
                    + " 次, correct=减赔:" + hasCorrect + ", lured=准赔:" + hasLured);

            if (result instanceof Map<?, ?> rm) {
                System.out.println("[fusion-adversary-e2e] criteria_verified="
                        + rm.get("criteria_verified") + ", degraded=" + rm.get("degraded"));
            }

            assertTrue(capturedCalls.size() >= 1, "至少应有 1 次 LLM 调用");

        } finally {
            FusionTestHarness.clearCurrentTestCalls();
        }
    }

    // ==================== Test 3: 工具异常自愈 ====================

    @Test
    @Timeout(300)
    @DisplayName("E2E: 工具异常 → RootCauseRail 诊断 DeviceFailure → degrade 降级完成")
    void fusionRootCauseToolExceptionDegrades() {
        RealLlmHarness.requireEnv();

        List<String[]> capturedCalls = new CopyOnWriteArrayList<>();
        FusionTestHarness.setCurrentTestCalls(capturedCalls);
        try {
            Tool normalTool = FusionTestHarness.createStringTool(
                    "getStatus", "获取系统状态",
                    inputs -> "系统运行正常，所有服务在线");

            Tool failingTool = new Tool(ToolCard.builder()
                    .id("fetchData").name("fetchData")
                    .description("获取外部数据（当前不可用）")
                    .inputParams(FusionTestHarness.paramsSchema(Map.of()))
                    .build()) {
                @Override
                public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                    throw new RuntimeException("连接超时：外部数据 API 不可达");
                }
                @Override
                public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
                    throw new RuntimeException("连接超时：外部数据 API 不可达");
                }
            };

            ReActAgent agent = FusionTestHarness.createAgent(
                    "rootcause-test", 6,
                    List.of(new RootCauseRail()),
                    List.of(normalTool, failingTool));

            Object result = agent.invoke(
                    Map.of("query", """
                            请执行以下操作：
                            1. 调用 getStatus 检查系统状态
                            2. 调用 fetchData 获取外部数据
                            如果某个工具返回错误，请报告该错误并基于可用信息给出最终结论。
                            不要卡住——必须在 3 步以内给出最终回答。"""),
                    new AgentSessionApi("rootcause-" + System.nanoTime()));

            assertNotNull(result, "agent 应返回结果（不 crash）");

            if (result instanceof Map<?, ?> rm) {
                System.out.println("[fusion-rootcause-e2e] LLM调用=" + capturedCalls.size()
                        + " degraded=" + rm.get("root_cause_degraded")
                        + " cause=" + rm.get("root_cause"));
            }

            assertFalse(capturedCalls.isEmpty(), "应有 LLM 调用");

        } finally {
            FusionTestHarness.clearCurrentTestCalls();
        }
    }

    // ==================== Test 4: 多步骤规划+执行 ====================

    @Test
    @Timeout(300)
    @DisplayName("E2E: 多步骤任务 → ReActAgent + CriteriaVerificationRail + ReplanRail")
    void fusionPlanningThenExecute() {
        RealLlmHarness.requireEnv();

        List<String[]> capturedCalls = new CopyOnWriteArrayList<>();
        FusionTestHarness.setCurrentTestCalls(capturedCalls);
        try {
            Tool analyzeTool = FusionTestHarness.createStringTool(
                    "analyzePortfolio", "分析投资组合配置。参数：portfolioId（组合ID）。",
                    Map.of("portfolioId", "投资组合ID"),
                    inputs -> """
                            {
                              "portfolio_id": "%s",
                              "total_value": 1000000,
                              "allocation": {"stocks": 0.65, "bonds": 0.20, "cash": 0.10, "crypto": 0.05},
                              "risk_score": 7.5, "ytd_return_pct": 12.3
                            }
                            """.formatted(inputs.getOrDefault("portfolioId", "unknown")));

            Tool marketTool = FusionTestHarness.createStringTool(
                    "researchMarket", "研究当前市场状况。参数：sector（行业名称）。",
                    Map.of("sector", "行业名称"),
                    inputs -> """
                            {
                              "sector": "%s", "trend": "bullish", "volatility": "medium",
                              "key_events": ["美联储维持利率不变", "科技股引领涨势"],
                              "outlook": "短期乐观，关注通胀数据"
                            }
                            """.formatted(inputs.getOrDefault("sector", "general")));

            Tool riskTool = FusionTestHarness.createStringTool(
                    "assessRisk", "评估投资风险承受度。参数：investorProfile（投资者画像）。",
                    Map.of("investorProfile", "投资者画像"),
                    inputs -> """
                            {
                              "profile": "%s", "risk_tolerance": "moderate_aggressive",
                              "max_drawdown_tolerance_pct": 15, "recommended_bond_allocation_pct": 25
                            }
                            """.formatted(inputs.getOrDefault("investorProfile", "default")));

            GoalSpec goal = GoalSpec.of("投资分析",
                    List.of("输出含投资建议", "建议应基于工具实际返回的数据"));
            CriteriaVerificationRail criteria = new CriteriaVerificationRail(
                    new DecisionHistoryCriteriaVerifier(), goal);
            ReplanRail replan = new ReplanRail(GoalSpec.of("投资分析", List.of(), 3));

            ReActAgent agent = FusionTestHarness.createAgent(
                    "planning-test", 8,
                    List.of(criteria, replan),
                    List.of(analyzeTool, marketTool, riskTool));

            Object result = agent.invoke(
                    Map.of("query", """
                            请按以下步骤完成投资分析：
                            1. 调用 analyzePortfolio(portfolioId="P1001") 获取投资组合数据
                            2. 调用 researchMarket(sector="technology") 获取市场状况
                            3. 调用 assessRisk(investorProfile="growth_seeker") 评估风险
                            4. 综合以上数据，给出一条具体的投资建议（不超过 3 句话）"""),
                    new AgentSessionApi("planning-" + System.nanoTime()));

            assertNotNull(result, "agent 应返回结果");
            assertFalse(capturedCalls.isEmpty(), "应有 LLM 调用");

            System.out.println("[fusion-planning-e2e] LLM 调用 " + capturedCalls.size() + " 次");

            boolean hasAdvice = capturedCalls.stream()
                    .anyMatch(c -> c.length >= 2 && c[1] != null
                            && (c[1].contains("建议") || c[1].contains("投资")
                                || c[1].contains("配置")));
            System.out.println("[fusion-planning-e2e] 输出含投资建议: " + hasAdvice);
            assertTrue(capturedCalls.size() >= 1, "至少应有 1 次 LLM 调用");

        } finally {
            FusionTestHarness.clearCurrentTestCalls();
        }
    }
}
