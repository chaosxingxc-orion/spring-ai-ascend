/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.mainplan;

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
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for MainPlanAgent with a mock LLM.
 */
class MainPlanAgentIntegrationTest {

    private static final String TEST_PROVIDER = "MainPlanIntegrationTestProvider";
    private static final AtomicBoolean FACTORY_REGISTERED = new AtomicBoolean(false);

    private ReActAgent agent;
    private final List<String> dispatchedInstructions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ensureFactoryRegistered();
        Runner.setConfig(RunnerConfig.DEFAULT);
        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
        dispatchedInstructions.clear();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("Full flow: info sufficient - dispatch - summarize")
    void testInfoSufficientFlow() {
        agent = MainPlanAgentBuilder.builder()
                .modelClient(TEST_PROVIDER, "key", "base", "model", false)
                .travelPlanDispatcher(instruction -> {
                    dispatchedInstructions.add(instruction);
                    return "行程方案：上海→北京 CA1234 8月2日 08:00，北京→上海 CA5678 8月3日 20:00";
                })
                .travelerName("张三")
                .build();

        AgentSessionApi session = AgentSessionApi.create(
                "integration-test-session", null, agent.getCard());
        List<Object> results = collect(agent.stream(
                Map.of("query", "8月2日上海到北京出差2天", "conversation_id", "integration-test-session"),
                session,
                List.of(StreamMode.OUTPUT)
        ));

        // Verify dispatch was called
        assertEquals(1, dispatchedInstructions.size());
        String instruction = dispatchedInstructions.get(0);
        assertTrue(instruction.contains("上海"), "Instruction should mention 上海");
        assertTrue(instruction.contains("北京"), "Instruction should mention 北京");

        // Verify final output exists
        String finalOutput = extractFinalOutput(results);
        assertNotNull(finalOutput);
        assertTrue(finalOutput.length() > 0);
    }

    @Test
    @DisplayName("Full flow: info insufficient - interrupt - resume - dispatch")
    void testInfoInsufficientFlow() {
        agent = MainPlanAgentBuilder.builder()
                .modelClient(TEST_PROVIDER, "key", "base", "model", false)
                .travelPlanDispatcher(instruction -> {
                    dispatchedInstructions.add(instruction);
                    return "行程方案：深圳→上海 CZ9999 8月5日 09:00";
                })
                .travelerName("李四")
                .build();

        String sessionId = "interrupt-test-session";

        // First turn: LLM should call request_user_input (missing destination)
        AgentSessionApi session1 = AgentSessionApi.create(sessionId, null, agent.getCard());
        List<Object> firstTurn = collect(agent.stream(
                Map.of("query", "我要出差3天", "conversation_id", sessionId),
                session1,
                List.of(StreamMode.OUTPUT)
        ));

        // Should get an interaction chunk (interrupt)
        OutputSchema interactionChunk = findInteractionChunk(firstTurn);
        assertNotNull(interactionChunk, "Should get an interaction chunk when info is insufficient");
        InteractionOutput interactionOutput = (InteractionOutput) interactionChunk.getPayload();
        assertNotNull(interactionOutput.getId());
        assertNotNull(interactionOutput.getValue());

        // Second turn: user provides missing info
        InteractiveInput resumeInput = new InteractiveInput();
        resumeInput.update(interactionOutput.getId(), "上海");

        AgentSessionApi session2 = AgentSessionApi.create(sessionId, null, agent.getCard());
        collect(agent.stream(
                Map.of("query", resumeInput, "conversation_id", sessionId),
                session2,
                List.of(StreamMode.OUTPUT)
        ));

        // Now dispatch should have been called
        assertTrue(dispatchedInstructions.size() > 0,
                "Dispatch should be called after user provides missing info");
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> items = new ArrayList<>();
        iterator.forEachRemaining(items::add);
        return items;
    }

