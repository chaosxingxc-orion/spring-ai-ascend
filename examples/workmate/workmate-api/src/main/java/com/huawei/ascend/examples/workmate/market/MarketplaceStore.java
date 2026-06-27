package com.huawei.ascend.examples.workmate.market;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.support.JsonFileStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MarketplaceStore {

    private static final Logger LOG = LoggerFactory.getLogger(MarketplaceStore.class);
    private static final String FILE_NAME = "known-marketplaces.json";

    private final JsonFileStore<Map<String, MarketplaceDefinition>> backing;

    public MarketplaceStore(WorkmateDataProperties dataProperties, ObjectMapper objectMapper) {
        this.backing = new JsonFileStore<>(
                dataProperties.resolvedPath().resolve(FILE_NAME),
                objectMapper,
                new TypeReference<Map<String, MarketplaceDefinition>>() {},
                LinkedHashMap::new,
                LOG);
    }

    List<MarketplaceDefinition> list() {
        return backing.read(map -> List.copyOf(map.values()));
    }

    Optional<MarketplaceDefinition> find(String marketplaceId) {
        return backing.read(map -> Optional.ofNullable(map.get(marketplaceId)));
    }

    void save(MarketplaceDefinition marketplace) {
        backing.write(map -> map.put(marketplace.id(), marketplace));
    }

    void delete(String marketplaceId) {
        backing.write(map -> {
            MarketplaceDefinition removed = map.remove(marketplaceId);
            if (removed != null && removed.builtin()) {
                map.put(marketplaceId, removed);
                throw new IllegalArgumentException("Cannot delete builtin marketplace: " + marketplaceId);
            }
        });
    }

    void replaceAll(Map<String, MarketplaceDefinition> next) {
        backing.replace(new LinkedHashMap<>(next));
    }
}
