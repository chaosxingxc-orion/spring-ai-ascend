package com.huawei.ascend.examples.workmate.market.dto;

import com.huawei.ascend.examples.workmate.market.MarketplaceDefinition;
import com.huawei.ascend.examples.workmate.market.PluginDefinition;
import java.util.List;

public record MarketplaceDetailResponse(
        String id,
        String name,
        String sourceType,
        String sourceUri,
        boolean builtin,
        List<PluginDefinition> plugins) {

    public static MarketplaceDetailResponse from(MarketplaceDefinition marketplace) {
        return new MarketplaceDetailResponse(
                marketplace.id(),
                marketplace.name(),
                marketplace.sourceType(),
                marketplace.sourceUri(),
                marketplace.builtin(),
                marketplace.plugins());
    }
}
