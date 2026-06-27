package com.huawei.ascend.examples.workmate.office;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SkillRegistryTest {

    @Test
    void loadsBundledOfficeSkills() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        SkillRegistry registry = new SkillRegistry(new com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties(
                officeRoot.toString()), null);

        assertThat(registry.listSkills()).extracting(SkillDefinition::id)
                .contains("excel-handler", "skill-authoring", "web-access");
        assertThat(registry.requireSkill("skill-authoring").skillBody()).contains("SKILL.md");
    }
}
