package com.huawei.ascend.examples.workmate.market.dto;

import com.huawei.ascend.examples.workmate.market.MarketplaceDefinition;
import com.huawei.ascend.examples.workmate.market.PluginDefinition;
import java.util.List;

public record MarketplaceResponse(
        String id,
        String name,
        String sourceType,
        String sourceUri,
        boolean builtin,
        int pluginCount) {

    public static MarketplaceResponse from(MarketplaceDefinition marketplace) {
        return new MarketplaceResponse(
                marketplace.id(),
                marketplace.name(),
                marketplace.sourceType(),
                marketplace.sourceUri(),
                marketplace.builtin(),
                marketplace.plugins().size());
    }
}
