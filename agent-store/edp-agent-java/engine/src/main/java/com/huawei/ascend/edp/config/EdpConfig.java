package com.huawei.ascend.edp.config;

import java.util.List;

/**
 * edp-config.yaml 专有配置模型。
 *
 * <p>文件作用：</p>
 * <ul>
 *     <li>承载 EDPAgent 专有 YAML 配置的反序列化结果。</li>
 *     <li>向业务工具 Schema、业务 Rails、模型采样参数和话术模板提供配置。</li>
 *     <li>作为 Python EDPAgent 配置迁移到 Java spike 的主要配置承载对象。</li>
 * </ul>
 *
 * <p>对外提供的接口：</p>
 * <ul>
 *     <li>各字段 getter/setter：供 Jackson 反序列化和业务代码读取。</li>
 *     <li>{@link Scope}：业务范围配置。</li>
 *     <li>{@link TodolistStep}：轻量 Todo 步骤定义。</li>
 *     <li>{@link LlmSampling}：模型采样参数。</li>
 *     <li>{@link Memory}：记忆开关配置。</li>
 * </ul>
 */
public class EdpConfig {

    /** 业务范围配置。 */
    private Scope scope;

    /** 规划步骤文本列表。 */
    private List<String> planningSteps;

    /** 模型采样参数配置。 */
    private LlmSampling llmSampling;

    /** 场景发现配置。原 AgentRule.md 的 scenario_discovery 节。 */
    private ScenarioDiscoveryConfig scenarioDiscovery;

    public ScenarioDiscoveryConfig getScenarioDiscovery() { return scenarioDiscovery; }
    public void setScenarioDiscovery(ScenarioDiscoveryConfig scenarioDiscovery) { this.scenarioDiscovery = scenarioDiscovery; }

    public Scope getScope() { return scope; }
    public void setScope(Scope scope) { this.scope = scope; }

    public List<String> getPlanningSteps() { return planningSteps; }
    public void setPlanningSteps(List<String> planningSteps) { this.planningSteps = planningSteps; }

    public LlmSampling getLlmSampling() { return llmSampling; }
    public void setLlmSampling(LlmSampling llmSampling) { this.llmSampling = llmSampling; }

    /**
     * 业务范围配置。
     */
    public static class Scope {
        /** 允许处理的业务范围描述。 */
        private String allowed;

        public String getAllowed() { return allowed; }
        public void setAllowed(String allowed) { this.allowed = allowed; }
    }

    /**
     * 模型采样参数配置。
     */
    public static class LlmSampling {
        /** 温度参数。 */
        private double temperature;

        /** top_p 采样参数。 */
        private double topP;

        /** 最大重试次数。 */
        private int maxRetries;

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public double getTopP() { return topP; }
        public void setTopP(double topP) { this.topP = topP; }

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    }

}
