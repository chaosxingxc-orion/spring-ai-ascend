/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.mainplan;

import com.huawei.ascend.examples.travel.mainplan.constant.AgentConstants;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link MainPlanAgentBuilder}.
 */
class MainPlanAgentBuilderTest {

    private static final Function<String, String> STUB_DISPATCHER = s -> "stub result";

    @BeforeEach
    void setUp() {
        Runner.setConfig(RunnerConfig.DEFAULT);
        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    @DisplayName("Should build ReActAgent with all required params")
    void testBuildSuccess() {
        ReActAgent agent = MainPlanAgentBuilder.builder()
                .modelClient("test-provider", "test-key", "http://localhost:8080", "test-model", false)
                .travelPlanDispatcher(STUB_DISPATCHER)
                .build();

        assertNotNull(agent);
        assertEquals(AgentConstants.AGENT_ID, agent.getCard().getId());
    }

    @Test
    @DisplayName("Should throw when modelProvider is missing")
    void testBuildMissingModelProvider() {
        MainPlanAgentBuilder.MainPlanAgentBuilderBuilder builder = MainPlanAgentBuilder.builder()
                .travelPlanDispatcher(STUB_DISPATCHER);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    @DisplayName("Should throw when travelPlanDispatcher is missing")
    void testBuildMissingDispatcher() {
        MainPlanAgentBuilder.MainPlanAgentBuilderBuilder builder = MainPlanAgentBuilder.builder()
                .modelClient("test-provider", "test-key", "http://localhost:8080", "test-model", false);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    @DisplayName("Should apply default city to system prompt")
    void testDefaultCityApplied() {
        ReActAgent agent = MainPlanAgentBuilder.builder()
                .modelClient("test-provider", "test-key", "http://localhost:8080", "test-model", false)
                .travelPlanDispatcher(STUB_DISPATCHER)
                .defaultCity("上海")
                .build();

        ReActAgentConfig config = (ReActAgentConfig) agent.getConfig();
        assertNotNull(config);
        assertNotNull(config.getPromptTemplate());
    }

    @Test
    @DisplayName("Should apply traveler name to system prompt")
    void testTravelerNameApplied() {
        ReActAgent agent = MainPlanAgentBuilder.builder()
                .modelClient("test-provider", "test-key", "http://localhost:8080", "test-model", false)
                .travelPlanDispatcher(STUB_DISPATCHER)
                .travelerName("张三")
                .build();

        assertNotNull(agent);
    }

    @Test
    @DisplayName("Should apply system prompt override when provided")
    void testSystemPromptOverride() {
        ReActAgent agent = MainPlanAgentBuilder.builder()
                .modelClient("test-provider", "test-key", "http://localhost:8080", "test-model", false)
                .travelPlanDispatcher(STUB_DISPATCHER)
                .systemPromptOverride("自定义系统提示词")
                .build();

        ReActAgentConfig config = (ReActAgentConfig) agent.getConfig();
        assertNotNull(config.getPromptTemplate());
        String promptContent = config.getPromptTemplate().get(0).get("content");
        assertEquals("自定义系统提示词", promptContent);
    }

    @Test
    @DisplayName("Should use default maxIterations when not specified")
    void testDefaultMaxIterations() {
        ReActAgent agent = MainPlanAgentBuilder.builder()
                .modelClient("test-provider", "test-key", "http://localhost:8080", "test-model", false)
                .travelPlanDispatcher(STUB_DISPATCHER)
                .build();

        ReActAgentConfig config = (ReActAgentConfig) agent.getConfig();
        assertEquals(10, config.getMaxIterations());
    }

    @Test
    @DisplayName("Should apply custom maxIterations")
    void testCustomMaxIterations() {
        ReActAgent agent = MainPlanAgentBuilder.builder()
                .modelClient("test-provider", "test-key", "http://localhost:8080", "test-model", false)
                .travelPlanDispatcher(STUB_DISPATCHER)
                .maxIterations(5)
                .build();

        ReActAgentConfig config = (ReActAgentConfig) agent.getConfig();
        assertEquals(5, config.getMaxIterations());
    }
}
