package com.openjiuwen.runtime.beta.replan;

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
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ReplanRail 承重测试——Replan 计数/超限 escalate 嫁接 1.0 ReActAgent 的核心承重契约。
 *
 * <p>承重 IFF（plan V2 轮4）：Replan 计数 ⟺ 超限 escalate。
 * <ul>
 *   <li>超限（replanCount >= maxReplanCount）→ afterModelCall forceFinish(degraded)，拦在 replan 工具执行前。</li>
 *   <li>未超限 → withReplan 计数放行，agent 继续。</li>
 * </ul>
 *
 * <p>承重断言真化：用 ReplanRail.replanCount() + MultiTurnReplanClient.callCount 计数（剥 token→RED IFF），
 * 非弱断言。mutation-RED：剥 withReplan 计数行 → canReplan 永真 → 永不 forceFinish → 超限测试 RED。
 */
@DisplayName("ReplanRail: Replan 计数/超限 escalate 承重")
class ReplanRailTest {

    private static final String REPLAN_RAIL_PROVIDER = "ReplanRailProvider";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    @BeforeEach
    void resetCounters() {
        MultiTurnReplanClient.callCount.set(0);
        MultiTurnReplanClient.replanRounds.set(Integer.MAX_VALUE);
    }

    private static void ensureFactoryRegistered() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new ModelClientFactory() {
                @Override
                public String providerName() {
                    return REPLAN_RAIL_PROVIDER;
                }

                @Override
                public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                    return new MultiTurnReplanClient(modelConfig, clientConfig);
                }
            });
        }
    }

    private static ReActAgent newAgentWithRail(ReplanRail rail) {
        ensureFactoryRegistered();
        ReActAgent agent = new ReActAgent(
                AgentCard.builder().id("agent").name("agent").description("replan-rail").build());
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(10)
                .build()
                .configureModelClient(REPLAN_RAIL_PROVIDER, "key", "http://localhost", "fake-model", false));
        // 双注册（同 ReplanSpikeTest）：AbilityManager 元数据 + Runner.resourceMgr execute 通道。
        // resource_mgr 注册让 executeToolCall 真调 ReplanTool.invoke（未超限放行后工具 execute 成功）。
        ReplanTool replanTool = new ReplanTool();
        agent.getAbilityManager().add(replanTool.getCard());
        com.openjiuwen.core.runner.Runner.resourceMgr().addTool(replanTool, agent.getCard().getId(), true);
        agent.registerRail(rail);
        return agent;
    }

    /** mock：前 replanRounds 轮返 __replan__ tool_call，之后返纯答案。replanRounds=MAX_VALUE 一直返 replan。 */
    static final class MultiTurnReplanClient extends BaseModelClient {
        static final AtomicInteger callCount = new AtomicInteger(0);
        static final AtomicInteger replanRounds = new AtomicInteger(Integer.MAX_VALUE);

        MultiTurnReplanClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        public AssistantMessage invoke(
                Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            int n = callCount.incrementAndGet();
            if (n > replanRounds.get()) {
                return new AssistantMessage("task done answer");
            }
            return AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(ToolCall.builder()
                            .id("r" + n)
                            .type("function")
                            .name(ReplanTool.TOOL_NAME)
                            .arguments("{\"replan_reason\":\"stuck-" + n + "\",\"new_approach\":\"approach-" + n + "\"}")
                            .build()))
                    .finishReason("tool_calls")
                    .build();
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

    @Nested
    @DisplayName("超限 escalate IFF（剥 withReplan 计数→canReplan 永真→永不 forceFinish→RED）")
    class OverLimitEscalation {

        @Test
        @DisplayName("超过 maxReplanCount 的第 N+1 次 replan → forceFinish degraded（offset 700 拦在工具执行前）")
        void replanOverLimitEscalatesToDegraded() {
            GoalSpec goal = GoalSpec.of("test-goal", List.of(), 2); // maxReplanCount=2
            ReplanRail rail = new ReplanRail(goal);
            MultiTurnReplanClient.replanRounds.set(Integer.MAX_VALUE); // 一直返 replan

            Object result = newAgentWithRail(rail)
                    .invoke(Map.of("query", "q"), new AgentSessionApi("over-limit"));

            // 第 1,2 次 replan：canReplan true → 计数放行；第 3 次：canReplan(2<2) false → 超限 forceFinish
            assertThat(result).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertThat(resultMap.get(ReplanRail.REPLAN_EXCEEDED_KEY))
                    .as("超限应 escalate（IFF：replanCount>=max ⟹ forceFinish degraded）")
                    .isEqualTo(true);
            assertThat(resultMap.get(ReplanRail.DEGRADED_KEY)).isEqualTo(true);
            assertThat(resultMap.get(ReplanRail.REPLAN_COUNT_KEY)).isEqualTo(2);
            assertThat(rail.replanCount())
                    .as("前 2 次 withReplan 计数，第 3 次超限不计数")
                    .isEqualTo(2);
            assertThat(MultiTurnReplanClient.callCount.get())
                    .as("第 3 次 model call 后 forceFinish（offset 700），无第 4 次")
                    .isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("未超限放行（计数但继续）")
    class UnderLimitContinuation {

        @Test
        @DisplayName("replan 次数 < maxReplanCount → 计数放行，agent 自然完成")
        void replanUnderLimitCompletesNormally() {
            GoalSpec goal = GoalSpec.of("test-goal", List.of(), 5); // maxReplanCount=5
            ReplanRail rail = new ReplanRail(goal);
            MultiTurnReplanClient.replanRounds.set(2); // 前 2 次 replan + 第 3 次答案

            Object result = newAgentWithRail(rail)
                    .invoke(Map.of("query", "q"), new AgentSessionApi("under-limit"));

            // 第 1,2 次 replan：计数放行；第 3 次：返答案（无 replan tool_call）→ rail 不拦 → agent 自然完成
            assertThat(rail.replanCount())
                    .as("2 次 replan 计数")
                    .isEqualTo(2);
            assertThat(result).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertThat(resultMap.get("result_type"))
                    .as("未超限，agent 自然 answer 完成")
                    .isEqualTo("answer");
            assertThat(resultMap).doesNotContainKey(ReplanRail.DEGRADED_KEY);
        }

        @Test
        @DisplayName("无 replan tool_call 时 rail 不介入（计数 0）")
        void noReplanNoIntervention() {
            GoalSpec goal = GoalSpec.of("test-goal", List.of(), 3);
            ReplanRail rail = new ReplanRail(goal);
            MultiTurnReplanClient.replanRounds.set(0); // 第 1 次就返答案

            Object result = newAgentWithRail(rail)
                    .invoke(Map.of("query", "q"), new AgentSessionApi("no-replan"));

            assertThat(rail.replanCount()).isEqualTo(0);
            assertThat(result).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertThat(resultMap.get("result_type")).isEqualTo("answer");
        }
    }
}
