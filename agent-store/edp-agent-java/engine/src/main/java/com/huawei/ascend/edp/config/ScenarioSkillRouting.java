package com.huawei.ascend.edp.config;

import java.util.List;

/**
 * Skill 路由规则。
 *
 * 对齐 Python 解耦版 agent_rule.py ScenarioSkillRouting。
 */
public class ScenarioSkillRouting {
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
