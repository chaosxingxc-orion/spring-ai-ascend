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
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 轮 5 承重证伪 spike——验证 onToolException 的 {@code requestForceFinish} 是否拦住 tool_call 循环。
 *
 * <p>承重前提：轮5 RootCauseRail 想在工具失败时（onToolException）诊断根因 + dispatch 降级（forceFinish）。
 * 但轮4 已证 afterToolCall 的 forceFinish 不拦循环（offset 878 interrupt 路径）。onToolException 是工具
 * 异常钩子（RailExecutor exception 路径 fire ON_TOOL_EXCEPTION），其 forceFinish 消费点未确认——本 spike 证伪。
 *
 * <p>spike 绿（返 forcedMap + callCount==1）→ onToolException forceFinish 拦循环，RootCauseRail 用 onToolException。
 * spike 红（跑 maxIterations）→ onToolException forceFinish 不拦，RootCauseRail 用 afterModelCall（同轮4 范式）。
 */
class OnToolExceptionSpikeTest {

    private static final String SPIKE_PROVIDER = "OnToolExceptionSpikeProvider";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    private static void ensureFactoryRegistered() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new ModelClientFactory() {
                @Override
                public String providerName() {
                    return SPIKE_PROVIDER;
                }

                @Override
                public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                    return new FailingToolCallClient(modelConfig, clientConfig);
                }
            });
        }
    }

    private static ReActAgent newAgent(AgentRail rail) {
        ensureFactoryRegistered();
        ReActAgent agent = new ReActAgent(
                AgentCard.builder().id("agent").name("agent").description("onToolException-spike").build());
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(5)
                .build()
                .configureModelClient(SPIKE_PROVIDER, "key", "http://localhost", "fake-model", false));
        FailingTool failingTool = new FailingTool();
        agent.getAbilityManager().add(failingTool.getCard());
        com.openjiuwen.core.runner.Runner.resourceMgr().addTool(failingTool, agent.getCard().getId(), true);
        agent.registerRail(rail);
        return agent;
    }

    /** mock 返 __fail__ tool_call，驱动 ReActAgent executeToolCall → FailingTool.invoke throw → onToolException。 */
    static final class FailingToolCallClient extends BaseModelClient {
        static final AtomicInteger callCount = new AtomicInteger(0);

        FailingToolCallClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        public AssistantMessage invoke(
                Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            callCount.incrementAndGet();
            return AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(ToolCall.builder()
                            .id("f1")
                            .type("function")
                            .name(FailingTool.NAME)
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

    /** 工具 invoke 永抛异常——模拟设备故障，触发 onToolException。用独立名 __fail_spike__ 避免与 RootCauseRailTest 的 __fail__ 在全局 resource_mgr 冲突。 */
    static final class FailingTool extends Tool {
        static final String NAME = "__fail_spike__";

        FailingTool() {
            super(ToolCard.builder().id(NAME).name(NAME).description("always fails (device failure sim)").build());
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

    static final class ForceFinishOnToolExceptionRail extends AgentRail {
        @Override
        public void onToolException(AgentCallbackContext context) {
            context.requestForceFinish(Map.of("forced_on_tool_exception", "YES"));
        }
    }

    @Test
    void onToolExceptionForceFinishInterruptsLoopOrNot() {
        FailingToolCallClient.callCount.set(0);
        Object result = newAgent(new ForceFinishOnToolExceptionRail())
                .invoke(Map.of("query", "test"), new AgentSessionApi("on-tool-exception-spike"));

        // 承重证伪（结论翻转）：onToolException forceFinish 不拦循环（同 afterToolCall，offset 878 / exception 路径不消费）
        // → RootCauseRail 用 afterModelCall（offset 700，同轮4 范式）；onToolException 只能记录诊断信号
        assertThat(FailingToolCallClient.callCount.get())
                .as("onToolException forceFinish 不拦循环（证伪），跑到 maxIterations（callCount>1）")
                .isGreaterThan(1);
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertThat(resultMap.get("result_type")).isEqualTo("error");
    }
}