    private static OutputSchema findInteractionChunk(List<Object> chunks) {
        for (Object chunk : chunks) {
            if (chunk instanceof OutputSchema schema) {
                if ("__interaction__".equals(schema.getType())) {
                    return schema;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String extractFinalOutput(List<Object> chunks) {
        for (int i = chunks.size() - 1; i >= 0; i--) {
            Object chunk = chunks.get(i);
            if (chunk instanceof OutputSchema schema) {
                if (schema.getPayload() instanceof Map<?, ?>) {
                    Map<String, Object> payload = (Map<String, Object>) schema.getPayload();
                    Object outerOutput = payload.get("output");
                    if (outerOutput instanceof Map<?, ?>) {
                        return String.valueOf(((Map<?, ?>) outerOutput).get("output"));
                    } else if (outerOutput instanceof String s && !s.isEmpty()) {
                        return s;
                    }
                }
            }
        }
        return "";
    }

    private static void ensureFactoryRegistered() {
        if (FACTORY_REGISTERED.compareAndSet(false, true)) {
            Model.registerFactory(new Model.ModelClientFactory() {
                @Override
                public String providerName() {
                    return TEST_PROVIDER;
                }

                @Override
                public BaseModelClient create(ModelRequestConfig modelConfig,
                                              ModelClientConfig clientConfig) {
                    return new IntegrationTestModelClient(modelConfig, clientConfig);
                }
            });
        }
    }

    private static final class IntegrationTestModelClient extends BaseModelClient {

        private IntegrationTestModelClient(ModelRequestConfig modelConfig,
                                           ModelClientConfig clientConfig) {
            super(modelConfig, clientConfig);
        }

        @Override
        public AssistantMessage invoke(Object messages, Object tools, Float temperature,
                                       Float topP, String model, Integer maxTokens, String stop,
                                       BaseOutputParser outputParser, Float timeout,
                                       Map<String, Object> kwargs) {
            String lastToolContent = findLastToolContent(messages);

            if (lastToolContent == null) {
                String userContent = findUserContent(messages);
                if (userContent != null && containsInfoSufficient(userContent)) {
                    return AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(ToolCall.builder()
                                    .id("dispatch-call-1")
                                    .name("dispatch_travel_plan")
                                    .arguments("{\"instruction\":\"张三从上海到北京出差，"
                                            + "去程日期是2026年8月2日，返程日期是2026年8月3日。\"}")
                                    .build()))
                            .build();
                } else {
                    return AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(ToolCall.builder()
                                    .id("ask-call-1")
                                    .name("request_user_input")
                                    .arguments("{\"missing_fields\":[\"目的地\",\"出发日期\"],"
                                            + "\"follow_up_message\":\"请问您要去哪个城市出差？\"}")
                                    .build()))
                            .build();
                }
            }

            if (lastToolContent.contains("user_input_collected") || lastToolContent.contains("response")) {
                return AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(ToolCall.builder()
                                .id("dispatch-call-2")
                                .name("dispatch_travel_plan")
                                .arguments("{\"instruction\":\"李四从深圳到上海出差，出发日期是2026年8月5日。\"}")
                                .build()))
                        .build();
            }

            return new AssistantMessage("FINAL:" + lastToolContent);
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages, Object tools,
                                                      Float temperature, Float topP, String model,
                                                      Integer maxTokens, String stop,
                                                      BaseOutputParser outputParser, Float timeout,
                                                      Map<String, Object> kwargs) {
            return List.<AssistantMessageChunk>of().iterator();
        }

        @Override
        public ImageGenerationResponse generateImage(List<UserMessage> messages, String model,
                                                     String size, String negativePrompt, int n,
                                                     boolean promptExtend, boolean watermark,
                                                     int seed, Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AudioGenerationResponse generateSpeech(List<UserMessage> messages, String model,
                                                      String voice, String languageType,
                                                      Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VideoGenerationResponse generateVideo(List<UserMessage> messages, String imgUrl,
                                                     String audioUrl, String model, String size,
                                                     String resolution, int duration,
                                                     boolean promptExtend, boolean watermark,
                                                     String negativePrompt, Integer seed,
                                                     Map<String, Object> kwargs) {
            throw new UnsupportedOperationException();
        }

        private static String findLastToolContent(Object messages) {
            if (messages instanceof List<?> list) {
                for (int i = list.size() - 1; i >= 0; i--) {
                    Object item = list.get(i);
                    if (item instanceof BaseMessage msg && "tool".equals(msg.getRole())) {
                        return msg.getContentAsString();
                    }
                }
            }
            return null;
        }

        private static String findUserContent(Object messages) {
            if (messages instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof BaseMessage msg && "user".equals(msg.getRole())) {
                        return msg.getContentAsString();
                    }
                }
            }
            return null;
        }

        private static boolean containsInfoSufficient(String userContent) {
            return userContent != null
                    && (userContent.contains("上海") || userContent.contains("深圳"))
                    && userContent.contains("北京")
                    && userContent.contains("8月");
        }
    }
}
