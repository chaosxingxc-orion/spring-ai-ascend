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
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.runtime.beta.replan.ReplanTool;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 轮 4 承重证伪 spike——确定 ReplanRail 该用哪个 rail 钩子拦截 __replan__ tool_call。
 *
 * <p>承重背景：轮4 把 Replan 嫁接到 1.0 ReActAgent（replan 虚拟工具承载 Replan 意图）。ReplanRail
 * 需在 LLM 产出 __replan__ tool_call 后做计数/超限 escalate。问题：用哪个钩子 + forceFinish 能拦住？
 *
 * <p>承重证伪结果（颠覆 afterToolCall 假设）：
 * <ul>
 *   <li>{@code afterToolCall} 的 forceFinish <b>不拦</b> tool_call 循环——ReActAgent 跑到 maxIterations
 *       （offset 878 consumeForceFinish 在 tool-interrupt 处理路径，正常 tool 循环不消费）。</li>
 *   <li>{@code afterModelCall} 的 forceFinish <b>能拦</b>——看 response.getToolCalls() 含 __replan__ 时
 *       forceFinish，offset 700 消费（callModel 后、toolCalls 终态判定前），拦在 replan 工具执行前。</li>
 * </ul>
 *
 * <p>形态调整：ReplanRail 用 afterModelCall（看 response 的 toolCalls 是否含 __replan__）计数/超限
 * forceFinish——与轮3 CriteriaVerificationRail 同一 afterModelCall forceFinish 路径，承重一致。
 */
class ReplanSpikeTest {

    private static final String REPLAN_SPIKE_PROVIDER = "ReplanSpikeProvider";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    private static void ensureFactoryRegistered() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new ModelClientFactory() {
                @Override
                public String providerName() {
                    return REPLAN_SPIKE_PROVIDER;
                }

                @Override
                public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                    return new ReplanSpikeClient(modelConfig, clientConfig);
                }
            });
        }
    }

    private static ReActAgent newReplanAgent(AgentRail rail) {
        ensureFactoryRegistered();
        ReActAgent agent = new ReActAgent(
                AgentCard.builder().id("agent").name("agent").description("replan-spike").build());
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(5)
                .build()
                .configureModelClient(REPLAN_SPIKE_PROVIDER, "key", "http://localhost", "fake-model", false));
        // 双注册：AbilityManager.add(ToolCard) 元数据 + Runner.resourceMgr().addTool(Tool) execute 通道。
        // resource_mgr 注册让 executeToolCall 真能调 ReplanTool.invoke（不抛 Ability not found），
        // 从而 afterToolCall 真 fire（RailExecutor 正常路径）——spike 证伪实验的前提。
        ReplanTool replanTool = new ReplanTool();
        agent.getAbilityManager().add(replanTool.getCard());
        com.openjiuwen.core.runner.Runner.resourceMgr().addTool(replanTool, agent.getCard().getId(), true);
        agent.registerRail(rail);
        return agent;
    }

    /** mock 返带 __replan__ tool_call 的 AssistantMessage。 */
    static final class ReplanSpikeClient extends BaseModelClient {
        static final AtomicInteger modelCallCount = new AtomicInteger(0);

        ReplanSpikeClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        public AssistantMessage invoke(
                Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            modelCallCount.incrementAndGet();
            return AssistantMessage.builder()
                    .content("")
                    .toolCalls(List.of(ToolCall.builder()
                            .id("r1")
                            .type("function")
                            .name(ReplanTool.TOOL_NAME)
                            .arguments("{\"replan_reason\":\"stuck\",\"new_approach\":\"try-x\"}")
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

    /** afterToolCall forceFinish——证 offset 878 不能拦正常 tool 循环。 */
    static final class ForceFinishAfterToolCallRail extends AgentRail {
        @Override
        public void afterToolCall(AgentCallbackContext context) {
            context.requestForceFinish(Map.of("forced_after_toolcall", "YES"));
        }
    }

    /** afterModelCall 看 response.getToolCalls() 含 __replan__ → forceFinish（offset 700 路径）。 */
    static final class ForceFinishOnReplanModelRail extends AgentRail {
        @Override
        public void afterModelCall(AgentCallbackContext context) {
            if (isReplanToolCall(context)) {
                context.requestForceFinish(Map.of("forced_on_replan_model", "YES"));
            }
        }
    }

    private static boolean isReplanToolCall(AgentCallbackContext context) {
        if (context.getInputs() instanceof ModelCallInputs inputs
                && inputs.getResponse() instanceof AssistantMessage msg
                && msg.getToolCalls() != null) {
            return msg.getToolCalls().stream()
                    .anyMatch(tc -> ReplanTool.TOOL_NAME.equals(tc.getName()));
        }
        return false;
    }

    @Test
    void afterToolCallForceFinishDoesNotInterruptToolCallLoop() {
        ReplanSpikeClient.modelCallCount.set(0);
        Object result = newReplanAgent(new ForceFinishAfterToolCallRail())
                .invoke(Map.of("query", "test"), new AgentSessionApi("replan-spike-tool"));

        // 承重证伪：afterToolCall forceFinish 不拦 tool_call 循环——跑到 maxIterations（modelCallCount>1）
        // （offset 878 consumeForceFinish 在 tool-interrupt 路径，正常循环不消费）
        assertThat(ReplanSpikeClient.modelCallCount.get())
                .as("afterToolCall forceFinish 不拦循环（证伪），跑到 maxIterations（modelCallCount>1）")
                .isGreaterThan(1);
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> toolResultMap = (Map<String, Object>) result;
        assertThat(toolResultMap.get("result_type")).isEqualTo("error");
    }

    @Test
    void afterModelCallForceFinishInterruptsReplanToolCall() {
        ReplanSpikeClient.modelCallCount.set(0);
        Object result = newReplanAgent(new ForceFinishOnReplanModelRail())
                .invoke(Map.of("query", "test"), new AgentSessionApi("replan-spike-model"));

        // afterModelCall forceFinish 拦 replan tool_call → 返 forcedMap（offset 700，拦在 toolCall 执行前）
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertThat(resultMap).containsEntry("forced_on_replan_model", "YES");
        assertThat(ReplanSpikeClient.modelCallCount.get())
                .as("afterModelCall forceFinish 拦在 replan toolCall 执行前（callCount==1）")
                .isEqualTo(1);
    }
}
