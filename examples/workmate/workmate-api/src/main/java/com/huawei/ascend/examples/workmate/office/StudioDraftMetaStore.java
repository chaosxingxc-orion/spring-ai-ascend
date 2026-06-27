package com.huawei.ascend.examples.workmate.office;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class StudioDraftMetaStore {

    private final OfficeImportPaths importPaths;
    private final ObjectMapper objectMapper;

    public StudioDraftMetaStore(OfficeImportPaths importPaths, ObjectMapper objectMapper) {
        this.importPaths = importPaths;
        this.objectMapper = objectMapper;
    }

    public Optional<StudioDraftMeta> read(String assetType, String assetId) {
        Path file = metaFile(assetType, assetId);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(Files.readString(file), StudioDraftMeta.class));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read draft meta " + file, ex);
        }
    }

    public StudioDraftMeta markDraft(String assetType, String assetId, String origin) {
        StudioDraftMeta existing = read(assetType, assetId).orElse(null);
        String resolvedOrigin = origin != null && !origin.isBlank()
                ? origin
                : existing != null ? existing.origin() : "blank";
        StudioDraftMeta meta = new StudioDraftMeta(
                assetType,
                assetId,
                StudioDraftMeta.STATUS_DRAFT,
                resolvedOrigin,
                Instant.now().toString());
        write(meta);
        return meta;
    }

    public StudioDraftMeta markPublished(String assetType, String assetId) {
        StudioDraftMeta existing =
                read(assetType, assetId).orElse(new StudioDraftMeta(assetType, assetId, StudioDraftMeta.STATUS_DRAFT, "blank", null));
        StudioDraftMeta meta = new StudioDraftMeta(
                assetType,
                assetId,
                StudioDraftMeta.STATUS_PUBLISHED,
                existing.origin(),
                Instant.now().toString());
        write(meta);
        return meta;
    }

    public void delete(String assetType, String assetId) {
        try {
            Files.deleteIfExists(metaFile(assetType, assetId));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete draft meta", ex);
        }
    }

    private void write(StudioDraftMeta meta) {
        try {
            com.huawei.ascend.examples.workmate.support.JsonStores.writeAtomic(
                    objectMapper, metaFile(meta.assetType(), meta.assetId()), meta);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write draft meta", ex);
        }
    }

    private Path metaFile(String assetType, String assetId) {
        return importPaths.draftMetaFile(assetType + "__" + assetId);
    }
}
