/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.hotel.HotelPlanningAgent;
import com.huawei.ascend.examples.hotel.LlmConfig;
import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider.MemoryRecord;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class HotelAgentHandlerTest {
    private static final String HOTEL_MODEL_PROVIDER = "HotelAgentHandlerTestProvider";
    private static final AtomicBoolean MODEL_FACTORY_REGISTERED = new AtomicBoolean(false);

    @Test
    void executePrependsRecallToRealHotelAgentInputAndSavesCleanTurn() {
        ensureModelFactoryRegistered();
        CapturingModelClient.capturedMessages.clear();
        AgentExecutionContext context = context("帮我找北京酒店");
        HotelInMemoryMemoryProvider memoryProvider = new HotelInMemoryMemoryProvider();
        memoryProvider.init(context);
        memoryProvider.save(context, List.of(new MemoryRecord(
                "memory-1",
                "assistant",
                "用户上次在北京偏好国贸附近的酒店",
                Map.of())));
        try (HotelPlanningAgent agent = new HotelPlanningAgent(
                new LlmConfig(HOTEL_MODEL_PROVIDER, "key", "http://localhost", "fake-model", false))) {
            HotelAgentHandler handler = new HotelAgentHandler("hotel-planning-agent", agent, memoryProvider);

            List<AgentExecutionResult> results;
            try (Stream<?> raw = handler.execute(context);
                 Stream<AgentExecutionResult> adapted = handler.resultAdapter().adapt(raw)) {
                results = adapted.toList();
            }

            assertThat(results)
                    .singleElement()
                    .satisfies(result -> assertThat(result.outputContent())
                            .contains("酒店推荐完成"));
            assertThat(CapturingModelClient.capturedMessages)
                    .anySatisfy(message -> assertThat(CapturingModelClient.capturedContent())
                            .contains("Relevant memory:")
                            .contains("用户上次在北京偏好国贸附近的酒店")
                            .contains("帮我找北京酒店"));
            assertThat(memoryProvider.records("tenant", "user"))
                    .extracting(MemoryRecord::content)
                    .anySatisfy(content -> assertThat(content).isEqualTo("帮我找北京酒店"))
                    .anySatisfy(content -> assertThat(content).contains("酒店推荐完成"))
                    .allSatisfy(content -> assertThat(content).doesNotContain("Relevant memory:"));
        }
    }

    @Test
    void railBasedHotelHandlerInjectsRecallThroughRunnerRunAgent() {
        ensureModelFactoryRegistered();
        CapturingModelClient.capturedMessages.clear();
        AgentExecutionContext context = context("帮我找北京酒店");
        HotelInMemoryMemoryProvider memoryProvider = new HotelInMemoryMemoryProvider();
        memoryProvider.init(context);
        memoryProvider.save(context, List.of(new MemoryRecord(
                "memory-1",
                "assistant",
                "用户上次在北京偏好国贸附近的酒店",
                Map.of())));
        try (HotelPlanningAgent agent = new HotelPlanningAgent(
                new LlmConfig(HOTEL_MODEL_PROVIDER, "key", "http://localhost", "fake-model", false))) {
            RailBasedHotelAgentHandler handler =
                    new RailBasedHotelAgentHandler("hotel-planning-agent", agent, memoryProvider);

            List<?> rawResults;
            try (Stream<?> raw = handler.execute(context)) {
                rawResults = raw.toList();
            }

            assertThat(rawResults).isNotEmpty();
            assertThat(CapturingModelClient.capturedMessages)
                    .anySatisfy(message -> assertThat(message.getContentAsString())
                            .contains("Relevant memory:")
                            .contains("用户上次在北京偏好国贸附近的酒店"));
            assertThat(CapturingModelClient.capturedMessages)
                    .anySatisfy(message -> assertThat(message.getContentAsString())
                            .isEqualTo("帮我找北京酒店"));
        }
    }

    @Test
    void searchReturnsRecentMemoryWhenTextSimilarityMisses() {
        HotelInMemoryMemoryProvider memoryProvider = new HotelInMemoryMemoryProvider();
        AgentExecutionContext firstTurn = context("北京国贸600以内三星酒店");
        memoryProvider.init(firstTurn);
        memoryProvider.save(firstTurn, List.of(new MemoryRecord(
                "memory-1",
                "assistant",
                "城市北京，价格600以内，国贸附近，三星以上",
                Map.of())));

        AgentExecutionContext recallTurn = context("还是按之前的需求，换成6月25日入住");

        assertThat(memoryProvider.search(recallTurn, "还是按之前的需求，换成6月25日入住", 5))
                .extracting(MemoryProvider.MemoryHit::content)
                .contains("城市北京，价格600以内，国贸附近，三星以上");
    }

    private static AgentExecutionContext context(String userText) {
        return new AgentExecutionContext(new RuntimeIdentity("tenant", "user", "session", "task", "hotel-planning-agent"),
                "USER_MESSAGE", List.of(RuntimeMessage.user(userText)), Map.of());
    }

    private static void ensureModelFactoryRegistered() {
        if (MODEL_FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new Model.ModelClientFactory() {
                @Override
                public String providerName() {
                    return HOTEL_MODEL_PROVIDER;
                }

                @Override
                public BaseModelClient create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
                    return new CapturingModelClient(modelConfig, clientConfig);
                }
            });
        }
    }

    private static final class CapturingModelClient extends BaseModelClient {
        private static final List<BaseMessage> capturedMessages = new CopyOnWriteArrayList<>();

        private static String capturedContent() {
            return capturedMessages.stream()
                    .map(BaseMessage::getContentAsString)
                    .reduce("", (left, right) -> left + "\n" + right);
        }

        private CapturingModelClient(ModelRequestConfig modelConfig, ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature, Float topP, String model,
                Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout, Map<String, Object> kwargs) {
            capturedMessages.clear();
            if (messages instanceof List<?> list) {
                list.stream()
                        .filter(BaseMessage.class::isInstance)
                        .map(BaseMessage.class::cast)
                        .forEach(capturedMessages::add);
            }
            return new AssistantMessage("酒店推荐完成");
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools, Float temperature, Float topP,
                String model, Integer maxTokens, String stop, BaseOutputParser outputParser, Float timeout,
                Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model, String size,
                String negativePrompt, int n, boolean promptExtend, boolean watermark, int seed,
                Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model, String voice,
                String languageType, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl, String audioUrl,
                String model, String size, String resolution, int duration, boolean promptExtend, boolean watermark,
                String negativePrompt, Integer seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RailBasedHotelAgentHandler extends OpenJiuwenAgentRuntimeHandler {
        private final HotelPlanningAgent agent;
        private final MemoryProvider memoryProvider;

        private RailBasedHotelAgentHandler(String agentId, HotelPlanningAgent agent, MemoryProvider memoryProvider) {
            super(agentId);
            this.agent = Objects.requireNonNull(agent, "agent");
            this.memoryProvider = Objects.requireNonNull(memoryProvider, "memoryProvider");
        }

        @Override
        protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
            return agent.newBaseAgent();
        }

        @Override
        protected List<AgentRail> openJiuwenRails(AgentExecutionContext context) {
            return List.of(memoryRuntimeRail(context, memoryProvider));
        }
    }
}
