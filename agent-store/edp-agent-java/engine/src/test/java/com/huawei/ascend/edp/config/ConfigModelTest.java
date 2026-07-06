package com.huawei.ascend.edp.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置模型类单元测试（ScenarioConfig, ScenarioScopeConfig, ScenarioSkillRouting）。
 *
 * 验证阶段 2 架构包结构生产化中的场景解耦模型类。
 */
class ConfigModelTest {

    // architecture 已删除——MCP 先行架构已作为框架默认配置放入 engine/src/main/resources/governance/planrule.yaml 的 supplementary_prompt

    // ── ScenarioDiscoveryConfig ──

    @Test
    void testScenarioDiscoveryConfig_Fields() {
        ScenarioDiscoveryConfig discovery = new ScenarioDiscoveryConfig();
        discovery.setBasePath("scenarios");
        discovery.setActiveScenario("wealth-demo");
        assertEquals("scenarios", discovery.getBasePath());
        assertEquals("wealth-demo", discovery.getActiveScenario());
    }

    // ── ScenarioDiscoveryConfig ──

    @Test
    void testEnvOverrides_Fields() {
        EdpAgentConfig.EnvOverrides overrides = new EdpAgentConfig.EnvOverrides();
        overrides.setApiKey("test-key");
        overrides.setModelProvider("OpenAI");
        overrides.setModelName("deepseek-v4");
        overrides.setModelBaseUrl("https://api.test.com");
        overrides.setVersatileUrl("http://localhost:30001");
        assertEquals("test-key", overrides.getApiKey());
        assertEquals("OpenAI", overrides.getModelProvider());
        assertEquals("deepseek-v4", overrides.getModelName());
        assertEquals("https://api.test.com", overrides.getModelBaseUrl());
        assertEquals("http://localhost:30001", overrides.getVersatileUrl());
    }

    @Test
    void testEnvOverrides_DefaultNull() {
        EdpAgentConfig.EnvOverrides overrides = new EdpAgentConfig.EnvOverrides();
        assertNull(overrides.getApiKey(), "默认 apiKey 应为 null");
        assertNull(overrides.getModelProvider(), "默认 modelProvider 应为 null");
        assertNull(overrides.getModelName(), "默认 modelName 应为 null");
        assertNull(overrides.getModelBaseUrl(), "默认 modelBaseUrl 应为 null");
        assertNull(overrides.getVersatileUrl(), "默认 versatileUrl 应为 null");
    }
}
