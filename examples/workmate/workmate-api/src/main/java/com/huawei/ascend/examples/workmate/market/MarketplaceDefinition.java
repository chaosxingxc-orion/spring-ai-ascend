package com.huawei.ascend.examples.workmate.market;

import java.util.List;

public record MarketplaceDefinition(
        String id,
        String name,
        String sourceType,
        String sourceUri,
        boolean builtin,
        List<PluginDefinition> plugins) {

    public MarketplaceDefinition {
        if (plugins == null) {
            plugins = List.of();
        }
    }
}
