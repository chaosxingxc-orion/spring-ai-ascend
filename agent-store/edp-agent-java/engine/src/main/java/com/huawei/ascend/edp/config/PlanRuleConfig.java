package com.huawei.ascend.edp.config;

/**
 * planrule.yaml 配置模型。
 *
 * <p>定位：定义Agent的角色定位、职责边界和行为约束</p>
 * <p>作用：</p>
 * <ul>
 *     <li>回答Agent是谁（角色定义）</li>
 *     <li>回答Agent负责什么（职责范围）</li>
 *     <li>回答Agent边界是什么（允许/禁止的业务范围）</li>
 *     <li>定义Agent的行为约束规则</li>
 * </ul>
 */
public class PlanRuleConfig {

    /** Agent角色。 */
    private String role;

    /** Agent描述。 */
    private String description;

    /** 场景名称（仅场景级配置，框架默认无值）。仅在 scenario 模式下注入系统提示词。 */
    private String scenarioName;

    /** 场景描述（仅场景级配置，框架默认无值）。仅在 scenario 模式下注入系统提示词。 */
    private String scenarioDescription;

    /** Agent职责边界配置。 */
    private Scope scope;

    /** 补充提示词（行为约束规则）。 */
    private String supplementaryPrompt;

    /** Skill路由规则列表（继承式覆盖）。框架默认无值，仅场景级配置。 */
    private java.util.List<SkillRoute> skillRouting;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getScenarioName() { return scenarioName; }
    public void setScenarioName(String scenarioName) { this.scenarioName = scenarioName; }

    public String getScenarioDescription() { return scenarioDescription; }
    public void setScenarioDescription(String scenarioDescription) { this.scenarioDescription = scenarioDescription; }

    public Scope getScope() { return scope; }
    public void setScope(Scope scope) { this.scope = scope; }

    public String getSupplementaryPrompt() { return supplementaryPrompt; }
    public void setSupplementaryPrompt(String supplementaryPrompt) { this.supplementaryPrompt = supplementaryPrompt; }

    public java.util.List<SkillRoute> getSkillRouting() { return skillRouting; }
    public void setSkillRouting(java.util.List<SkillRoute> skillRouting) { this.skillRouting = skillRouting; }

    /**
     * Agent职责边界配置。
     */
    public static class Scope {
        /** 允许的业务范围列表（替代式覆盖）。 */
        private String allowed;

        /** 禁止的业务范围列表（替代式覆盖）。 */
        private String denied;

        public String getAllowed() { return allowed; }
        public void setAllowed(String allowed) { this.allowed = allowed; }

        public String getDenied() { return denied; }
        public void setDenied(String denied) { this.denied = denied; }
    }

    /**
     * Skill路由规则。
     *
     * <p>描述特定触发条件下应调用的目标Skill及其优先级。</p>
     */
    public static class SkillRoute {
        /** 触发条件描述，例如 "用户首次请求推荐理财产品"。 */
        private String trigger;

        /** 目标 Skill 名称，例如 "product_recommend_skill"。 */
        private String skill;

        /** 优先级，数字越小越优先。 */
        private int priority;

        public String getTrigger() { return trigger; }
        public void setTrigger(String trigger) { this.trigger = trigger; }

        public String getSkill() { return skill; }
        public void setSkill(String skill) { this.skill = skill; }

        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
    }
}