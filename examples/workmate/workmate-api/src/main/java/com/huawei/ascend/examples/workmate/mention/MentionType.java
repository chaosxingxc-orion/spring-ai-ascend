package com.huawei.ascend.examples.workmate.mention;

public enum MentionType {
    FILE,
    SKILL,
    MEMBER,
    CONNECTOR;

    public static MentionType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("mention type is required");
        }
        return MentionType.valueOf(value.trim().toUpperCase());
    }
}
