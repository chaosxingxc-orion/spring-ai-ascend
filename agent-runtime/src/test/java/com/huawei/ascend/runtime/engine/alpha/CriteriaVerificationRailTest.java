package com.huawei.ascend.runtime.engine.alpha;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.beta.model.GoalSpec;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.Model.ModelClientFactory;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.runtime.beta.verification.CriteriaVerificationRail;
import com.openjiuwen.runtime.beta.verification.DecisionHistoryCriteriaVerifier;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * CriteriaVerificationRail 嫁接承重——criteria 验证闭环通过 forceFinish 双向 gate 真嫁接到 1.0 ReActAgent。
 *
 * <p>承重：用真 ReActAgent（非 RecordingAgent）+ 真 model call（ControllableClient 返纯答案）+
 * CriteriaVerificationRail（afterModelCall verify → forceFinish 双向）。断言 invoke 返回的 forced Map
 * 含 criteria 终态标记（VERIFIED/RESULT/DEGRADED/UNMET），证：
 * <ul>
 *   <li>criteria 通过（output 覆盖 successCriteria）→ forceFinish verified=true（锁定正确终态）。</li>
 *   <li>criteria 不通过（output 未覆盖）→ forceFinish verified=false + degraded + unmet（诚实标记）。</li>
 * </ul>
 *
 * <p>这是 spike 1（forceFinish 消费）+ DecisionHistoryCriteriaVerifier（verify 三步）的端到端嫁接承重。
 * forced Map 的 VERIFIED 键由 criteria verify 真值驱动（IFF：改 output 覆盖性 → verified 翻转）。
 */
class CriteriaVerificationRailTest {

    private static final String RAIL_PROVIDER = "CriteriaVerificationRailProvider";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    @BeforeEach
    void resetAnswer() {
        ControllableClient.answer = "pong";
    }

    private static void ensureFactoryRegistered() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new ModelClientFactory() {
                @Override
                public String providerName() {
                    return RAIL_PROVIDER;
                }

                @Override
                public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                    return new ControllableClient(modelConfig, clientConfig);
                }
            });
        }
    }

    private static ReActAgent newAgentWithGoal(GoalSpec goal) {
        ensureFactoryRegistered();
        ReActAgent agent = new ReActAgent(
                AgentCard.builder().id("agent").name("agent").description("criteria-rail").build());
        ReActAgentConfig config = ReActAgentConfig.builder()
                .maxIterations(3)
                .build()
                .configureModelClient(RAIL_PROVIDER, "key", "http://localhost", "fake-model", false);
        agent.configure(config);
        // 无 kernel 构造器 → ASSUME_FAIL fallback；criteria 用规则判断（output 覆盖）
        agent.registerRail(new CriteriaVerificationRail(new DecisionHistoryCriteriaVerifier(), goal));
        return agent;
    }

    @Test
    void criteriaPassLocksVerifiedTerminal() {
        ControllableClient.answer = "the apple and banana are fresh today";
        GoalSpec goal = GoalSpec.of("pick fruit", List.of("apple banana"));

        Object result = newAgentWithGoal(goal).invoke(Map.of("query", "pick"), new AgentSessionApi("rail-verified"));

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertThat(resultMap.get(CriteriaVerificationRail.VERIFIED_KEY)).isEqualTo(true);
        assertThat(resultMap.get(CriteriaVerificationRail.RESULT_KEY)).isEqualTo("PASS");
        assertThat((String) resultMap.get(CriteriaVerificationRail.OUTPUT_KEY)).contains("apple");
        assertThat(resultMap).doesNotContainKey(CriteriaVerificationRail.DEGRADED_KEY);
    }

    @Test
    void criteriaFailMarksDegradedTerminal() {
        ControllableClient.answer = "totally unrelated content about weather xyz";
        GoalSpec goal = GoalSpec.of("pick fruit", List.of("apple banana"));

        Object result = newAgentWithGoal(goal).invoke(Map.of("query", "pick"), new AgentSessionApi("rail-degraded"));

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertThat(resultMap.get(CriteriaVerificationRail.VERIFIED_KEY)).isEqualTo(false);
        assertThat(resultMap.get(CriteriaVerificationRail.RESULT_KEY)).isEqualTo("FAIL");
        assertThat(resultMap.get(CriteriaVerificationRail.DEGRADED_KEY)).isEqualTo(true);
        assertThat((List<?>) resultMap.get(CriteriaVerificationRail.UNMET_KEY))
                .as("未满足的 criteria 应列出具体标准（IFF：改 output 覆盖性 → verified 翻转）")
                .anySatisfy(msg -> assertThat(msg.toString()).contains("apple banana"));
    }

    /** 可控纯答案 client：返 static answer（纯文本，无 tool_call），每测试 @BeforeEach 重置。 */
    static final class ControllableClient extends BaseModelClient {
        static volatile String answer = "pong";

        ControllableClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        public AssistantMessage invoke(
                Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            return new AssistantMessage(answer);
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(
                Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(
                List<UserMessage> messages, String model, String size, String negativePrompt, int n,
                boolean promptExtend, boolean watermark, int seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AudioGenerationResponse generateSpeech(
                List<UserMessage> messages, String model, String voice, String languageType,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VideoGenerationResponse generateVideo(
                List<UserMessage> messages, String imgUrl, String audioUrl, String model, String size,
                String resolution, int duration, boolean promptExtend, boolean watermark,
                String negativePrompt, Integer seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }
    }
}
