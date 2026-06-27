package com.huawei.ascend.examples.workmate.office;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads office assets from layered directories (builtin → market → import → draft).
 * Each asset type supplies a subdir loader; this class handles directory scanning and layer ordering.
 */
public final class LayeredOfficeAssetLoader {

    private static final Logger LOG = LoggerFactory.getLogger(LayeredOfficeAssetLoader.class);

    private LayeredOfficeAssetLoader() {
    }

    public record Layer(Path directory, OfficeAssetSource source, boolean skipIfExists) {}

    public record LoadResult<T>(Map<String, T> entries, List<String> warnings) {}

    @FunctionalInterface
    public interface SubdirLoader<T> {
        void load(
                Path assetDir,
                OfficeAssetSource source,
                Map<String, T> target,
                List<String> warnings,
                boolean skipIfExists);
    }

    public static <T> LoadResult<T> loadSubdirs(List<Layer> layers, SubdirLoader<T> loader) {
        Map<String, T> target = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (Layer layer : layers) {
            scanSubdirs(layer.directory(), dir -> loader.load(dir, layer.source(), target, warnings, layer.skipIfExists()));
        }
        return new LoadResult<>(Map.copyOf(target), List.copyOf(warnings));
    }

    public static void scanSubdirs(Path rootDir, Consumer<Path> consumer) {
        if (!Files.isDirectory(rootDir)) {
            return;
        }
        try (var stream = Files.list(rootDir)) {
            stream.filter(Files::isDirectory).forEach(consumer);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan assets under " + rootDir, ex);
        }
    }

    public static Optional<Map<?, ?>> readYamlMap(Path yamlFile) {
        if (!Files.isRegularFile(yamlFile)) {
            return Optional.empty();
        }
        try (InputStream input = Files.newInputStream(yamlFile)) {
            Yaml yaml = SafeYaml.loader();
            Object loaded = yaml.load(input);
            if (loaded instanceof Map<?, ?> raw) {
                return Optional.of(raw);
            }
            LOG.warn("Skip invalid yaml (not a map): {}", yamlFile);
            return Optional.empty();
        } catch (IOException ex) {
            LOG.warn("Failed to read yaml {}: {}", yamlFile, ex.getMessage());
            return Optional.empty();
        }
    }

    public static boolean shouldSkipExisting(
            String id, OfficeAssetSource source, Map<?, ?> target, boolean skipIfExists) {
        if (skipIfExists && target.containsKey(id)) {
            LOG.warn("Skip {} asset {} — id already registered", source, id);
            return true;
        }
        return false;
    }
}
