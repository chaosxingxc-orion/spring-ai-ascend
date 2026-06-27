package com.huawei.ascend.examples.workmate.market.dto;

import com.huawei.ascend.examples.workmate.market.PluginDefinition;

public record PluginResponse(
        String marketplaceId,
        String marketplaceName,
        String id,
        String name,
        String description,
        String version,
        String category,
        boolean installed,
        boolean policyLocked,
        boolean updateAvailable) {

    public static PluginResponse from(
            String marketplaceId,
            String marketplaceName,
            PluginDefinition plugin,
            boolean installed) {
        return new PluginResponse(
                marketplaceId,
                marketplaceName,
                plugin.id(),
                plugin.name(),
                plugin.description(),
                plugin.version(),
                plugin.category(),
                installed,
                plugin.policyLocked(),
                plugin.updateAvailable());
    }
}
