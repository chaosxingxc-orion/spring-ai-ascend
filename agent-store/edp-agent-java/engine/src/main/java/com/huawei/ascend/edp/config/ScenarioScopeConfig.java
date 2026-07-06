package com.huawei.ascend.edp.config;

import java.util.List;

/**
 * 场景业务范围配置。
 *
 * 对齐 Python 解耦版 agent_rule.py ScenarioScopeConfig。
 */
public class ScenarioScopeConfig {
    /** 允许的业务范围列表。 */
    private List<String> allowed;

    /** 禁止的业务范围列表。 */
    private List<String> denied;

    public List<String> getAllowed() { return allowed; }
    public void setAllowed(List<String> allowed) { this.allowed = allowed; }

    public List<String> getDenied() { return denied; }
    public void setDenied(List<String> denied) { this.denied = denied; }
}
