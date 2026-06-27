package com.huawei.ascend.examples.workmate.market;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.support.JsonFileStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PluginInstallStore {

    private static final Logger LOG = LoggerFactory.getLogger(PluginInstallStore.class);
    private static final String FILE_NAME = "plugins-installed.json";

    record InstalledPlugin(String marketplaceId, String pluginId, String version) {}

    private final JsonFileStore<Map<String, InstalledPlugin>> backing;

    public PluginInstallStore(WorkmateDataProperties dataProperties, ObjectMapper objectMapper) {
        this.backing = new JsonFileStore<>(
                dataProperties.resolvedPath().resolve(FILE_NAME),
                objectMapper,
                new TypeReference<Map<String, InstalledPlugin>>() {},
                HashMap::new,
                LOG);
    }

    Optional<InstalledPlugin> find(String marketplaceId, String pluginId) {
        return backing.read(map -> Optional.ofNullable(map.get(key(marketplaceId, pluginId))));
    }

    public boolean isInstalled(String marketplaceId, String pluginId) {
        return backing.read(map -> map.containsKey(key(marketplaceId, pluginId)));
    }

    public void install(String marketplaceId, String pluginId, String version) {
        backing.write(map -> map.put(key(marketplaceId, pluginId), new InstalledPlugin(marketplaceId, pluginId, version)));
    }

    public void uninstall(String marketplaceId, String pluginId) {
        backing.write(map -> map.remove(key(marketplaceId, pluginId)));
    }

    Map<String, InstalledPlugin> all() {
        return backing.read(map -> Map.copyOf(map));
    }

    public Set<String> installedPluginIds(String marketplaceId) {
        return backing.read(map -> map.values().stream()
                .filter(plugin -> marketplaceId.equals(plugin.marketplaceId()))
                .map(InstalledPlugin::pluginId)
                .collect(Collectors.toUnmodifiableSet()));
    }

    private static String key(String marketplaceId, String pluginId) {
        return marketplaceId + ":" + pluginId;
    }
}
