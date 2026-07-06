package com.huawei.ascend.edp.config;

/**
 * 场景发现配置。
 *
 * 对齐 Python 解耦版 agent_rule.py ScenarioDiscoveryConfig。
 * 合入 edp-config.yaml，不再单独放在 AgentRule.md。
 */
public class ScenarioDiscoveryConfig {
    /** 场景文件目录，相对场景根目录，例如 "scenarios"。 */
    private String basePath;

    /** 当前激活的场景名，例如 "wealth-demo"。 */
    private String activeScenario;

    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }

    public String getActiveScenario() { return activeScenario; }
    public void setActiveScenario(String activeScenario) { this.activeScenario = activeScenario; }
}
