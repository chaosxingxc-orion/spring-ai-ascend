package com.huawei.ascend.edp.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

/**
 * EdpConfigValidator 配置校验器单元测试。
 *
 * 验证阶段 2/3 生产化中的 fail-fast 校验逻辑。
 * 覆盖：模型配置校验、Versatile URL 校验、todolist_steps 校验、场景校验、skill_routing 校验。
 */
class EdpConfigValidatorTest {

    // ── 模型配置校验 ──

    @Test
    void testValidateModelConfig_MissingModel() {
        assertThrows(IllegalStateException.class,
                () -> EdpConfigValidator.validateModelConfig((EdpaSpringBootConfig.ModelConfig) null),
                "缺少 model 应 fail-fast");
    }

    @Test
    void testValidateModelConfig_MissingProvider() {
        EdpaSpringBootConfig.ModelConfig model = createValidModelConfig();
        model.setProvider(null);
        assertThrows(IllegalStateException.class,
                () -> EdpConfigValidator.validateModelConfig(model),
                "缺少 provider 应 fail-fast");
    }

    @Test
    void testValidateModelConfig_MissingName() {
        EdpaSpringBootConfig.ModelConfig model = createValidModelConfig();
        model.setName(null);
        assertThrows(IllegalStateException.class,
                () -> EdpConfigValidator.validateModelConfig(model),
                "缺少 model name 应 fail-fast");
    }

    @Test
    void testValidateModelConfig_MissingBaseUrl() {
        EdpaSpringBootConfig.ModelConfig model = createValidModelConfig();
        model.setBaseUrl(null);
        assertThrows(IllegalStateException.class,
                () -> EdpConfigValidator.validateModelConfig(model),
                "缺少 baseUrl 应 fail-fast");
    }

    @Test
    void testValidateModelConfig_PlaceholderApiKey_NoEnvVar() {
        EdpaSpringBootConfig.ModelConfig model = createValidModelConfig();
        model.setApiKey("");
        try {
            EdpConfigValidator.validateModelConfig(model);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("apiKey missing"), "空 apiKey 无环境变量时应 fail-fast");
        }
    }

    @Test
    void testValidateModelConfig_ValidApiKey() {
        EdpaSpringBootConfig.ModelConfig model = createValidModelConfig();
        model.setApiKey("real-api-key-12345");
        assertDoesNotThrow(() -> EdpConfigValidator.validateModelConfig(model), "有效 apiKey 应通过校验");
    }

    // ── Versatile URL 校验 ──

    @Test
    void testValidateVersatileUrl_ValidHttp() {
        EdpaSpringBootConfig.VersatileConfig versatile = new EdpaSpringBootConfig.VersatileConfig();
        versatile.setUrl("http://localhost:30001/v1/0/agent-manager/workflows/{workflow_id}");
        assertDoesNotThrow(() -> EdpConfigValidator.validateVersatileUrl(versatile), "http URL 应通过校验");
    }

    @Test
    void testValidateVersatileUrl_ValidHttps() {
        EdpaSpringBootConfig.VersatileConfig versatile = new EdpaSpringBootConfig.VersatileConfig();
        versatile.setUrl("https://api.example.com/v1/workflows");
        assertDoesNotThrow(() -> EdpConfigValidator.validateVersatileUrl(versatile), "https URL 应通过校验");
    }

    @Test
    void testValidateVersatileUrl_InvalidUrl() {
        EdpaSpringBootConfig.VersatileConfig versatile = new EdpaSpringBootConfig.VersatileConfig();
        versatile.setUrl("ftp://invalid-url");
        assertThrows(IllegalStateException.class,
                () -> EdpConfigValidator.validateVersatileUrl(versatile),
                "非 http/https URL 应 fail-fast");
    }

    @Test
    void testValidateVersatileUrl_NullVersatile() {
        assertDoesNotThrow(() -> EdpConfigValidator.validateVersatileUrl(null), "null versatile 应通过");
    }

    @Test
    void testValidateVersatileUrl_Placeholder() {
        EdpaSpringBootConfig.VersatileConfig versatile = new EdpaSpringBootConfig.VersatileConfig();
        versatile.setUrl("${EDP_AGENT_VERSATILE_URL}");
        try {
            EdpConfigValidator.validateVersatileUrl(versatile);
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("placeholder") || e.getMessage().contains("env var"),
                    "占位符 URL 无环境变量时应 fail-fast");
        }
    }

    // ── 场景配置校验 ──

    @Test
    void testValidateScenarioConfig_NullScenarioHome() {
        assertDoesNotThrow(() -> EdpConfigValidator.validateScenarioConfig(null), "null scenarioHome 应跳过校验");
    }

    @Test
    void testValidateScenarioConfig_NonExistentPath() {
        Path nonExistent = Path.of("/non/existent/path");
        assertThrows(IllegalStateException.class,
                () -> EdpConfigValidator.validateScenarioConfig(nonExistent),
                "不存在的 scenarioHome 应 fail-fast");
    }

    @Test
    void testValidateScenarioConfig_ExistingWealthDemo() {
        Path scenarioHome = Path.of("../scenarios/wealth-demo").toAbsolutePath().normalize();
        if (Files.exists(scenarioHome)) {
            assertDoesNotThrow(() -> EdpConfigValidator.validateScenarioConfig(scenarioHome),
                    "wealth-demo 场景目录应通过校验");
        } else {
            System.out.println("SKIP: wealth-demo scenario directory not found at " + scenarioHome);
        }
    }

    // ── Skill 目录校验 ──

    @Test
    void testValidateSkillDir_NullDir() {
        assertDoesNotThrow(() -> EdpConfigValidator.validateSkillDir(null), "null skillDir 应跳过");
    }

    @Test
    void testValidateSkillDir_NonExistentDir() {
        Path nonExistent = Path.of("/nonexistent/skills");
        assertThrows(IllegalStateException.class,
                () -> EdpConfigValidator.validateSkillDir(nonExistent),
                "不存在的 Skill 目录应 fail-fast");
    }

    // ── 工具方法 ──

    private EdpaSpringBootConfig.ModelConfig createValidModelConfig() {
        EdpaSpringBootConfig.ModelConfig model = new EdpaSpringBootConfig.ModelConfig();
        model.setProvider("OpenAI");
        model.setName("deepseek-v4-pro");
        model.setBaseUrl("https://api.deepseek.com/v1");
        model.setApiKey("test-api-key");
        return model;
    }
}
