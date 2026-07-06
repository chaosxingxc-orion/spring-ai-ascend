package com.huawei.ascend.edp.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Governance配置加载器。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>读取三个治理域配置文件（planrule.yaml、actrule.yaml、scriptconfig.yaml）。</li>
 *     <li>支持配置路径优先级：场景级governance > 框架级governance。</li>
 *     <li>实现继承覆盖机制（字段级别的继承覆盖）。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>{@link #load(Path)}：从指定governance目录加载配置。</li>
 *     <li>{@link #loadWithPriority(Path, Path)}：优先加载场景级配置，再合并框架级配置。</li>
 * </ul>
 */
public class GovernanceConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(GovernanceConfigLoader.class);

    /**
     * YAML 解析器。使用 snake_case 字段映射。
     */
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    /**
     * 从指定governance目录加载配置。
     *
     * @param governanceDir governance目录路径（包含三个yaml文件）
     * @return 解析后的 GovernanceConfig；文件不存在时返回默认对象
     */
    public static GovernanceConfig load(Path governanceDir) {
        if (governanceDir == null || !Files.exists(governanceDir)) {
            LOGGER.info("Governance directory not found at {}, using defaults", governanceDir);
            return new GovernanceConfig();
        }

        GovernanceConfig config = new GovernanceConfig();

        // 加载planrule.yaml
        Path planrulePath = governanceDir.resolve("planrule.yaml");
        if (Files.exists(planrulePath)) {
            try {
                JsonNode root = YAML_MAPPER.readTree(Files.readString(planrulePath));
                JsonNode planruleNode = root.get("planrule");
                if (planruleNode != null) {
                    PlanRuleConfig planrule = YAML_MAPPER.treeToValue(planruleNode, PlanRuleConfig.class);
                    config.setPlanrule(planrule);
                    LOGGER.info("Loaded planrule.yaml from {}", planrulePath);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load planrule.yaml from {}: {}", planrulePath, e.getMessage());
            }
        }

        // 加载actrule.yaml
        Path actrulePath = governanceDir.resolve("actrule.yaml");
        if (Files.exists(actrulePath)) {
            try {
                JsonNode root = YAML_MAPPER.readTree(Files.readString(actrulePath));
                JsonNode actruleNode = root.get("actrule");
                if (actruleNode != null) {
                    ActRuleConfig actrule = YAML_MAPPER.treeToValue(actruleNode, ActRuleConfig.class);
                    config.setActrule(actrule);
                    LOGGER.info("Loaded actrule.yaml from {}", actrulePath);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load actrule.yaml from {}: {}", actrulePath, e.getMessage());
            }
        }

        // 加载scriptconfig.yaml
        Path scriptconfigPath = governanceDir.resolve("scriptconfig.yaml");
        if (Files.exists(scriptconfigPath)) {
            try {
                JsonNode root = YAML_MAPPER.readTree(Files.readString(scriptconfigPath));
                JsonNode scriptconfigNode = root.get("scriptconfig");
                if (scriptconfigNode != null) {
                    ScriptConfig scriptconfig = YAML_MAPPER.treeToValue(scriptconfigNode, ScriptConfig.class);
                    config.setScriptconfig(scriptconfig);
                    LOGGER.info("Loaded scriptconfig.yaml from {}", scriptconfigPath);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load scriptconfig.yaml from {}: {}", scriptconfigPath, e.getMessage());
            }
        }

        return config;
    }

    /**
     * 优先加载场景级配置，再合并框架级配置（场景级优先）。
     *
     * <p>配置优先级：</p>
     * <ul>
     *     <li>场景级governance配置（scenarios/{场景名}/governance/）</li>
     *     <li>框架级governance配置（engine/src/main/resources/governance/）</li>
     * </ul>
     *
     * <p>继承覆盖规则：字段级别的继承覆盖，不是文件级别的完全覆盖。</p>
     *
     * @param scenarioDir 场景级governance目录
     * @param frameworkDir 框架级governance目录
     * @return 合并后的 GovernanceConfig
     */
    public static GovernanceConfig loadWithPriority(Path scenarioDir, Path frameworkDir) {
        // 1. 先加载框架级配置（Default）
        GovernanceConfig defaultConfig = load(frameworkDir);

        // 2. 再加载场景级配置（Scenario）
        GovernanceConfig scenarioConfig = load(scenarioDir);

        // 3. 合并配置：场景级覆盖框架级
        defaultConfig.mergeScenarioConfig(scenarioConfig);

        LOGGER.info("Governance config loaded with priority: scenario={}, framework={}", scenarioDir, frameworkDir);

        return defaultConfig;
    }

    /**
     * 从classpath加载框架级默认配置。
     *
     * <p>框架级配置路径：engine/src/main/resources/governance/</p>
     *
     * @return 框架级默认 GovernanceConfig
     */
    public static GovernanceConfig loadDefaultFromClasspath() {
        try {
            // 从classpath读取governance目录
            Path defaultGovernancePath = Path.of("src/main/resources/governance").toAbsolutePath();
            return load(defaultGovernancePath);
        } catch (Exception e) {
            LOGGER.warn("Failed to load default governance config from classpath: {}", e.getMessage());
            return new GovernanceConfig();
        }
    }

    /**
     * 根据场景名称加载场景级配置并合并框架级配置。
     *
     * @param scenarioName 场景名称（例如："wealth-demo"、"hz-zhidaitong"）
     * @param baseDir scenarios目录路径
     * @return 合并后的 GovernanceConfig
     */
    public static GovernanceConfig loadForScenario(String scenarioName, Path baseDir) {
        if (scenarioName == null || scenarioName.isBlank()) {
            LOGGER.info("No scenario specified, using default governance config");
            return loadDefaultFromClasspath();
        }

        // 场景级governance路径：scenarios/{场景名}/governance/
        Path scenarioGovernancePath = baseDir.resolve(scenarioName).resolve("governance");

        // 框架级governance路径：engine/src/main/resources/governance/
        Path frameworkGovernancePath = Path.of("src/main/resources/governance").toAbsolutePath();

        // 优先级合并
        return loadWithPriority(scenarioGovernancePath, frameworkGovernancePath);
    }
}