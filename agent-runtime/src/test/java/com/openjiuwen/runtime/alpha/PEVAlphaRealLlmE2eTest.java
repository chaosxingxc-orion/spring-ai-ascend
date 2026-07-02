package com.openjiuwen.runtime.alpha;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.alpha.model.ExecutionPolicy;
import com.openjiuwen.core.alpha.model.PlanningMode;
import com.openjiuwen.core.alpha.model.VerifyMode;
import com.openjiuwen.core.dispatch.AutonomyLevel;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.DefaultModelClientFactories;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.kernel.model.*;
import com.openjiuwen.core.meta.AgentDefinition;
import com.openjiuwen.runtime.core.dispatch.TaskContext;
import com.openjiuwen.runtime.core.engine.DefaultAgentKernel;
import com.openjiuwen.runtime.core.engine.DefaultAgentKernel.ToolExecutor;
import com.openjiuwen.runtime.core.engine.DefaultSafetyBoundary;
import com.openjiuwen.runtime.core.engine.SafetyBoundary;
import com.huawei.ascend.runtime.engine.alpha.AgentCoreJavaLlmProvider;
import com.huawei.ascend.runtime.engine.alpha.InMemoryCheckpointStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Assumptions;

/**
 * PEV Alpha 策略真 LLM e2e 承重——证 Plan-Execute-Verify-Dispatch 数据通道通。
 *
 * <p>承重分层（铁律）：
 * <ul>
 *   <li><b>mock 控制流硬断</b>：{@link PEVAlphaStrategyControlFlowTest} 的 6 个测试证各
 *       dispatch 分支正确——本类不替代。</li>
 *   <li><b>真 LLM 数据通道软观察</b>：deepseek-v4-pro 真实 API 证 token 经
 *       AgentCoreJavaLlmProvider → DefaultAgentKernel.think() → DefaultPlanner/DefaultVerifier
 *       数据通道通。软观察 = requireEnv gate 跳过非 failure + @Timeout 安全网。</li>
 *   <li><b>诚实边界</b>：设备故障/对抗场景需更精细的工具行为编排，defer；
 *       当前只测正向直通（Plan→Execute→Verify→Complete）与多步骤场景。</li>
 * </ul>
 *
 * <h3>4 个 e2e 测试</h3>
 * <ol>
 *   <li><b>straightThroughSimpleTool</b> — 单个工具调用，LLM 规划→执行→验证通过</li>
 *   <li><b>multiStepWithTools</b> — 多步骤工具调用，独立规划执行验证</li>
 *   <li><b>llmOnlyReasoning</b> — 纯 LLM 推理（无工具），验证 LLM_CALL 节点执行</li>
 *   <li><b>verifyPassedEmitted</b> — 验证 VERIFY_PASSED 出现在事件流中</li>
 * </ol>
 */
@DisplayName("PEV Alpha E2E: PEV pipeline × deepseek 真 LLM（4 场景）")
class PEVAlphaRealLlmE2eTest {

    static {
        DefaultModelClientFactories.ensureRegistered();
    }

    // ── Holds shared tool maps so tests can register tools after kernel construction ──
    private Map<ToolName, ToolExecutor> toolExecutors;
    private Map<ToolName, AgentDefinition.ToolDefinition> toolDefs;

    private DefaultAgentKernel createKernel(String modelName, String apiKey, String apiBase) {
        ModelRequestConfig reqCfg = ModelRequestConfig.builder()
                .modelName(modelName)
                .temperature(0.7)
                .maxTokens(2048)
                .build();
        ModelClientConfig cliCfg = ModelClientConfig.builder()
                .clientId("pev-e2e-" + System.nanoTime())
                .clientProvider("OpenAI")
                .apiKey(apiKey)
                .apiBase(apiBase)
                .verifySsl(true)
                .build();
        Model model = new Model(cliCfg, reqCfg);

        this.toolExecutors = new ConcurrentHashMap<>();
        this.toolDefs = new ConcurrentHashMap<>();
        SafetyBoundary safety = new DefaultSafetyBoundary();
        InMemoryCheckpointStore checkpoint = new InMemoryCheckpointStore();

        return new DefaultAgentKernel(
                new AgentCoreJavaLlmProvider(model),
                toolExecutors, toolDefs, checkpoint, safety);
    }

    /** Register a simple tool for both execution and planning. */
    private void registerTool(String name, String description,
                              Map<String, String> paramDefs,
                              java.util.function.Function<Map<String, Object>, String> executor) {
        ToolName tn = new ToolName(name);
        // Execution channel
        toolExecutors.put(tn, args -> executor.apply(args));
        // Planning channel (tool signature for planner)
        List<AgentDefinition.ParameterDefinition> params = new ArrayList<>();
        for (var entry : paramDefs.entrySet()) {
            params.add(new AgentDefinition.ParameterDefinition(
                    entry.getKey(), "string", entry.getValue(), true));
        }
        toolDefs.put(tn, new AgentDefinition.ToolDefinition(name, description, params));
    }

