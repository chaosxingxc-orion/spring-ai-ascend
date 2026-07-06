package com.huawei.ascend.edp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 启动时配置校验器。
 *
 * fail-fast 校验：缺配置 → 启动失败并报明确错误。
 *
 * V2 方案 B：场景校验从 scenarioHome 出发，
 * 不再依赖 yamlDir.resolve(basePath) 解析。
 */
public class EdpConfigValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdpConfigValidator.class);

    /**
     * 校验模型配置完整性。
     */
    public static void validateModelConfig(EdpaSpringBootConfig.ModelConfig model) {
        if (model == null) {
            throw new IllegalStateException("Model config missing. Set edpa.agent.model in application.yml.");
        }
        if (model.getProvider() == null || model.getProvider().isBlank()) {
            throw new IllegalStateException("Model provider missing. Set edpa.agent.model.provider.");
        }
        if (model.getName() == null || model.getName().isBlank()) {
            throw new IllegalStateException("Model name missing. Set edpa.agent.model.name.");
        }
        if (model.getBaseUrl() == null || model.getBaseUrl().isBlank()) {
            throw new IllegalStateException("Model baseUrl missing. Set edpa.agent.model.base-url.");
        }

        String apiKey = model.getApiKey();
        String envApiKey = System.getenv("EDP_AGENT_MODEL_API_KEY");
        boolean hasValidApiKey = (apiKey != null && !apiKey.isBlank())
                || (envApiKey != null && !envApiKey.isBlank());
        if (!hasValidApiKey) {
            throw new IllegalStateException("Model apiKey missing. Set EDP_AGENT_MODEL_API_KEY environment variable.");
        }

        LOGGER.info("Model config validated: provider={}, name={}, apiKeySource={}",
                model.getProvider(), model.getName(),
                (envApiKey != null && !envApiKey.isBlank()) ? "ENV_VAR" : "application.yml");
    }

    /**
     * 校验 Versatile URL 合法性。
     */
    public static void validateVersatileUrl(EdpaSpringBootConfig.VersatileConfig versatile) {
        if (versatile != null && versatile.getUrl() != null) {
            String url = versatile.getUrl();
            if (url.startsWith("${")) {
                String envUrl = System.getenv("EDP_AGENT_VERSATILE_URL");
                if (envUrl != null && !envUrl.isBlank()) {
                    LOGGER.info("Versatile URL from env var validated: {}", envUrl);
                } else {
                    throw new IllegalStateException("Versatile URL is a Spring placeholder but env var EDP_AGENT_VERSATILE_URL not set.");
                }
            } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new IllegalStateException("Versatile URL invalid: " + url + ". Must start with http:// or https://.");
            } else {
                LOGGER.info("Versatile URL validated: {}", url);
            }
        }
    }

    /**
     * 校验 Skill 目录存在。
     */
    public static void validateSkillDir(Path skillDir) {
        if (skillDir != null && !Files.exists(skillDir)) {
            throw new IllegalStateException("Skill directory not found: " + skillDir);
        }
    }

    /**
     * 校验场景目录。scenario-config.yaml 已删除，验证 governance/ 目录结构。
     */
    public static void validateScenarioConfig(Path scenarioHome) {
        if (scenarioHome == null) {
            LOGGER.info("No scenarioHome configured, skipping scenario validation.");
            return;
        }
        if (!Files.exists(scenarioHome)) {
            throw new IllegalStateException("scenarioHome directory not found: " + scenarioHome);
        }
        Path governanceDir = scenarioHome.resolve("governance");
        if (!Files.exists(governanceDir)) {
            throw new IllegalStateException(
                    "Scenario governance directory not found: " + governanceDir
                            + ". Expected governance/{planrule,actrule,scriptconfig}.yaml");
        }
        LOGGER.info("Scenario governance validated: {}", governanceDir);
    }

    /**
     * 校验 skill_routing 中声明的 Skill 在 skills 目录中存在。
     * 数据源已从 ScenarioConfig 迁移至 PlanRuleConfig。
     */
    public static void validateSkillRouting(PlanRuleConfig planrule, Path skillsDir) {
        if (planrule == null || planrule.getSkillRouting() == null) return;
        for (PlanRuleConfig.SkillRoute routing : planrule.getSkillRouting()) {
            Path skillDir = skillsDir.resolve(routing.getSkill());
            if (!Files.exists(skillDir)) {
                throw new IllegalStateException("Skill routing references non-existent skill: " + routing.getSkill());
            }
        }
    }
}
