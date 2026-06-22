package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillHubProviderTest {

    @Test
    void skillSummaryNormalizesOptionalFieldsAndCopiesCollections() {
        List<String> tags = new java.util.ArrayList<>(List.of("travel"));
        Map<String, Object> metadata = new java.util.HashMap<>(Map.of("source", "local"));

        SkillSummary summary = new SkillSummary("hotel", "", null, tags, metadata);
        tags.add("changed");
        metadata.put("changed", true);

        assertThat(summary.skillId()).isEqualTo("hotel");
        assertThat(summary.name()).isEqualTo("hotel");
        assertThat(summary.description()).isEmpty();
        assertThat(summary.tags()).containsExactly("travel");
        assertThat(summary.metadata()).containsExactlyEntriesOf(Map.of("source", "local"));
    }

    @Test
    void skillDefinitionNormalizesOptionalFieldsAndCopiesCollections() {
        SkillToolDependency dependency = new SkillToolDependency("mcp", "time", Map.of("serverId", "local"));

        SkillDefinition definition = new SkillDefinition(
                "time-skill",
                null,
                null,
                null,
                List.of("file://SKILL.md"),
                List.of(dependency),
                null);

        assertThat(definition.name()).isEqualTo("time-skill");
        assertThat(definition.description()).isEmpty();
        assertThat(definition.instructions()).isEmpty();
        assertThat(definition.referenceUris()).containsExactly("file://SKILL.md");
        assertThat(definition.toolDependencies()).containsExactly(dependency);
        assertThat(definition.metadata()).isEmpty();
    }

    @Test
    void skillIdsMustNotBeBlank() {
        assertThatThrownBy(() -> new SkillSummary(" ", "name", "", List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skillId");
        assertThatThrownBy(() -> new SkillDefinition(null, "name", "", "", List.of(), List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skillId");
    }

    @Test
    void toolDependencyIsDescriptiveMetadataOnly() {
        SkillToolDependency dependency = new SkillToolDependency(null, null, null);

        assertThat(dependency.type()).isEqualTo("unknown");
        assertThat(dependency.name()).isEmpty();
        assertThat(dependency.metadata()).isEmpty();
    }

    @Test
    void skillPackageCopiesContentAndDefaultsMediaType() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);

        SkillPackage skillPackage = new SkillPackage("demo", null, content, null);
        content[0] = 'H';
        byte[] returned = skillPackage.content();
        returned[1] = 'A';

        assertThat(skillPackage.mediaType()).isEqualTo("application/octet-stream");
        assertThat(new String(skillPackage.content(), StandardCharsets.UTF_8)).isEqualTo("hello");
        assertThat(skillPackage.metadata()).isEmpty();
    }
}
