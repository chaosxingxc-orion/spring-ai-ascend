package com.huawei.ascend.examples.workmate.model;

import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

public enum ModelEffort {
    AUTO,
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH,
    MAX;

    public static ModelEffort parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        try {
            return ModelEffort.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return AUTO;
        }
    }

    public void applyTo(ModelRequestConfig config) {
        switch (this) {
            case MINIMAL -> {
                config.setTemperature(0.0);
                config.setMaxTokens(1024);
            }
            case LOW -> {
                config.setTemperature(0.1);
                config.setMaxTokens(2048);
            }
            case HIGH -> {
                config.setTemperature(0.3);
                config.setMaxTokens(8192);
            }
            case MAX -> {
                config.setTemperature(0.4);
                config.setMaxTokens(16384);
            }
            case MEDIUM, AUTO -> {
                config.setTemperature(0.2);
                config.setMaxTokens(4096);
            }
        }
    }

    public String label() {
        return switch (this) {
            case AUTO -> "自动";
            case MINIMAL -> "极简";
            case LOW -> "低";
            case MEDIUM -> "中";
            case HIGH -> "高";
            case MAX -> "最大";
        };
    }
}
