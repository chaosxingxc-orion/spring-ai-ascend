package com.huawei.ascend.agentsdk.example;

import com.huawei.ascend.agentsdk.example.tools.CalcDiscountTool;
import com.huawei.ascend.agentsdk.example.tools.QueryOrderTool;
import com.huawei.ascend.agentsdk.example.tools.ReadFileTool;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class OpenJiuwenExampleSupport {
    private OpenJiuwenExampleSupport() {
    }

    static Path resolveYamlPath(String[] args, String defaultYaml) {
        if (args.length > 0 && !args[0].isBlank()) {
            return Path.of(args[0]).toAbsolutePath().normalize();
        }
        Path fromExampleDirectory = Path.of("openjiuwen", defaultYaml).toAbsolutePath().normalize();
        if (Files.exists(fromExampleDirectory)) {
            return fromExampleDirectory;
        }
        return Path.of("examples", "agent-sdk-example", "openjiuwen", defaultYaml)
                .toAbsolutePath()
                .normalize();
    }

    static String userInput(String[] args) {
        return args.length > 1 ? args[1] : """
                请查询订单 A-10086，并计算金额 120 的折扣。
                你必须调用 queryOrder 和 calcDiscount。
                请使用 order-analysis 技能解读订单状态。
                请使用 report-writing 技能整理最终证明报告。
                最终回答必须列出工具返回的 proof 字段和使用过的技能名称。
                """;
    }

    static void printReactExecution(String title, Path effectiveYaml, String userInput, ReActAgent agent) {
        resetToolCounters();
        System.out.println("=== " + title + " ===");
        System.out.println("agent.yaml: " + effectiveYaml);
        System.out.println("input: " + userInput);
        System.out.println("agent: " + agent.getClass().getName());
        System.out.println("agentId: " + agent.getCard().getId());
        System.out.println();

        Object result = agent.invoke(Map.of("query", userInput), new InMemorySession(agent.getCard().getId() + "-session"));
        System.out.println("=== OpenJiuwen Result ===");
        System.out.println(result);
        verifyProof(result, true);
    }

    static void printDeepAgentExecution(String title, Path effectiveYaml, String userInput, DeepAgent agent) {
        resetToolCounters();
        System.out.println("=== " + title + " ===");
        System.out.println("agent.yaml: " + effectiveYaml);
        System.out.println("input: " + userInput);
        System.out.println("agent: " + agent.getClass().getName());
        System.out.println("agentId: " + agent.getCard().getId());
        System.out.println();

        Object result = agent.run(Map.of("query", userInput));
        System.out.println("=== OpenJiuwen Result ===");
        System.out.println(result);
        verifyProof(result, false);
    }

    private static void resetToolCounters() {
        ReadFileTool.reset();
        QueryOrderTool.reset();
        CalcDiscountTool.reset();
    }

    private static void verifyProof(Object result, boolean requireReadFileTool) {
        String text = String.valueOf(result);
        require(QueryOrderTool.invocationCount() > 0,
                "queryOrder tool was not invoked by the real model call");
        require(CalcDiscountTool.invocationCount() > 0,
                "calcDiscount tool was not invoked by the real model call");
        if (requireReadFileTool) {
            require(ReadFileTool.invocationCount() >= 2,
                    "readFile tool did not read both skill files");
        }
        require(text.contains("queryOrder-java-tool-executed"),
                "model result did not include queryOrder tool proof");
        require(text.contains("calcDiscount-java-tool-executed"),
                "model result did not include calcDiscount tool proof");
        require(text.contains("ORDER_ANALYSIS_SKILL_USED"),
                "model result did not include order-analysis skill proof");
        require(text.contains("REPORT_WRITING_SKILL_USED"),
                "model result did not include report-writing skill proof");
        System.out.println();
        System.out.println("=== Proof Verification ===");
        System.out.println("readFile invocations: " + ReadFileTool.invocationCount());
        System.out.println("queryOrder invocations: " + QueryOrderTool.invocationCount());
        System.out.println("calcDiscount invocations: " + CalcDiscountTool.invocationCount());
        System.out.println("skill markers: ORDER_ANALYSIS_SKILL_USED, REPORT_WRITING_SKILL_USED");
        System.out.println("verification: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Example proof verification failed: " + message);
        }
    }

    private static final class InMemorySession implements Session {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();

        private InMemorySession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> state) {
            this.state.putAll(state);
        }
    }
}
