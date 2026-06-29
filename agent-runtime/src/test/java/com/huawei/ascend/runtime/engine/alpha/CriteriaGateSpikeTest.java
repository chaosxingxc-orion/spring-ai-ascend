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
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 轮 3 承重证伪 spike——验证"形态 a（afterModelCall gate）"在 1.0 agent-core-java 的 ReActAgent 上是否成立。
 *
 * <p>承重背景：criteria 验证闭环嫁接 ReActAgent 的设计假设是——在 {@code afterModelCall} 钩子里，
 * 当模型给出最终答案（response 无 tool_calls）时：criteria 通过 → {@code requestForceFinish(通过结果)}
 * 强制 Complete；criteria 不通过 → {@code pushSteering(修正反馈)} 让 agent 下一轮迭代修正。
 *
 * <p>上 session 字节码调研（agents.ReActAgent.invoke，行为级描述——精确 offset 见字节码，避免轮4接线踩空）：
 * afterModelCall 设的 {@code requestForceFinish} 在 callModel 返回后、toolCalls 终态判定前被
 * {@code consumeForceFinish} 消费——非空则 setResult(forcedMap) 直接返回，跳过自然 answer 路径；
 * {@code pushSteering} 的 {@code drainSteering} 把 steering 注入下一轮 ModelContext，但不改变
 * toolCalls 终止判定（纯答案终态无下一轮）。
 *
 * <p>本 spike 用真 ReActAgent（非 RecordingAgent test double）+ 多轮 CapturingModelClient 真跑证伪：
 * <ul>
 *   <li>Spike 1（forceFinish）：纯答案场景 afterModelCall requestForceFinish → 断言 invoke 返回 forcedMap
 *       （非自然 {output,result_type}）。绿 = 形态 a forceFinish 路径成立。</li>
 *   <li>Spike 2（steering）：纯答案场景 afterModelCall pushSteering → 断言触发额外 model call。
 *       绿 = steering 能让"已决定结束"的 agent 继续迭代；红 = steering 只在迭代中生效、拦不住纯答案终态
 *       （承重发现 → 需调整形态：tool 迭代场景 / beforeModelCall reminder / forceFinish 双向）。</li>
 * </ul>
 *
 * <p>这是 throwaway 承重证伪测试——spike 绿后形态 a 确认，criteria 移植 + CriteriaVerificationRail 落地，
 * 本 spike 的 forceFinish 断言会被正式承重测试取代（steering 若红则结论入 memory）。
 */
class CriteriaGateSpikeTest {

    private static final String SPIKE_PROVIDER = "CriteriaGateSpikeProvider";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    @BeforeEach
    void resetSpikeClientCounters() {
        SpikeClient.callCount.set(0);
        SteeringRail.pushCount.set(0);
    }

    private static void ensureFactoryRegistered() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new ModelClientFactory() {
                @Override
                public String providerName() {
                    return SPIKE_PROVIDER;
                }

                @Override
                public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                    return new SpikeClient(modelConfig, clientConfig);
                }
            });
        }
    }

    private static ReActAgent newSpikeAgent(AgentRail rail) {
        ensureFactoryRegistered();
        ReActAgent agent = new ReActAgent(
                AgentCard.builder().id("agent").name("agent").description("criteria-gate-spike").build());
        ReActAgentConfig config = ReActAgentConfig.builder()
                .maxIterations(4)
                .build()
                .configureModelClient(SPIKE_PROVIDER, "key", "http://localhost", "fake-model", false);
        agent.configure(config);
        agent.registerRail(rail);
        return agent;
    }

    /** 固定返纯文本答案（无 tool_calls），用 static 计数器记录 model call 次数（spike throwaway，每 test reset）。 */
    static final class SpikeClient extends BaseModelClient {
        static final AtomicInteger callCount = new AtomicInteger(0);

        SpikeClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        public AssistantMessage invoke(
                Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            callCount.incrementAndGet();
            return new AssistantMessage("pong");
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

    /** afterModelCall：纯答案（无 tool_calls）→ requestForceFinish(forcedMap)。模拟 criteria 通过→强制 Complete。 */
    static final class ForceFinishRail extends AgentRail {
        @Override
        public void afterModelCall(AgentCallbackContext context) {
            if (isFinalAnswer(context)) {
                context.requestForceFinish(Map.of("forced", "YES", "criteria_verified", true));
            }
        }
    }

    /** afterModelCall：纯答案 → 仅首次 pushSteering（修正反馈）。模拟 criteria 不通过→让 agent 修正。 */
    static final class SteeringRail extends AgentRail {
        static final AtomicInteger pushCount = new AtomicInteger(0);

        @Override
        public void afterModelCall(AgentCallbackContext context) {
            if (isFinalAnswer(context) && pushCount.getAndIncrement() == 0) {
                context.pushSteering("你的答案未覆盖成功标准 X，请补充后再给出最终答案");
            }
        }
    }

    private static boolean isFinalAnswer(AgentCallbackContext context) {
        if (context.getInputs() instanceof ModelCallInputs inputs
                && inputs.getResponse() instanceof AssistantMessage msg) {
            return msg.getToolCalls() == null || msg.getToolCalls().isEmpty();
        }
        return false;
    }

    // ==================== Spike 1: forceFinish 证伪 ====================

    @Test
    void spike1AfterModelCallForceFinishOverridesNaturalAnswer() {
        ReActAgent agent = newSpikeAgent(new ForceFinishRail());

        Object result = agent.invoke(Map.of("query", "ping"), new AgentSessionApi("conv-spike-1"));

        // forceFinish 被消费 → invoke 直接返回 forcedMap（offset 700 consumeForceFinish → setResult → areturn）
        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertThat(resultMap).containsEntry("forced", "YES");
        // 自然 answer 路径的 output/result_type 键不应出现（forceFinish 在 toolCalls 判定前短路）
        assertThat(resultMap).doesNotContainKey("result_type");
        // forceFinish 在首轮就拦截，model 只被调用一次
        assertThat(SpikeClient.callCount.get()).isEqualTo(1);
    }

    // ==================== Spike 2: steering 证伪 ====================

    @Test
    void spike2AfterModelCallSteeringDoesNotReviveTerminatedAnswer() {
        ReActAgent agent = newSpikeAgent(new SteeringRail());

        Object result = agent.invoke(Map.of("query", "ping"), new AgentSessionApi("conv-spike-2"));

        // 承重证伪（结论翻转）：纯答案终态下 afterModelCall pushSteering 不触发额外 model call。
        // 机制：drainSteering 把 steering 文本注入"下一轮"ModelContext，但 agent 在 getToolCalls 空
        // → 最终答案路径终止，下一轮 model call 永不发生（行为级，精确 offset 见字节码）。
        // steering 只在 agent 仍处 tool 迭代时生效——拦不住"已决定结束"的纯答案终态。
        // → 形态 a 调整：criteria gate 改用 forceFinish 双向（通过→verified / 不通过→degraded 标记），
        //   steering 修正循环砍（afterModelCall gate 结构上无法强制修正，承重发现，需外层壳 defer）。
        int calls = SpikeClient.callCount.get();
        assertThat(calls)
                .as("承重证伪：纯答案终态 steering 不触发额外轮（callCount 应==1，证形态a steering 路径不成立）", calls)
                .isEqualTo(1);
        // agent 仍按自然路径返回 answer（steering 被 drain 但无下一轮消费）
        assertThat(result).isInstanceOf(Map.class);
    }
}
