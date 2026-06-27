package com.huawei.ascend.examples.workmate.market;

public class MarketplaceNotFoundException extends RuntimeException {

    public MarketplaceNotFoundException(String marketplaceId) {
        super("Marketplace not found: " + marketplaceId);
    }
}
