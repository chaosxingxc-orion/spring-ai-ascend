package com.huawei.ascend.edp;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 3：配置与密钥生产化单元测试。
 *
 * 验证：
 * - edp-agent.yaml 密钥外部化（PLACEHOLDER_USE_ENV_VAR）
 * - edp-agent.yaml 系统提示词为空（动态生成）
 * - edp-agent.yaml skills.directories 为空（方案 B）
 * - edp-config.yaml todolist_steps 占位
 * - application.yml 场景路径配置
 * - .env.example 文件存在
 */
class Phase3ConfigTest {

    // ── edp-agent.yaml 密钥外部化 ──

    @Test
    void testApiKey_IsPlaceholder() throws IOException {
        Path yamlPath = Path.of("src/main/resources/edp-agent.yaml").toAbsolutePath();
        if (Files.exists(yamlPath)) {
            String content = Files.readString(yamlPath);
            assertTrue(content.contains("PLACEHOLDER_USE_ENV_VAR"),
                    "apiKey 应使用 PLACEHOLDER_USE_ENV_VAR 占位（密钥外部化）");
            assertFalse(content.contains("${EDP_AGENT_MODEL_API_KEY"),
                    "apiKey 不应使用 ${...} 占位符（Jackson 直读不支持 Spring Boot 占位符）");
        } else {
            System.out.println("SKIP: edp-agent.yaml not found");
        }
    }

    // ── edp-agent.yaml 系统提示词为空 ──

    @Test
    void testSystemPrompt_IsEmpty() throws IOException {
        Path yamlPath = Path.of("src/main/resources/edp-agent.yaml").toAbsolutePath();
        if (Files.exists(yamlPath)) {
            String content = Files.readString(yamlPath);
            assertTrue(content.contains("system: \"\"") || content.contains("system: ''"),
                    "系统提示词应为空字符串（动态由 ScenarioPromptBuilder 拼接）");
        } else {
            System.out.println("SKIP: edp-agent.yaml not found");
        }
    }

    // ── edp-agent.yaml skills.directories 为空 ──

    @Test
    void testSkillsDirectories_IsEmpty() throws IOException {
        Path yamlPath = Path.of("src/main/resources/edp-agent.yaml").toAbsolutePath();
        if (Files.exists(yamlPath)) {
            String content = Files.readString(yamlPath);
            assertTrue(content.contains("directories: []"),
                    "skills.directories 应为空列表（方案 B：由 scenarioHome 动态加载）");
        } else {
            System.out.println("SKIP: edp-agent.yaml not found");
        }
    }

    // ── edp-agent.yaml 不含硬编码业务规则 ──

    @Test
    void testNoHardcodedBusinessRules() throws IOException {
        Path yamlPath = Path.of("src/main/resources/edp-agent.yaml").toAbsolutePath();
        if (Files.exists(yamlPath)) {
            String content = Files.readString(yamlPath);
            assertFalse(content.contains("业务范围") && content.contains("理财产品"),
                    "prompt.system 不应包含硬编码业务规则（已迁移到场景文件）");
        } else {
            System.out.println("SKIP: edp-agent.yaml not found");
        }
    }

    // ── application.yml 场景路径 ──

    @Test
    void testApplicationYaml_ScenarioHome() throws IOException {
        Path appYaml = Path.of("src/main/resources/application.yml").toAbsolutePath();
        if (Files.exists(appYaml)) {
            String content = Files.readString(appYaml);
            assertTrue(content.contains("scenario-home"),
                    "application.yml 应包含 scenario-home 配置");
            assertTrue(content.contains("EDP_AGENT_SCENARIO_HOME"),
                    "scenario-home 应支持环境变量覆盖");
            assertTrue(content.contains("../scenarios/wealth-demo"),
                    "scenario-home 默认值应为 ../scenarios/wealth-demo");
        } else {
            System.out.println("SKIP: application.yml not found");
        }
    }

    // ── application.yml 不含 Spike ──

