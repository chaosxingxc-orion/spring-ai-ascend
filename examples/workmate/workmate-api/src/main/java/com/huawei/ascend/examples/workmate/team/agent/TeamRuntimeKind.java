package com.huawei.ascend.examples.workmate.team.agent;

public enum TeamRuntimeKind {
    WORKMATE_ORCHESTRATOR("workmate-orchestrator"),
    OPENJIUWEN_TEAM("openjiuwen-team");

    private final String yamlValue;

    TeamRuntimeKind(String yamlValue) {
        this.yamlValue = yamlValue;
    }

    public String yamlValue() {
        return yamlValue;
    }

    public static TeamRuntimeKind fromYaml(String value) {
        if (value == null || value.isBlank()) {
            return WORKMATE_ORCHESTRATOR;
        }
        String normalized = value.trim().toLowerCase();
        for (TeamRuntimeKind kind : values()) {
            if (kind.yamlValue.equals(normalized)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown team runtime: " + value);
    }
}
