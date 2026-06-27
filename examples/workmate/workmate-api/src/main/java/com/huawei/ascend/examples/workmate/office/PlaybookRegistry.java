package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class PlaybookRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(PlaybookRegistry.class);

    private final Path officeRoot;
    private final OfficeImportPaths importPaths;
    private volatile Map<String, PlaybookRegistryEntry> playbooks = Map.of();

    public PlaybookRegistry(WorkmateOfficeProperties officeProperties, OfficeImportPaths importPaths) {
        this.officeRoot = officeProperties.resolvedRoot();
        this.importPaths = importPaths;
        reloadAll();
    }

    public int reloadAll() {
        Map<String, PlaybookRegistryEntry> next = new LinkedHashMap<>();
        loadPlaybooksDir(officeRoot.resolve("playbooks"), OfficeAssetSource.BUILTIN, next);
        if (importPaths != null) {
            loadPlaybooksDir(importPaths.playbooksDraftsDir(), OfficeAssetSource.DRAFT, next);
        }
        playbooks = Map.copyOf(next);
        LOG.info("Loaded {} office playbook(s) from {} (+ drafts)", next.size(), officeRoot);
        return next.size();
    }

    public List<PlaybookDefinition> listPlaybooks() {
        return playbooks.values().stream().map(PlaybookRegistryEntry::definition).toList();
    }

    public List<PlaybookRegistryEntry> listEntries() {
        return List.copyOf(playbooks.values());
    }

    public List<PlaybookDefinition> listByPlacement(String placement) {
        if (placement == null || placement.isBlank()) {
            return listPlaybooks();
        }
        return playbooks.values().stream()
                .map(PlaybookRegistryEntry::definition)
                .filter(playbook -> playbook.placements().contains(placement))
                .toList();
    }

    public Optional<PlaybookDefinition> findPlaybook(String id) {
        return findEntry(id).map(PlaybookRegistryEntry::definition);
    }

    public Optional<PlaybookRegistryEntry> findEntry(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(playbooks.get(id));
    }

    public Optional<PlaybookRegistryEntry> findBaselineEntry(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        Path file = officeRoot.resolve("playbooks").resolve(id + ".yaml");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(loadPlaybookFile(file, OfficeAssetSource.BUILTIN));
        } catch (IOException | IllegalArgumentException ex) {
            LOG.warn("Skip invalid baseline playbook {}: {}", id, ex.getMessage());
            return Optional.empty();
        }
    }

    public PlaybookDefinition parsePlaybookYaml(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            throw new IllegalArgumentException("playbook yaml content required");
        }
        Yaml yaml = SafeYaml.loader();
        Object loaded = yaml.load(yamlContent);
        if (!(loaded instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("playbook yaml must be a YAML map");
        }
        return parseFromMap(raw);
    }

    private void loadPlaybooksDir(Path playbooksDir, OfficeAssetSource source, Map<String, PlaybookRegistryEntry> target) {
        if (!Files.isDirectory(playbooksDir)) {
            if (source == OfficeAssetSource.BUILTIN) {
                LOG.warn("Office playbooks directory not found: {}", playbooksDir);
            }
            return;
        }
        try (var stream = Files.list(playbooksDir)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".yaml"))
                    .forEach(path -> loadPlaybookFile(path, source, target));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan playbooks under " + playbooksDir, ex);
        }
    }

    private void loadPlaybookFile(Path yamlFile, OfficeAssetSource source, Map<String, PlaybookRegistryEntry> target) {
        try {
            PlaybookRegistryEntry entry = loadPlaybookFile(yamlFile, source);
            target.put(entry.definition().id(), entry);
        } catch (IllegalArgumentException ex) {
            LOG.warn("Skip invalid playbook yaml {}: {}", yamlFile, ex.getMessage());
        } catch (IOException ex) {
            LOG.warn("Failed to load playbook from {}: {}", yamlFile, ex.getMessage());
        }
    }

    private PlaybookRegistryEntry loadPlaybookFile(Path yamlFile, OfficeAssetSource source) throws IOException {
        try (InputStream input = Files.newInputStream(yamlFile)) {
            Yaml yaml = SafeYaml.loader();
            Object loaded = yaml.load(input);
            if (!(loaded instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("not a map: " + yamlFile);
            }
            PlaybookDefinition definition = parseFromMap(raw);
            return new PlaybookRegistryEntry(definition, source, yamlFile);
        }
    }

    private static PlaybookDefinition parseFromMap(Map<?, ?> raw) {
        String id = OfficeYaml.stringField(raw, "id");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("missing id");
        }
        String title = OfficeYaml.stringField(raw, "title");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("missing title for " + id);
        }
        String initPrompt = OfficeYaml.stringField(raw, "initPrompt");
        if (initPrompt == null || initPrompt.isBlank()) {
            throw new IllegalArgumentException("missing initPrompt for " + id);
        }
        return new PlaybookDefinition(
                id,
                title,
                OfficeYaml.stringField(raw, "description"),
                OfficeYaml.stringField(raw, "accent"),
                OfficeYaml.stringField(raw, "expertId"),
                initPrompt.trim(),
                OfficeYaml.stringList(raw, "placements"));
    }
}
