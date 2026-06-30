package com.huawei.ascend.runtime.engine.alpha;

import com.openjiuwen.core.beta.model.GoalSpec;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.Model.ModelClientFactory;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.model_clients.OpenAiCompatibleModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.runtime.beta.verification.CriteriaVerificationRail;
import com.openjiuwen.runtime.beta.verification.DecisionHistoryCriteriaVerifier;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fusion e2e 测试共享工具——提供 CapturingClient、deepseek factory 注册、Agent/Tool 创建。
 *
 * <p>承重分层（铁律）：mock 控制流硬断（轮3-9）≠ 真 LLM 数据通道软观察（本 harness 的 CapturingClient）。
 * requireEnv gate 在测试方法中调用（非 factory 层），确保 env 缺失时跳过非失败。
 */
public final class FusionTestHarness {

    /** 注册到 {@link Model} 全局 registry 的 provider name。 */
    public static final String DEEPSEEK_PROVIDER = "deepseek-fusion";

    /** deepseek factory 已注册标志（static，JVM 生命周期内只注册一次）。 */
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    /** 桥接 static factory 和实例级 capturedCalls。测试方法中通过 {@link #setCurrentTestCalls} 设置。 */
    private static final ThreadLocal<List<String[]>> currentTestCalls = new ThreadLocal<>();

    private FusionTestHarness() {}

    // ==================== Factory 注册 ====================

