package com.huawei.ascend.examples.workmate.market;

public class PluginNotFoundException extends RuntimeException {

    public PluginNotFoundException(String marketplaceId, String pluginId) {
        super("Plugin not found: " + marketplaceId + "/" + pluginId);
    }
}
