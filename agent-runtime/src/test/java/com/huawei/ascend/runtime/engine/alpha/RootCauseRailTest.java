package com.huawei.ascend.runtime.engine.alpha;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.runtime.beta.selfheal.RootCauseRail;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RootCauseRail 嫁接承重——工具失败（设备故障）→ 根因诊断 DeviceFailure → Degrade → afterModelCall
 * forceFinish degraded，端到端嫁接 1.0 ReActAgent。
 *
 * <p>承重 IFF：工具失败 → onToolException 诊断 DeviceFailure → dispatch Degrade（标记）→ 下一轮
 * afterModelCall forceFinish degraded（offset 700 拦循环）。
 *
 * <p>承重证伪（spike）：onToolException forceFinish 不拦循环（OnToolExceptionSpikeTest 证）→ 故
 * RootCauseRail 双钩子协作（onToolException 诊断标记 + afterModelCall 终止）。
 *
 * <p>mutation-RED：剥 onToolException 诊断（不标记 pendingAction）→ afterModelCall 无 Degrade → 跑
 * maxIterations → RED（证 IFF 非恒真）。
 */
class RootCauseRailTest {

    private static final String RAIL_PROVIDER = "RootCauseRailProvider";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    @BeforeEach
    void resetCounters() {
        MultiTurnToolClient.callCount.set(0);
        MultiTurnToolClient.toolName.set("__fail__");
        MultiTurnToolClient.answerAfterN.set(Integer.MAX_VALUE);
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
                    return new MultiTurnToolClient(modelConfig, clientConfig);
                }
            });
        }
    }

    private static ReActAgent newAgent(RootCauseRail rail, Tool extraTool) {
        ensureFactoryRegistered();
        ReActAgent agent = new ReActAgent(
                AgentCard.builder().id("agent").name("agent").description("root-cause-rail").build());
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(10)
                .build()
                .configureModelClient(RAIL_PROVIDER, "key", "http://localhost", "fake-model", false));
        // 双注册（同轮4 范式）：__fail__ + __ok__ 工具都注册，让 execute 找到
        registerTool(agent, new FailingTool());
        if (extraTool != null) {
            registerTool(agent, extraTool);
        }
        agent.registerRail(rail);
        return agent;
    }

    private static void registerTool(ReActAgent agent, Tool tool) {
        agent.getAbilityManager().add(tool.getCard());
        com.openjiuwen.core.runner.Runner.resourceMgr().addTool(tool, agent.getCard().getId(), true);
    }

    /** mock：第 answerAfterN 轮前返 tool_call(toolName)，之后返纯答案。 */
    static final class MultiTurnToolClient extends BaseModelClient {
        static final AtomicInteger callCount = new AtomicInteger(0);
        static final AtomicInteger answerAfterN = new AtomicInteger(Integer.MAX_VALUE);
        static final java.util.concurrent.atomic.AtomicReference<String> toolName =
                new java.util.concurrent.atomic.AtomicReference<>("__fail__");

        MultiTurnToolClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        public AssistantMessage invoke(
                Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            int n = callCount.incrementAndGet();
            if (n > answerAfterN.get()) {
                return new AssistantMessage("task done answer");
            }
            return AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(ToolCall.builder()
                            .id("t" + n)
                            .type("function")
                            .name(toolName.get())
                            .arguments("{}")
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

    /** 永抛异常——模拟设备故障，触发 onToolException。 */
    static final class FailingTool extends Tool {
        static final String NAME = "__fail__";

        FailingTool() {
            super(ToolCard.builder().id(NAME).name(NAME).description("always fails").build());
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            throw new RuntimeException("simulated device failure");
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            throw new RuntimeException("simulated device failure");
        }
    }

    /** 正常工具——返成功，不触发 onToolException（对照）。 */
    static final class OkTool extends Tool {
        static final String NAME = "__ok__";

        OkTool() {
            super(ToolCard.builder().id(NAME).name(NAME).description("always succeeds").build());
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return Map.of("status", "ok");
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.of(invoke(inputs, kwargs)).iterator();
        }
    }

    @Test
    void deviceFailureDegradesAfterToolException() {
        MultiTurnToolClient.toolName.set(FailingTool.NAME);
        MultiTurnToolClient.answerAfterN.set(Integer.MAX_VALUE); // 一直 tool_call __fail__

        Object result = newAgent(new RootCauseRail(), null)
                .invoke(Map.of("query", "q"), new AgentSessionApi("device-failure"));

        // 第1轮 tool_call __fail__ → execute throw → onToolException 诊断 DeviceFailure→Degrade（标记）
        // → 循环继续 → 第2轮 model call → afterModelCall forceFinish degraded（offset 700）
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertThat(resultMap.get(RootCauseRail.ROOT_CAUSE_DEGRADED_KEY))
                .as("设备故障应根因降级（IFF：tool throw ⟹ DeviceFailure ⟹ Degrade ⟹ forceFinish）")
                .isEqualTo(true);
        assertThat(resultMap.get(RootCauseRail.ROOT_CAUSE_KEY)).isEqualTo("DeviceFailure");
        assertThat(MultiTurnToolClient.callCount.get())
                .as("第2轮 afterModelCall forceFinish（offset 700），callCount==2")
                .isEqualTo(2);
    }

    @Test
    void normalToolDoesNotDegrade() {
        MultiTurnToolClient.toolName.set(OkTool.NAME);
        MultiTurnToolClient.answerAfterN.set(1); // 第1次 tool_call __ok__ + 第2次答案

        Object result = newAgent(new RootCauseRail(), new OkTool())
                .invoke(Map.of("query", "q"), new AgentSessionApi("normal-tool"));

        // 无工具失败 → 无 onToolException → 无 degraded → agent 自然完成
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertThat(resultMap.get("result_type")).isEqualTo("answer");
        assertThat(resultMap).doesNotContainKey(RootCauseRail.ROOT_CAUSE_DEGRADED_KEY);
    }
}
