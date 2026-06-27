package com.huawei.ascend.examples.workmate.discover;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.support.JsonStores;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class DiscoverStore {

    private static final Logger LOG = LoggerFactory.getLogger(DiscoverStore.class);
    private static final String FILE_NAME = "discover-state.json";
    private static final int MAX_USED = 30;

    private final Path storeFile;
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Set<String> favorites = new LinkedHashSet<>();
    private List<UsedEntry> used = new ArrayList<>();

    DiscoverStore(WorkmateDataProperties dataProperties, ObjectMapper objectMapper) {
        this.storeFile = dataProperties.resolvedPath().resolve(FILE_NAME);
        this.objectMapper = objectMapper;
        load();
    }

    Set<String> favorites() {
        lock.readLock().lock();
        try {
            return Set.copyOf(favorites);
        } finally {
            lock.readLock().unlock();
        }
    }

    List<UsedEntry> used() {
        lock.readLock().lock();
        try {
            return List.copyOf(used);
        } finally {
            lock.readLock().unlock();
        }
    }

    void toggleFavorite(String key, boolean favorite) {
        lock.writeLock().lock();
        try {
            if (favorite) {
                favorites.add(key);
            } else {
                favorites.remove(key);
            }
            persistUnlocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    void recordUsed(String key, String title, String subtitle) {
        lock.writeLock().lock();
        try {
            used.removeIf(entry -> entry.key().equals(key));
            used.add(0, new UsedEntry(key, title, subtitle, Instant.now()));
            if (used.size() > MAX_USED) {
                used = new ArrayList<>(used.subList(0, MAX_USED));
            }
            persistUnlocked();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void load() {
        if (!Files.isRegularFile(storeFile)) {
            return;
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(storeFile.toFile(), new TypeReference<>() {});
            lock.writeLock().lock();
            try {
                Object fav = raw.get("favorites");
                if (fav instanceof List<?> list) {
                    favorites = new LinkedHashSet<>();
                    for (Object item : list) {
                        if (item != null) {
                            favorites.add(String.valueOf(item));
                        }
                    }
                }
                Object usedRaw = raw.get("used");
                if (usedRaw instanceof List<?> list) {
                    used = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            used.add(new UsedEntry(
                                    String.valueOf(map.get("key")),
                                    String.valueOf(map.get("title")),
                                    map.get("subtitle") == null ? null : String.valueOf(map.get("subtitle")),
                                    map.get("lastUsedAt") == null
                                            ? Instant.EPOCH
                                            : Instant.parse(String.valueOf(map.get("lastUsedAt")))));
                        }
                    }
                }
            } finally {
                lock.writeLock().unlock();
            }
        } catch (IOException | RuntimeException ex) {
            LOG.warn("Failed to read discover store {}: {}", storeFile, ex.getMessage());
        }
    }

    private void persistUnlocked() {
        try {
            Files.createDirectories(storeFile.getParent());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("favorites", favorites);
            List<Map<String, Object>> usedPayload = new ArrayList<>();
            for (UsedEntry entry : used) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", entry.key());
                row.put("title", entry.title());
                row.put("subtitle", entry.subtitle());
                row.put("lastUsedAt", entry.lastUsedAt().toString());
                usedPayload.add(row);
            }
            payload.put("used", usedPayload);
            JsonStores.writeAtomic(objectMapper, storeFile, payload);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write discover store " + storeFile, ex);
        }
    }

    record UsedEntry(String key, String title, String subtitle, Instant lastUsedAt) {}
}