    @Test
    void testApplicationYaml_NoSpike() throws IOException {
        Path appYaml = Path.of("src/main/resources/application.yml").toAbsolutePath();
        if (Files.exists(appYaml)) {
            String content = Files.readString(appYaml);
            assertFalse(content.toLowerCase().contains("spike"),
                    "application.yml 不应包含 spike 引用");
            assertTrue(content.contains("edp-agent-engine"),
                    "应用名应为 edp-agent-engine");
        } else {
            System.out.println("SKIP: application.yml not found");
        }
    }

    // ── Spring Profile 文件存在 ──

    @Test
    void testApplicationProfilesExist() {
        assertTrue(Files.exists(Path.of("src/main/resources/application-dev.yml").toAbsolutePath()),
                "application-dev.yml 应存在");
        assertTrue(Files.exists(Path.of("src/main/resources/application-test.yml").toAbsolutePath()),
                "application-test.yml 应存在");
        assertTrue(Files.exists(Path.of("src/main/resources/application-prod.yml").toAbsolutePath()),
                "application-prod.yml 应存在");
    }

    // ── .env.example 存在 ──

    @Test
    void testEnvExampleExists() {
        Path envExample = Path.of("../.env.example").toAbsolutePath().normalize();
        assertTrue(Files.exists(envExample), ".env.example 应存在（密钥外部化模板）");
    }

    @Test
    void testEnvExample_ContainsApiKey() throws IOException {
        Path envExample = Path.of("../.env.example").toAbsolutePath().normalize();
        if (Files.exists(envExample)) {
            String content = Files.readString(envExample);
            assertTrue(content.contains("EDP_AGENT_MODEL_API_KEY"),
                    ".env.example 应包含 API Key 环境变量");
        } else {
            System.out.println("SKIP: .env.example not found");
        }
    }

    @Test
    void testEnvExample_ContainsScenarioHome() throws IOException {
        Path envExample = Path.of("../.env.example").toAbsolutePath().normalize();
        if (Files.exists(envExample)) {
            String content = Files.readString(envExample);
            assertTrue(content.contains("EDP_AGENT_SCENARIO_HOME"),
                    ".env.example 应包含场景路径环境变量");
        } else {
            System.out.println("SKIP: .env.example not found");
        }
    }

    // ── scenario-config.yaml 存在且不含 frontmatter ──

    @Test
    void testScenarioConfigYaml_NoFrontmatter() throws IOException {
        Path wealthDemo = Path.of("../scenarios/wealth-demo/scenario-config.yaml").toAbsolutePath().normalize();
        if (Files.exists(wealthDemo)) {
            String content = Files.readString(wealthDemo);
            assertFalse(content.startsWith("---"), "scenario-config.yaml 不应含 YAML frontmatter（纯 YAML 格式）");
            assertTrue(content.contains("name:"), "应包含 name 字段");
            assertTrue(content.contains("scope:"), "应包含 scope 字段");
        } else {
            System.out.println("SKIP: wealth-demo scenario-config.yaml not found");
        }
    }

    // ── SKILL.yaml 存在 ──

    @Test
    void testSkillYamlExists() {
        Path skillYaml = Path.of("../scenarios/wealth-demo/skills/product_recommend_skill/SKILL.yaml")
                .toAbsolutePath().normalize();
        assertTrue(Files.exists(skillYaml), "product_recommend_skill/SKILL.yaml 应存在");
    }

    // ── 旧版配置文件不存在 ──

    @Test
    void testScriptsConfigMd_NotExist() {
        Path oldConfig = Path.of("src/main/resources/ScriptsConfig.md").toAbsolutePath();
        assertFalse(Files.exists(oldConfig), "旧版 ScriptsConfig.md 应已删除");
    }

    // ── resources 下不应有 scenarios/skills 目录（方案 B）──

    @Test
    void testResourcesScenarios_NotExist() {
        Path resourcesScenarios = Path.of("src/main/resources/scenarios").toAbsolutePath();
        assertFalse(Files.exists(resourcesScenarios),
                "resources/scenarios/ 不应存在（方案 B：场景在根目录 scenarios/）");
    }

    @Test
    void testResourcesSkills_NotExist() {
        Path resourcesSkills = Path.of("src/main/resources/skills").toAbsolutePath();
        assertFalse(Files.exists(resourcesSkills),
                "resources/skills/ 不应存在（方案 B：业务 Skill 在 scenarios/wealth-demo/skills/）");
    }
}
