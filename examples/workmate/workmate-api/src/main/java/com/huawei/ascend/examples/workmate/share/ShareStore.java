package com.huawei.ascend.examples.workmate.share;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.support.JsonStores;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class ShareStore {

    private static final Logger LOG = LoggerFactory.getLogger(ShareStore.class);
    private static final String FILE_NAME = "share-links.json";

    private final Path storeFile;
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<ShareLink> links = new ArrayList<>();

    ShareStore(WorkmateDataProperties dataProperties, ObjectMapper objectMapper) {
        this.storeFile = dataProperties.resolvedPath().resolve(FILE_NAME);
        this.objectMapper = objectMapper;
        load();
    }

    ShareLink create(UUID sessionId, String title, String scope, int expiresInHours) {
        Instant expiresAt = expiresInHours > 0
                ? Instant.now().plus(expiresInHours, ChronoUnit.HOURS)
                : null;
        ShareLink link = new ShareLink(
                UUID.randomUUID().toString().replace("-", ""),
                sessionId,
                title,
                Instant.now(),
                scope == null || scope.isBlank() ? "full" : scope,
                expiresAt);
        lock.writeLock().lock();
        try {
            links.add(link);
            persistUnlocked();
            return link;
        } finally {
            lock.writeLock().unlock();
        }
    }

    ShareLink create(UUID sessionId, String title) {
        return create(sessionId, title, "full", 168);
    }

    Optional<ShareLink> findByToken(String token) {
        lock.readLock().lock();
        try {
            if (token == null || token.isBlank()) {
                return Optional.empty();
            }
            return links.stream()
                    .filter(link -> link.token().equals(token))
                    .filter(link -> !isExpired(link))
                    .findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    static boolean isExpired(ShareLink link) {
        return link.expiresAt() != null && Instant.now().isAfter(link.expiresAt());
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
                    String scope = map.containsKey("scope")
                            ? String.valueOf(map.get("scope"))
                            : "full";
                    links.add(new ShareLink(
                            String.valueOf(map.get("token")),
                            UUID.fromString(String.valueOf(map.get("sessionId"))),
                            String.valueOf(map.get("title")),
                            Instant.parse(String.valueOf(map.get("createdAt"))),
                            scope,
                            expiresAt));
                }
            } finally {
                lock.writeLock().unlock();
            }
        } catch (IOException | RuntimeException ex) {
            LOG.warn("Failed to read share store {}: {}", storeFile, ex.getMessage());
        }
    }

    private void persistUnlocked() {
        try {
            Files.createDirectories(storeFile.getParent());
            List<Map<String, Object>> payload = new ArrayList<>();
            for (ShareLink link : links) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("token", link.token());
                row.put("sessionId", link.sessionId().toString());
                row.put("title", link.title());
                row.put("createdAt", link.createdAt().toString());
                row.put("scope", link.scope());
                if (link.expiresAt() != null) {
                    row.put("expiresAt", link.expiresAt().toString());
                }
                payload.add(row);
            }
            JsonStores.writeAtomic(objectMapper, storeFile, Map.of("links", payload));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write share store " + storeFile, ex);
        }
    }

    record ShareLink(
            String token,
            UUID sessionId,
            String title,
            Instant createdAt,
            String scope,
            Instant expiresAt) {}
}
