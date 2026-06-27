package com.huawei.ascend.examples.workmate.share;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class TempDownloadStore {

    private static final Logger LOG = LoggerFactory.getLogger(TempDownloadStore.class);
    private static final String FILE_NAME = "temp-downloads.json";

    private final Path storeFile;
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<TempLink> links = new ArrayList<>();

    TempDownloadStore(WorkmateDataProperties dataProperties, ObjectMapper objectMapper) {
        this.storeFile = dataProperties.resolvedPath().resolve(FILE_NAME);
        this.objectMapper = objectMapper;
        load();
    }

    TempLink create(UUID sessionId, String path, Instant expiresAt) {
        lock.writeLock().lock();
        try {
            purgeExpiredUnlocked();
            TempLink link = new TempLink(
                    UUID.randomUUID().toString().replace("-", ""),
                    sessionId,
                    path,
                    Instant.now(),
                    expiresAt);
            links.add(link);
            persistUnlocked();
            return link;
        } finally {
            lock.writeLock().unlock();
        }
    }

    Optional<TempLink> findValid(String token) {
        lock.writeLock().lock();
        try {
            purgeExpiredUnlocked();
            if (token == null || token.isBlank()) {
                return Optional.empty();
            }
            return links.stream()
                    .filter(link -> link.token().equals(token))
                    .filter(link -> link.expiresAt() == null || !Instant.now().isAfter(link.expiresAt()))
                    .findFirst();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void purgeExpiredUnlocked() {
        Instant now = Instant.now();
        if (links.removeIf(link -> link.expiresAt() != null && now.isAfter(link.expiresAt()))) {
            persistUnlocked();
        }
    }

    private void load() {
        if (!Files.isRegularFile(storeFile)) {
            return;
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(storeFile.toFile(), new TypeReference<>() {});
            Object linksRaw = raw.get("links");
            if (!(linksRaw instanceof List<?> list)) {
                return;
            }
            lock.writeLock().lock();
            try {
                links.clear();
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> map)) {
                        continue;
                    }
                    Instant expiresAt = null;
                    Object expiresRaw = map.get("expiresAt");
                    if (expiresRaw != null && !String.valueOf(expiresRaw).isBlank()) {
                        expiresAt = Instant.parse(String.valueOf(expiresRaw));
                    }
                    links.add(new TempLink(
                            String.valueOf(map.get("token")),
                            UUID.fromString(String.valueOf(map.get("sessionId"))),
                            String.valueOf(map.get("path")),
                            Instant.parse(String.valueOf(map.get("createdAt"))),
                            expiresAt));
                }
            } finally {
                lock.writeLock().unlock();
            }
        } catch (IOException | RuntimeException ex) {
            LOG.warn("Failed to read temp download store {}: {}", storeFile, ex.getMessage());
        }
    }

    private void persistUnlocked() {
        try {
            Files.createDirectories(storeFile.getParent());
            List<Map<String, Object>> payload = new ArrayList<>();
            for (TempLink link : links) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("token", link.token());
                row.put("sessionId", link.sessionId().toString());
                row.put("path", link.path());
                row.put("createdAt", link.createdAt().toString());
                if (link.expiresAt() != null) {
                    row.put("expiresAt", link.expiresAt().toString());
                }
                payload.add(row);
            }
            JsonStores.writeAtomic(objectMapper, storeFile, Map.of("links", payload));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write temp download store " + storeFile, ex);
        }
    }

    record TempLink(String token, UUID sessionId, String path, Instant createdAt, Instant expiresAt) {}
}
