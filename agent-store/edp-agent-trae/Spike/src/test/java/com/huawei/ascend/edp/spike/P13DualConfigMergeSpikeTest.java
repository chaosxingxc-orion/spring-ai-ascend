package com.huawei.ascend.edp.spike;

import com.huawei.ascend.edp.config.EdpConfig;
import com.huawei.ascend.edp.config.EdpConfigLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("spike")
@DisplayName("P13: YAML + edp-config.yaml 双配置合并验证")
class P13DualConfigMergeSpikeTest {

    @Test
    @DisplayName("P13-1: edp-config.yaml 正常加载")
    void edpConfigYamlLoadsCorrectly() {
        Path configPath = Path.of("src/main/resources/edp-config.yaml");
        EdpConfig config = EdpConfigLoader.load(configPath);

        assertNotNull(config, "EdpConfig must not be null");
        assertNotNull(config.getScope(), "Scope must not be null");
        assertEquals("基金理财相关业务（余额查询、转账、理财推荐、购买确认）", config.getScope().getAllowed());
        assertEquals("尚在学习中", config.getScope().getOutOfScopeMessage());
    }

    @Test
    @DisplayName("P13-2: todolistSteps 正确解析")
    void todolistStepsParsedCorrectly() {
        Path configPath = Path.of("src/main/resources/edp-config.yaml");
        EdpConfig config = EdpConfigLoader.load(configPath);

        assertNotNull(config.getTodolistSteps(), "todolistSteps must not be null");
        assertEquals(4, config.getTodolistSteps().size(), "Should have 4 todolist steps");

        assertEquals(1, config.getTodolistSteps().get(0).getStepId());
        assertEquals("推荐理财产品", config.getTodolistSteps().get(0).getContent());
        assertEquals("product_recommend_skill", config.getTodolistSteps().get(0).getSkill());

        assertEquals(4, config.getTodolistSteps().get(3).getStepId());
        assertEquals("fund_planning_skill", config.getTodolistSteps().get(3).getSkill());
    }

    @Test
    @DisplayName("P13-3: limits 正确解析")
    void limitsParsedCorrectly() {
        Path configPath = Path.of("src/main/resources/edp-config.yaml");
        EdpConfig config = EdpConfigLoader.load(configPath);

        assertNotNull(config.getLimits(), "Limits must not be null");
        assertNotNull(config.getLimits().getTasks());
        assertEquals(100, config.getLimits().getTasks().get("call_versatile"));
    }

    @Test
    @DisplayName("P13-4: thinkChunk 正确解析")
    void thinkChunkParsedCorrectly() {
        Path configPath = Path.of("src/main/resources/edp-config.yaml");
        EdpConfig config = EdpConfigLoader.load(configPath);

        assertNotNull(config.getThinkChunk(), "ThinkChunk must not be null");
        assertEquals("fixed_script", config.getThinkChunk().getMode());
        assertEquals(4, config.getThinkChunk().getCharsPerFrame());
        assertEquals(2, config.getThinkChunk().getTokensBetweenFrames());
        assertEquals(50, config.getThinkChunk().getMinIntervalMs());
    }

    @Test
    @DisplayName("P13-5: llmSampling 正确解析")
    void llmSamplingParsedCorrectly() {
        Path configPath = Path.of("src/main/resources/edp-config.yaml");
        EdpConfig config = EdpConfigLoader.load(configPath);

        assertNotNull(config.getLlmSampling(), "LlmSampling must not be null");
        assertEquals(0.1, config.getLlmSampling().getTemperature());
        assertEquals(0.95, config.getLlmSampling().getTopP());
        assertEquals(0, config.getLlmSampling().getMaxRetries());
    }

    @Test
    @DisplayName("P13-6: edp-config.yaml 缺失时返回空 EdpConfig")
    void missingConfigReturnsEmptyEdpConfig() {
        Path nonExistentPath = Path.of("non-existent-config.yaml");
        EdpConfig config = EdpConfigLoader.load(nonExistentPath);

        assertNotNull(config, "Must return empty EdpConfig, not null");
        assertNull(config.getScope(), "Scope must be null for missing config");
        assertNull(config.getTodolistSteps(), "TodolistSteps must be null for missing config");
    }

}