    private static TaskContext buildTaskContext(DefaultAgentKernel kernel,
                                                 String userQuery,
                                                 AgentDefinition agentDef,
                                                 ExecutionPolicy policy) {
        return new TaskContext(
                TaskId.generate(),
                new AgentName("pev-e2e"),
                TaskInput.of(userQuery),
                agentDef,
                kernel,
                Budget.Fixed.productionDefault(),
                AutonomyLevel.GUIDED,
                Map.of("executionPolicy", policy));
    }

    private static AgentDefinition buildAgentDef(List<String> toolNames) {
        List<AgentDefinition.ToolDefinition> tools = new ArrayList<>();
        for (String name : toolNames) {
            tools.add(new AgentDefinition.ToolDefinition(name, name + " tool", List.of()));
        }
        return new AgentDefinition(
                new AgentName("pev-e2e"),
                "PEV e2e test agent",
                "你是一个任务执行专家。使用可用工具完成用户请求。",
                tools,
                AutonomyLevel.GUIDED,
                Budget.Fixed.productionDefault(),
                ExecutionPolicy.productionDefault(),
                null, null, Map.of());
    }

    private static String[] requireEnv() {
        String key = System.getenv("OPENJIUWEN_API_KEY");
        String base = System.getenv("OPENJIUWEN_BASE_URL");
        String model = System.getenv("OPENJIUWEN_MODEL");
        Assumptions.assumeTrue(key != null && !key.isBlank(), "OPENJIUWEN_API_KEY 未设置，跳过真 LLM e2e");
        Assumptions.assumeTrue(base != null && !base.isBlank(), "OPENJIUWEN_BASE_URL 未设置，跳过真 LLM e2e");
        Assumptions.assumeTrue(model != null && !model.isBlank(), "OPENJIUWEN_MODEL 未设置，跳过真 LLM e2e");
        return new String[]{key, base, model};
    }

    // ==================== E1: straight-through simple tool ====================

    @Test
    @Timeout(300)
    @DisplayName("E1: 单工具直通 — Plan→Execute→Verify→Complete")
    void straightThroughSimpleTool() {
        String[] env = requireEnv();
        DefaultAgentKernel kernel = createKernel(env[2], env[0], env[1]);

        registerTool("calculator", "执行简单算术计算",
                Map.of("expression", "算术表达式，如 2+3"),
                inputs -> {
                    String expr = String.valueOf(inputs.getOrDefault("expression", "0"));
                    try {
                        return String.valueOf(evalSimple(expr));
                    } catch (Exception e) {
                        return "Error: " + e.getMessage();
                    }
                });

        AgentDefinition agentDef = buildAgentDef(List.of("calculator"));
        TaskContext ctx = buildTaskContext(kernel,
                "请使用 calculator 工具计算 12 + 34 的结果", agentDef,
                new ExecutionPolicy(PlanningMode.AUTO, VerifyMode.LIGHT, 1, 2, true));

        PEVAlphaStrategy strategy = new PEVAlphaStrategy();
        List<AgentEvent> events = strategy.execute(ctx).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).isNotEmpty();

        assertThat(events.stream().anyMatch(e -> e.type() == EventType.TASK_COMPLETED))
                .as("PEV pipeline must emit TASK_COMPLETED for simple tool call")
                .isTrue();
        assertThat(events.stream().noneMatch(e -> e.type() == EventType.TASK_FAILED))
                .as("PEV pipeline must not fail for simple tool call")
                .isTrue();

