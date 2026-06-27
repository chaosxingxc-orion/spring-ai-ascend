package com.huawei.ascend.examples.workmate.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.examples.workmate.capability.CapabilityUsageService;
import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.market.PluginInstallStore;
import com.huawei.ascend.examples.workmate.skill.SkillSecurityScanner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @Mock
    private SkillSecurityScanner securityScanner;

    @Mock
    private CapabilityUsageService capabilityUsageService;

    private SkillRegistry registry;
    private SkillInstallStore installStore;
    private PluginInstallStore pluginInstallStore;
    private SkillService service;

    @BeforeEach
    void setUp() throws Exception {
        Path dataDir = Files.createTempDirectory("workmate-skill-service-");
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        registry = new SkillRegistry(
                new com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties(
                        Path.of("../office").toAbsolutePath().normalize().toString()),
                null);
        var dataProperties = new WorkmateDataProperties(dataDir.toString());
        pluginInstallStore = new PluginInstallStore(dataProperties, objectMapper);
        installStore = new SkillInstallStore(pluginInstallStore, dataProperties, objectMapper);
        service = new SkillService(registry, installStore, securityScanner, capabilityUsageService);
        service.seedDefaultInstalls();
    }

    @Test
    void installSyncsBuiltinPluginStore() {
        SkillDefinition skill = registry.listSkills().stream()
                .filter(item -> !item.defaultInstalled())
                .findFirst()
                .orElseThrow();
        assertThat(installStore.isInstalled(skill.id())).isFalse();

        service.install(skill.id());

        assertThat(installStore.isInstalled(skill.id())).isTrue();
        assertThat(pluginInstallStore.isInstalled("workmate-builtin", skill.id())).isTrue();
    }

    @Test
    void uninstallRejectsPolicyLockedSkill() {
        SkillDefinition locked = registry.listSkills().stream()
                .filter(SkillDefinition::defaultInstalled)
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(() -> service.uninstall(locked.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Policy locked");
    }

    @Test
    void uninstallSyncsBuiltinPluginStore() {
        SkillDefinition skill = registry.listSkills().stream()
                .filter(item -> !item.defaultInstalled())
                .findFirst()
                .orElseThrow();
        service.install(skill.id());

        service.uninstall(skill.id());

        assertThat(installStore.isInstalled(skill.id())).isFalse();
        assertThat(pluginInstallStore.isInstalled("workmate-builtin", skill.id())).isFalse();
    }
}
