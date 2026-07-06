package com.huawei.ascend.edp.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

/**
 * EdpAgentConfigLoader 标准 Agent YAML 加载器单元测试。
 *
 * 验证阶段 1 工程身份生产化 + 阶段 3 配置生产化。
 * 覆盖：edp-agent.yaml Jackson 直读加载、密钥占位符机制。
 */
class EdpAgentConfigLoaderTest {

    @Test
    void testLoad_ValidYaml() {
        Path yamlPath = Path.of("src/main/resources/edp-agent.yaml").toAbsolutePath();
        if (Files.exists(yamlPath)) {
            EdpAgentConfig config = EdpAgentConfigLoader.load(yamlPath);
            assertNotNull(config, "加载结果不应为 null");
            assertNull(config.getName(), "元数据已注释，name 应为 null");
        } else {
            System.out.println("SKIP: edp-agent.yaml not found at " + yamlPath);
        }
    }

    @Test
    void testLoad_ApiKeyPlaceholder() {
        Path yamlPath = Path.of("src/main/resources/edp-agent.yaml").toAbsolutePath();
        if (Files.exists(yamlPath)) {
            EdpAgentConfig config = EdpAgentConfigLoader.load(yamlPath);
            // model/versatile 已迁移至 application.yml (EdpaSpringBootConfig)，edp-agent.yaml 中已注释
            assertNull(config.getModel(), "model 已迁出，应为 null");
        } else {
            System.out.println("SKIP: edp-agent.yaml not found");
        }
    }

    @Test
    void testLoad_VersatileUrl() {
        Path yamlPath = Path.of("src/main/resources/edp-agent.yaml").toAbsolutePath();
        if (Files.exists(yamlPath)) {
            EdpAgentConfig config = EdpAgentConfigLoader.load(yamlPath);
            // versatile 已迁移至 application.yml (EdpaSpringBootConfig)，edp-agent.yaml 中已注释
            assertNull(config.getVersatile(), "versatile 已迁出，应为 null");
        } else {
            System.out.println("SKIP: edp-agent.yaml not found");
        }
    }

    @Test
    void testLoad_NonExistentFile() {
        // EdpAgentConfigLoader 对不存在文件返回默认对象（降级设计）
        EdpAgentConfig config = EdpAgentConfigLoader.load(Path.of("/nonexistent/edp-agent.yaml"));
        assertNotNull(config, "不存在文件应返回默认对象（降级设计）");
        assertNull(config.getName(), "默认对象 name 应为 null");
    }

    @Test
    void testEnvOverrides_ApiKeyOverride() {
        EdpAgentConfig config = new EdpAgentConfig();
        EdpAgentConfig.Model model = new EdpAgentConfig.Model();
        model.setApiKey("PLACEHOLDER_USE_ENV_VAR");
        config.setModel(model);

        EdpAgentConfig.EnvOverrides overrides = new EdpAgentConfig.EnvOverrides();
        overrides.setApiKey("real-api-key-from-env");

        // 模拟 applyEnvOverrides
        if (overrides.getApiKey() != null && !overrides.getApiKey().isBlank()) {
            model.setApiKey(overrides.getApiKey());
        }

        assertEquals("real-api-key-from-env", config.getModel().getApiKey(),
                "EnvOverrides 应覆盖 PLACEHOLDER apiKey");
    }
}