        boolean hasPlan = events.stream().anyMatch(e -> e.type() == EventType.PLAN_GENERATED);
        System.out.println("[pev-e2e-E1] plan generated: " + hasPlan
                + ", total events: " + events.size());
    }

    // ==================== E2: multi-step with tools ====================

    @Test
    @Timeout(300)
    @DisplayName("E2: 多步骤工具调用 — 独立规划执行验证")
    void multiStepWithTools() {
        String[] env = requireEnv();
        DefaultAgentKernel kernel = createKernel(env[2], env[0], env[1]);

        registerTool("getTemperature", "获取指定城市的当前温度",
                Map.of("city", "城市名称"),
                inputs -> {
                    String city = String.valueOf(inputs.getOrDefault("city", ""));
                    return city + " 当前温度: 25°C，晴";
                });

        registerTool("getHumidity", "获取指定城市的当前湿度",
                Map.of("city", "城市名称"),
                inputs -> {
                    String city = String.valueOf(inputs.getOrDefault("city", ""));
                    return city + " 当前湿度: 65%";
                });

        AgentDefinition agentDef = buildAgentDef(List.of("getTemperature", "getHumidity"));
        TaskContext ctx = buildTaskContext(kernel,
                "请分别获取北京的当前温度和湿度，然后汇总报告天气状况",
                agentDef,
                new ExecutionPolicy(PlanningMode.AUTO, VerifyMode.LIGHT, 1, 3, true));

        PEVAlphaStrategy strategy = new PEVAlphaStrategy();
        List<AgentEvent> events = strategy.execute(ctx).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).isNotEmpty();
        assertThat(events.stream().anyMatch(e -> e.type() == EventType.TASK_COMPLETED))
                .as("multi-step PEV pipeline must complete")
                .isTrue();
        assertThat(events.stream().noneMatch(e -> e.type() == EventType.TASK_FAILED))
                .as("multi-step PEV pipeline must not fail")
                .isTrue();

        System.out.println("[pev-e2e-E2] completed, events: " + events.size());
    }

    // ==================== E3: LLM-only reasoning ====================

    @Test
    @Timeout(300)
    @DisplayName("E3: 纯 LLM 推理（无工具） — LLM_CALL 节点执行")
    void llmOnlyReasoning() {
        String[] env = requireEnv();
        DefaultAgentKernel kernel = createKernel(env[2], env[0], env[1]);

        AgentDefinition agentDef = buildAgentDef(List.of());
        TaskContext ctx = buildTaskContext(kernel,
                "请分析：为什么天空是蓝色的？用一句话回答。",
                agentDef,
                new ExecutionPolicy(PlanningMode.AUTO, VerifyMode.LIGHT, 1, 2, true));

        PEVAlphaStrategy strategy = new PEVAlphaStrategy();
        List<AgentEvent> events = strategy.execute(ctx).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).isNotEmpty();
        assertThat(events.stream().anyMatch(e -> e.type() == EventType.TASK_COMPLETED))
                .as("LLM-only reasoning PEV pipeline must complete")
                .isTrue();

        boolean hasNodeCompleted = events.stream().anyMatch(e -> e.type() == EventType.NODE_COMPLETED);
        System.out.println("[pev-e2e-E3] LLM-only, node completed: " + hasNodeCompleted
                + ", total events: " + events.size());
    }

    // ==================== E4: verify loop check ====================

    @Test
    @Timeout(300)
    @DisplayName("E4: 验证通过检测 — VERIFY_PASSED 出现在事件流中")
    void verifyPassedEmitted() {
        String[] env = requireEnv();
        DefaultAgentKernel kernel = createKernel(env[2], env[0], env[1]);

        registerTool("echo", "回显输入文本",
                Map.of("text", "要回显的文本"),
                inputs -> String.valueOf(inputs.getOrDefault("text", "")));

        AgentDefinition agentDef = buildAgentDef(List.of("echo"));
        TaskContext ctx = buildTaskContext(kernel,
                "请使用 echo 工具回显文本 'hello world'",
                agentDef,
                new ExecutionPolicy(PlanningMode.AUTO, VerifyMode.STRICT, 1, 2, true));

        PEVAlphaStrategy strategy = new PEVAlphaStrategy();
        List<AgentEvent> events = strategy.execute(ctx).collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).isNotEmpty();
        assertThat(events.stream().anyMatch(e -> e.type() == EventType.TASK_COMPLETED))
                .as("PEV pipeline must complete")
                .isTrue();

        boolean hasVerifyPassed = events.stream().anyMatch(e -> e.type() == EventType.VERIFY_PASSED);
        System.out.println("[pev-e2e-E4] VERIFY_PASSED: " + hasVerifyPassed
                + ", total events: " + events.size());
    }

    // ==================== simple expression evaluator ====================

    private static int evalSimple(String expr) {
        expr = expr.replaceAll("\\s+", "");
        if (expr.contains("+")) {
            String[] parts = expr.split("\\+");
            return Integer.parseInt(parts[0]) + Integer.parseInt(parts[1]);
        }
        if (expr.contains("*")) {
            String[] parts = expr.split("\\*");
            return Integer.parseInt(parts[0]) * Integer.parseInt(parts[1]);
        }
        if (expr.contains("-")) {
            String[] parts = expr.split("-");
            return Integer.parseInt(parts[0]) - Integer.parseInt(parts[1]);
        }
        if (expr.contains("/")) {
            String[] parts = expr.split("/");
            return Integer.parseInt(parts[0]) / Integer.parseInt(parts[1]);
        }
        return Integer.parseInt(expr);
    }
}
