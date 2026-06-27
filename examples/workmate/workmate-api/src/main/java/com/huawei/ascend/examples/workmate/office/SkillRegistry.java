package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SkillRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(SkillRegistry.class);

    private final Path officeRoot;
    private final OfficeImportPaths importPaths;
    private volatile Map<String, SkillRegistryEntry> skills = Map.of();
    private volatile List<String> reloadWarnings = List.of();

    public SkillRegistry(WorkmateOfficeProperties officeProperties, OfficeImportPaths importPaths) {
        this.officeRoot = officeProperties.resolvedRoot();
        this.importPaths = importPaths;
        reloadAll();
    }

    public int reloadAll() {
        LayeredOfficeAssetLoader.LoadResult<SkillRegistryEntry> loaded =
                LayeredOfficeAssetLoader.loadSubdirs(skillLayers(true), this::loadSkillDir);
        skills = loaded.entries();
        reloadWarnings = loaded.warnings();
        LOG.info("Loaded {} office skill(s) from {} (+ imports/drafts)", skills.size(), officeRoot);
        return skills.size();
    }

    /** @deprecated Prefer {@link #reloadAll()}; kept for import/upload call sites. */
    public void reloadImports(Path importsDir) {
        reloadAll();
    }

    void reload(Path officeRoot) {
        reloadAll();
    }

    public List<String> reloadWarnings() {
        return reloadWarnings;
    }

    public Optional<SkillRegistryEntry> findEntry(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(skills.get(skillId));
    }

    /** Highest-priority entry excluding {@link OfficeAssetSource#DRAFT} overlays. */
    public Optional<SkillRegistryEntry> findBaselineEntry(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return Optional.empty();
        }
        LayeredOfficeAssetLoader.LoadResult<SkillRegistryEntry> loaded =
                LayeredOfficeAssetLoader.loadSubdirs(skillLayers(false), this::loadSkillDir);
        return Optional.ofNullable(loaded.entries().get(skillId));
    }

    private List<LayeredOfficeAssetLoader.Layer> skillLayers(boolean includeDrafts) {
        List<LayeredOfficeAssetLoader.Layer> layers = new ArrayList<>();
        layers.add(new LayeredOfficeAssetLoader.Layer(officeRoot.resolve("skills"), OfficeAssetSource.BUILTIN, false));
        layers.add(new LayeredOfficeAssetLoader.Layer(
                officeRoot.resolve("skills-market"), OfficeAssetSource.MARKET, true));
        if (importPaths != null) {
            layers.add(new LayeredOfficeAssetLoader.Layer(importPaths.skillsDir(), OfficeAssetSource.IMPORT, false));
            if (includeDrafts) {
                layers.add(new LayeredOfficeAssetLoader.Layer(
                        importPaths.skillsDraftsDir(), OfficeAssetSource.DRAFT, false));
            }
        }
        return layers;
    }

    public List<SkillDefinition> listSkills() {
        return skills.values().stream().map(SkillRegistryEntry::definition).toList();
    }

    public List<SkillRegistryEntry> listEntries() {
        return List.copyOf(skills.values());
    }

    public Optional<SkillDefinition> findSkill(String skillId) {
        return findEntry(skillId).map(SkillRegistryEntry::definition);
    }

    public SkillDefinition requireSkill(String skillId) {
        return findSkill(skillId).orElseThrow(() -> new SkillNotFoundException(skillId));
    }

    private void loadSkillDir(
            Path skillDir,
            OfficeAssetSource source,
            Map<String, SkillRegistryEntry> target,
            List<String> warnings,
            boolean skipIfExists) {
        Path yamlFile = skillDir.resolve("skill.yaml");
        Optional<Map<?, ?>> rawOpt = LayeredOfficeAssetLoader.readYamlMap(yamlFile);
        if (rawOpt.isEmpty()) {
            return;
        }
        Map<?, ?> raw = rawOpt.get();
        try {
            String id = OfficeYaml.stringField(raw, "id");
            if (id == null || id.isBlank()) {
                LOG.warn("Skip skill.yaml without id: {}", yamlFile);
                return;
            }
            if (LayeredOfficeAssetLoader.shouldSkipExisting(id, source, target, skipIfExists)) {
                return;
            }
            String skillFile = OfficeYaml.stringField(raw, "skillFile");
            if (skillFile == null || skillFile.isBlank()) {
                skillFile = "SKILL.md";
            }
            Path skillPath = skillDir.resolve(skillFile).normalize();
            if (!skillPath.startsWith(skillDir) || !Files.isRegularFile(skillPath)) {
                LOG.warn("Skip skill {} — skill file not found: {}", id, skillPath);
                return;
            }
            String skillBody = Files.readString(skillPath);
            SkillDefinition skill = new SkillDefinition(
                    id,
                    OfficeMarketText.normalizeDisplayText(OfficeYaml.stringField(raw, "name")),
                    OfficeMarketText.normalizeDisplayText(OfficeYaml.stringField(raw, "description")),
                    OfficeYaml.stringField(raw, "category"),
                    OfficeMarketText.normalizeTags(OfficeYaml.stringList(raw, "tags")),
                    OfficeYaml.stringField(raw, "source"),
                    OfficeYaml.booleanField(raw, "defaultInstalled", false),
                    skillBody);
            target.put(id, new SkillRegistryEntry(skill, source, skillDir, skillFile));
        } catch (IOException ex) {
            LOG.warn("Failed to load skill from {}: {}", yamlFile, ex.getMessage());
            warnings.add("Failed to load skill from " + yamlFile + ": " + ex.getMessage());
        }
    }
}
