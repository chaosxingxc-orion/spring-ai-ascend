package com.huawei.ascend.examples.workmate.market;

public record PluginDefinition(
        String id,
        String name,
        String description,
        String version,
        String category,
        boolean policyLocked,
        boolean updateAvailable) {}