    /**
     * 注册 deepseek CapturingClient factory（JVM 生命周期内只一次）。
     * 在 @BeforeAll 中调用。
     */
    public static void registerDeepseekFactory() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new DeepseekFusionFactory());
        }
    }

    /** 设置当前测试的 capturedCalls（ThreadLocal 桥接）。在 @Test 开头调用。 */
    public static void setCurrentTestCalls(List<String[]> calls) {
        currentTestCalls.set(calls);
    }

    /** 清除当前测试的 capturedCalls。在 @Test 的 finally 块中调用。 */
    public static void clearCurrentTestCalls() {
        currentTestCalls.remove();
    }

    // ==================== Agent 创建 ====================

    /**
     * 创建一个已配置 deepseek model client + rails + 工具的 ReActAgent。
     *
     * @param agentId    agent 标识（用于 session 追踪）
     * @param maxIter    ReActAgent 最大迭代次数
     * @param rails     要注册的 rails（可为空）
     * @param tools     要注册的工具（可为空）
     * @return 已配置的 ReActAgent（可直接 invoke）
     */
    public static ReActAgent createAgent(
            String agentId,
            int maxIter,
            List<AgentRail> rails,
            List<Tool> tools) {

        String apiKey = System.getenv("OPENJIUWEN_API_KEY");
        String apiBase = System.getenv("OPENJIUWEN_BASE_URL");
        String model = System.getenv("OPENJIUWEN_MODEL");

        ReActAgent agent = new ReActAgent(
                AgentCard.builder()
                        .id(agentId)
                        .name(agentId)
                        .description("Fusion e2e agent: " + agentId)
                        .build());

        ReActAgentConfig config = ReActAgentConfig.builder()
                .maxIterations(maxIter)
                .build()
                .configureModelClient(DEEPSEEK_PROVIDER, apiKey, apiBase, model, false);
        agent.configure(config);

        // 注册 rails
        if (rails != null) {
            for (AgentRail rail : rails) {
                agent.registerRail(rail);
            }
        }

        // 双注册工具（Tal Agent 范式）：AbilityManager 元数据 + Runner.resourceMgr execute 通道
        if (tools != null) {
            for (Tool tool : tools) {
                agent.getAbilityManager().add(tool.getCard());
                com.openjiuwen.core.runner.Runner.resourceMgr()
                        .addTool(tool, agent.getCard().getId(), true);
            }
        }

        return agent;
    }

    /**
     * 创建仅带 CriteriaVerificationRail 的 agent（最简配置）。
     */
    public static ReActAgent createCriteriaAgent(String agentId, GoalSpec goal) {
        return createAgent(agentId, 5,
                List.of(new CriteriaVerificationRail(new DecisionHistoryCriteriaVerifier(), goal)),
                List.of());
    }

    // ==================== Tool 创建辅助 ====================

    /**
     * 创建一个无参数的同步 Tool（自动生成 minimal inputSchema）。
     *
     * @param name        工具名（LLM 通过此名调用）
     * @param description 工具描述（出现在 system prompt 的 tools 列表中）
     * @param executor    工具执行逻辑（input params → output string）
     */
    public static Tool createStringTool(
            String name,
            String description,
            java.util.function.Function<Map<String, Object>, String> executor) {
        return createStringTool(name, description, Map.of(), executor);
    }

    /**
     * 创建一个带参数的同步 Tool。
     *
     * @param name        工具名
     * @param description 工具描述
     * @param paramDefs   参数名→参数描述（用于生成 JSON Schema inputParams）
     * @param executor    工具执行逻辑
     */
    public static Tool createStringTool(
            String name,
            String description,
            Map<String, String> paramDefs,
            java.util.function.Function<Map<String, Object>, String> executor) {
        return new Tool(ToolCard.builder()
                .id(name).name(name).description(description)
                .inputParams(paramsSchema(paramDefs))
                .build()) {
            @Override
            public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return executor.apply(inputs);
            }

            @Override
            public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
                return List.of(invoke(inputs, kwargs)).iterator();
            }
        };
    }

    /** 从参数描述 Map 生成 JSON Schema inputParams（DeepSeek 要求 type: "object"）。 */
    public static Map<String, Object> paramsSchema(Map<String, String> paramDefs) {
        Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new java.util.LinkedHashMap<>();
        for (var entry : paramDefs.entrySet()) {
            properties.put(entry.getKey(),
                    Map.of("type", "string", "description", entry.getValue()));
        }
        schema.put("properties", properties);
        if (!paramDefs.isEmpty()) {
            schema.put("required", List.copyOf(paramDefs.keySet()));
        }
        return schema;
    }

    // ==================== CapturingClient（真 LLM 数据通道软观察） ====================

    /**
     * 包装 {@link OpenAiCompatibleModelClient}，捕获每次 LLM 调用的 [prompt, response]。
     *
     * <p>prompt：messages 参数 toString（截断至 4000 chars）。
     * response：AssistantMessage.getContent()。
     */
    public static class CapturingClient extends BaseModelClient {
        private final OpenAiCompatibleModelClient delegate;
        private final List<String[]> calls;

        CapturingClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig,
                        List<String[]> calls) {
            super(modelConfig, clientConfig);
            this.delegate = new OpenAiCompatibleModelClient(modelConfig, clientConfig);
            this.calls = calls;
        }

        @Override
        public AssistantMessage invoke(
                Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop, BaseOutputParser outputParser,
                Float timeout, Map<String, Object> kwargs) throws Exception {
            String prompt = truncate(String.valueOf(messages), 4000);
            AssistantMessage response = delegate.invoke(
                    messages, tools, temperature, topP, model, maxTokens, stop,
                    outputParser, timeout, kwargs);
            String responseText = response.getContent() != null
                    ? String.valueOf(response.getContent()) : "";
            calls.add(new String[]{prompt, responseText});
            return response;
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(
                Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop, BaseOutputParser outputParser,
                Float timeout, Map<String, Object> kwargs) throws Exception {
            return delegate.stream(messages, tools, temperature, topP, model, maxTokens, stop,
                    outputParser, timeout, kwargs);
        }

        @Override
        public ImageGenerationResponse generateImage(
                List<UserMessage> messages, String model, String size, String negativePrompt,
                int n, boolean promptExtend, boolean watermark, int seed,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("image generation not supported");
        }

        @Override
        public AudioGenerationResponse generateSpeech(
                List<UserMessage> messages, String model, String voice, String languageType,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("speech generation not supported");
        }

        @Override
        public VideoGenerationResponse generateVideo(
                List<UserMessage> messages, String imgUrl, String audioUrl, String model,
                String size, String resolution, int duration, boolean promptExtend,
                boolean watermark, String negativePrompt, Integer seed,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException("video generation not supported");
        }

        private static String truncate(String s, int maxLen) {
            if (s == null) return "";
            return s.length() <= maxLen ? s : s.substring(0, maxLen);
        }
    }

    // ==================== Deepseek ModelClientFactory ====================

    /**
     * 注册到 {@link Model} 的 factory——providerName 固定为 {@value #DEEPSEEK_PROVIDER}。
     * 每次 ReActAgent 创建时，framework 调 create() 拿到 CapturingClient（包装 OpenAiCompatibleModelClient）。
     */
    static class DeepseekFusionFactory implements ModelClientFactory {
        @Override
        public String providerName() {
            return DEEPSEEK_PROVIDER;
        }

        @Override
        public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            List<String[]> calls = currentTestCalls.get();
            if (calls == null) {
                throw new IllegalStateException(
                        "currentTestCalls 未设置——请在 @Test 方法开头调用 FusionTestHarness.setCurrentTestCalls()");
            }
            return new CapturingClient(modelConfig, clientConfig, calls);
        }
    }
}
