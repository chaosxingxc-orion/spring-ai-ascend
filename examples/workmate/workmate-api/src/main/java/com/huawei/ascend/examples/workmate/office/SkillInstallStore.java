package com.huawei.ascend.examples.workmate.office;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.market.PluginInstallStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Skill install index backed by {@link PluginInstallStore} (single source of truth). */
@Component
public class SkillInstallStore {

    public static final String BUILTIN_MARKETPLACE_ID = "workmate-builtin";
    private static final String BUILTIN_VERSION = "1.0.0";
    private static final String LEGACY_FILE_NAME = "skills-installed.json";

    private static final Logger LOG = LoggerFactory.getLogger(SkillInstallStore.class);

    private final PluginInstallStore pluginInstallStore;

    public SkillInstallStore(
            PluginInstallStore pluginInstallStore,
            WorkmateDataProperties dataProperties,
            ObjectMapper objectMapper) {
        this.pluginInstallStore = pluginInstallStore;
        migrateLegacyInstalls(dataProperties.resolvedPath().resolve(LEGACY_FILE_NAME), objectMapper);
    }

    public Set<String> installedIds() {
        return pluginInstallStore.installedPluginIds(BUILTIN_MARKETPLACE_ID);
    }

    public boolean isInstalled(String skillId) {
        return pluginInstallStore.isInstalled(BUILTIN_MARKETPLACE_ID, skillId);
    }

    public void install(String skillId) {
        pluginInstallStore.install(BUILTIN_MARKETPLACE_ID, skillId, BUILTIN_VERSION);
    }

    public void uninstall(String skillId) {
        pluginInstallStore.uninstall(BUILTIN_MARKETPLACE_ID, skillId);
    }

    public void ensureDefaults(SkillRegistry registry) {
        boolean changed = false;
        for (SkillDefinition skill : registry.listSkills()) {
            if (skill.defaultInstalled() && !isInstalled(skill.id())) {
                install(skill.id());
                changed = true;
            }
        }
        if (changed) {
            LOG.debug("Seeded default skill installs");
        }
    }

    private void migrateLegacyInstalls(Path legacyFile, ObjectMapper objectMapper) {
        if (!Files.isRegularFile(legacyFile)) {
            return;
        }
        try {
            Set<String> legacyIds = objectMapper.readValue(legacyFile.toFile(), new TypeReference<Set<String>>() {});
            if (legacyIds == null || legacyIds.isEmpty()) {
                return;
            }
            for (String skillId : legacyIds) {
                if (!pluginInstallStore.isInstalled(BUILTIN_MARKETPLACE_ID, skillId)) {
                    pluginInstallStore.install(BUILTIN_MARKETPLACE_ID, skillId, BUILTIN_VERSION);
                }
            }
            LOG.info("Migrated {} legacy skill installs from {}", legacyIds.size(), legacyFile.getFileName());
        } catch (IOException ex) {
            LOG.warn("Failed to migrate legacy skill installs {}: {}", legacyFile, ex.getMessage());
        }
    }
}
