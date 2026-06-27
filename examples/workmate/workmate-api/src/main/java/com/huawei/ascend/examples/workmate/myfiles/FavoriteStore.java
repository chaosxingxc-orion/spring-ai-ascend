package com.huawei.ascend.examples.workmate.myfiles;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.support.JsonFileStore;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** W35 — FS-backed favorite markers for myFiles (`sessionId::path`). */
@Component
public class FavoriteStore {

    private static final Logger LOG = LoggerFactory.getLogger(FavoriteStore.class);
    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() {};

    private final JsonFileStore<Set<String>> backing;

    public FavoriteStore(WorkmateDataProperties dataProperties, ObjectMapper objectMapper) {
        this.backing = new JsonFileStore<>(
                dataProperties.resolvedPath().resolve("myfiles/favorites.json"),
                objectMapper,
                STRING_SET,
                LinkedHashSet::new,
                LOG);
    }

    public static String favoriteKey(UUID sessionId, String path) {
        return sessionId + "::" + normalizePath(path);
    }

    public boolean isFavorite(UUID sessionId, String path) {
        return backing.read(favorites -> favorites.contains(favoriteKey(sessionId, path)));
    }

    public void setFavorite(UUID sessionId, String path, boolean favorite) {
        backing.write(favorites -> {
            String key = favoriteKey(sessionId, path);
            if (favorite) {
                favorites.add(key);
            } else {
                favorites.remove(key);
            }
        });
    }

    public void relocateFavorite(UUID sessionId, String oldPath, String newPath) {
        backing.write(favorites -> {
            String oldKey = favoriteKey(sessionId, oldPath);
            if (favorites.remove(oldKey)) {
                favorites.add(favoriteKey(sessionId, newPath));
            }
        });
    }

    public void removeFavorite(UUID sessionId, String path) {
        setFavorite(sessionId, path, false);
    }

    public Set<String> load() {
        return backing.read(favorites -> Set.copyOf(favorites));
    }

    private static String normalizePath(String path) {
        return path.replace('\\', '/').trim();
    }
}
