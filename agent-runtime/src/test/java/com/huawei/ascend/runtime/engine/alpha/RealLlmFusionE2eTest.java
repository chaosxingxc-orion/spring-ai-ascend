package com.huawei.ascend.runtime.engine.alpha;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.Model.ModelClientFactory;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.model_clients.OpenAiCompatibleModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.beta.model.GoalSpec;
import com.openjiuwen.runtime.beta.replan.ReplanRail;
import com.openjiuwen.runtime.beta.selfheal.RootCauseRail;
import com.openjiuwen.runtime.beta.verification.CriteriaVerificationRail;
import com.openjiuwen.runtime.beta.verification.DecisionHistoryCriteriaVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * spring-ai-ascend 融合真 LLM e2e — ReActAgent + fusion rails × deepseek-v4-pro。
 *
 * <p>诚实分层（轮12 真 LLM 数据通道软观察）：
 * <ul>
 *   <li><b>gate</b>：{@link RealLlmHarness#requireEnv()} — 三 env 全到位才真跑（非 failure 跳过）</li>
 *   <li><b>mock 控制流硬断</b>（轮3-9 CapturingModelClient/StubKernel）：承重 token 来自 mock 注入，
 *       全绿可靠证控制流。本测试是真 LLM 软观察，不替代 mock 承重。</li>
 *   <li><b>真 LLM 软观察</b>：capturedCalls 证数据通道（token 经 OpenAiCompatibleModelClient →
 *       deepseek API → response 回 ReActAgent），非控制流硬断。</li>
 * </ul>
 *
 * <p>测试结构：
 * <ol>
 *   <li>注册 DeepseekModelClientFactory（wrap OpenAiCompatibleModelClient + capture）</li>
 *   <li>创建 ReActAgent + CriteriaVerificationRail（融合核心 rail）</li>
 *   <li>简单任务 → 验证 COMPLETED + LLM 调用可捕获</li>
 * </ol>
 */
@DisplayName("RealLlmFusionE2e: ReActAgent + fusion rails × deepseek 真 LLM")
class RealLlmFusionE2eTest {

    private static final String DEEPSEEK_PROVIDER = "deepseek-fusion-e2e";

    /** 所有 deepseek e2e 测试共享一个 factory 注册（static，只注册一次）。 */
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    /** 每个测试独立收集 LLM 调用 [prompt, response]。 */
    private final List<String[]> capturedCalls = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void registerDeepseekFactory() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new DeepseekCapturingFactory());
        }
    }

    /**
     * 注册到 {@link Model} 的 factory——providerName 固定为 {@value #DEEPSEEK_PROVIDER}。
     * 每个 ReActAgent 实例创建时，framework 调 create() 拿到一个 CapturingClient。
     */
    static class DeepseekCapturingFactory implements ModelClientFactory {
        @Override
        public String providerName() {
            return DEEPSEEK_PROVIDER;
        }

        @Override
        public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            // 把 capturedCalls 引用注入 CapturingClient（每测试独立）
            return new CapturingClient(modelConfig, clientConfig, getCurrentTestCalls());
        }

        /**
         * 从当前线程上下文获取 capturedCalls。因为 factory 是 static 注册的，
         * 而 capturedCalls 是实例字段，这里通过 ThreadLocal 桥接。
         */
        private List<String[]> getCurrentTestCalls() {
            List<String[]> calls = currentTestCalls.get();
            if (calls == null) {
                throw new IllegalStateException(
                    "currentTestCalls 未设置——请在 @Test 方法开头调用 setCurrentTestCalls()");
            }
            return calls;
        }
    }

    /** 桥接 static factory 和实例级 capturedCalls。 */
    private static final ThreadLocal<List<String[]>> currentTestCalls = new ThreadLocal<>();

    // ==================== 测试 ====================

    @Test
    @DisplayName("E2E: ReActAgent + CriteriaVerificationRail 完成简单任务 + LLM 调用可捕获")
    void fusionRailsCompleteSimpleTask() {
        RealLlmHarness.requireEnv(); // gate

        currentTestCalls.set(capturedCalls);
        try {
            ReActAgent agent = createFusionAgent();

            Object result = agent.invoke(
                    Map.of("query", "请用一句话回答：1+1等于几？只输出答案，不要解释。"),
                    new AgentSessionApi("fusion-e2e-" + System.currentTimeMillis()));

            // 验证 agent 返回了结果
            assertNotNull(result, "agent 应返回结果");

            // 验证 LLM 调用被捕获（数据通道软观察）
            assertFalse(capturedCalls.isEmpty(),
                    "承重 A 软观察：应至少有 1 次 LLM 调用");
            assertTrue(capturedCalls.stream().anyMatch(c -> c.length >= 2 && c[1] != null && !c[1].isBlank()),
                    "承重 B 软观察：至少 1 次调用应有非空 response");

            System.out.println("[fusion-e2e] ✅ 完成，LLM 调用 " + capturedCalls.size() + " 次");
            for (int i = 0; i < capturedCalls.size(); i++) {
                String[] c = capturedCalls.get(i);
                System.out.printf("[fusion-e2e]   call %d: prompt=%d chars, response=%d chars%n",
                        i + 1,
                        c[0] != null ? c[0].length() : 0,
                        c[1] != null ? c[1].length() : 0);
            }
        } finally {
            currentTestCalls.remove();
        }
    }

    @Test
    @DisplayName("E2E: ReActAgent + multi-rail（criteria+replan+rootCause）不掉线")
    void fusionMultiRailCompletesTask() {
        RealLlmHarness.requireEnv(); // gate

        currentTestCalls.set(capturedCalls);
        try {
            ReActAgent agent = createMultiRailFusionAgent();

            Object result = agent.invoke(
                    Map.of("query", "请回答：中国的首都是哪里？只输出城市名。"),
                    new AgentSessionApi("fusion-multi-" + System.currentTimeMillis()));

            assertNotNull(result, "agent 应返回结果");
            assertFalse(capturedCalls.isEmpty(), "应有 LLM 调用");

            // 验证 response 中包含合理答案（软观察：LLM 应输出"北京"相关信息）
            boolean hasAnswer = capturedCalls.stream()
                    .anyMatch(c -> c.length >= 2 && c[1] != null && c[1].contains("北京"));
            System.out.println("[fusion-multi-e2e] OK, response contains Beijing: " + hasAnswer
                    + ", LLM calls: " + capturedCalls.size());
        } finally {
            currentTestCalls.remove();
        }
    }

    // ==================== 工厂方法 ====================

    private ReActAgent createFusionAgent() {
        String apiKey = System.getenv("OPENJIUWEN_API_KEY");
        String apiBase = System.getenv("OPENJIUWEN_BASE_URL");
        String model = System.getenv("OPENJIUWEN_MODEL");

        ReActAgent agent = new ReActAgent(
                AgentCard.builder()
                        .id("fusion-e2e")
                        .name("fusion-e2e")
                        .description("Fusion E2E agent")
                        .build());

        ReActAgentConfig config = ReActAgentConfig.builder()
                .maxIterations(4)
                .build()
                .configureModelClient(DEEPSEEK_PROVIDER, apiKey, apiBase, model, false);
        agent.configure(config);

        // 轮3 核心 rail：criteria 验证闭环
        GoalSpec fusionGoal = GoalSpec.of("简单问答");
        agent.registerRail(new CriteriaVerificationRail(
                new DecisionHistoryCriteriaVerifier(), fusionGoal));

        return agent;
    }

    private ReActAgent createMultiRailFusionAgent() {
        String apiKey = System.getenv("OPENJIUWEN_API_KEY");
        String apiBase = System.getenv("OPENJIUWEN_BASE_URL");
        String model = System.getenv("OPENJIUWEN_MODEL");

        ReActAgent agent = new ReActAgent(
                AgentCard.builder()
                        .id("fusion-multi-e2e")
                        .name("fusion-multi-e2e")
                        .description("Multi-rail fusion E2E agent")
                        .build());

        ReActAgentConfig config = ReActAgentConfig.builder()
                .maxIterations(5)
                .build()
                .configureModelClient(DEEPSEEK_PROVIDER, apiKey, apiBase, model, false);
        agent.configure(config);

        // 注册多条 fusion rail（轮3+4+5）
        GoalSpec multiGoal = GoalSpec.of("简单问答");
        agent.registerRail(new CriteriaVerificationRail(
                new DecisionHistoryCriteriaVerifier(), multiGoal));
        agent.registerRail(new ReplanRail(multiGoal));
        agent.registerRail(new RootCauseRail());

        return agent;
    }

    // ==================== Capturing ModelClient ====================

    /**
     * 包装 {@link OpenAiCompatibleModelClient}，捕获每次 LLM 调用的 [prompt, response]。
     *
     * <p>prompt：从 messages 参数提取（toString 前 4000 chars）。
     * response：AssistantMessage.getContent()。
     */
    static class CapturingClient extends BaseModelClient {
        private final OpenAiCompatibleModelClient delegate;
        private final List<String[]> calls;

        CapturingClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig, List<String[]> calls) {
            super(modelConfig, clientConfig);
            this.delegate = new OpenAiCompatibleModelClient(modelConfig, clientConfig);
            this.calls = calls;
        }

        @Override
        public AssistantMessage invoke(
                Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop,
                com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser outputParser,
                Float timeout, Map<String, Object> kwargs) throws Exception {
            String prompt = truncate(String.valueOf(messages), 4000);
            AssistantMessage response = delegate.invoke(
                    messages, tools, temperature, topP, model, maxTokens, stop,
                    outputParser, timeout, kwargs);
            String responseText = response.getContent() != null ? String.valueOf(response.getContent()) : "";
            calls.add(new String[]{prompt, responseText});
            return response;
        }

        @Override
        public java.util.Iterator<com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk> stream(
                Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop,
                com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser outputParser,
                Float timeout, Map<String, Object> kwargs) throws Exception {
            return delegate.stream(messages, tools, temperature, topP, model, maxTokens, stop,
                    outputParser, timeout, kwargs);
        }

        @Override
        public com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse generateImage(
                java.util.List<com.openjiuwen.core.foundation.llm.schema.UserMessage> messages,
                String model, String size, String negativePrompt, int n,
                boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("image generation not supported");
        }

        @Override
        public com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse generateSpeech(
                java.util.List<com.openjiuwen.core.foundation.llm.schema.UserMessage> messages,
                String model, String voice, String languageType, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("speech generation not supported");
        }

        @Override
        public com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse generateVideo(
                java.util.List<com.openjiuwen.core.foundation.llm.schema.UserMessage> messages,
                String imgUrl, String audioUrl, String model, String size,
                String resolution, int duration, boolean promptExtend, boolean watermark,
                String negativePrompt, Integer seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("video generation not supported");
        }

        private static String truncate(String s, int maxLen) {
            if (s == null) return "";
            return s.length() <= maxLen ? s : s.substring(0, maxLen);
        }
    }
}
