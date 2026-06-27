package com.huawei.ascend.examples.workmate.office;

public enum TeamMemberBackend {
    LOCAL("local"),
    EXPERT_REF("expert_ref"),
    A2A("a2a"),
    EXTERNAL_CLI("external_cli"),
    EXTERNAL_JOIN("external_join");

    private final String yamlValue;

    TeamMemberBackend(String yamlValue) {
        this.yamlValue = yamlValue;
    }

    public String yamlValue() {
        return yamlValue;
    }

    public static TeamMemberBackend fromYaml(String value) {
        if (value == null || value.isBlank()) {
            return LOCAL;
        }
        String normalized = value.trim().toLowerCase();
        for (TeamMemberBackend backend : values()) {
            if (backend.yamlValue.equals(normalized)) {
                return backend;
            }
        }
        throw new IllegalArgumentException("Unknown team member backend: " + value);
    }
}
