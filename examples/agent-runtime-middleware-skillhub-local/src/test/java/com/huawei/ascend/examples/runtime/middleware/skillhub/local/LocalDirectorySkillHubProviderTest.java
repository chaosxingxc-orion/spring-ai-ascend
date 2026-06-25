package com.huawei.ascend.examples.runtime.middleware.skillhub.local;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDirectorySkillHubProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void listsAndLoadsLocalSkillDirectory() throws Exception {
        Path skillDir = tempDir.resolve("date-helper");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                description: Date helper skill for business calendar rules.
                ---

                # Date Helper

                Answer date questions with the provided business calendar rules.
                """);
        LocalDirectorySkillHubProvider provider = new LocalDirectorySkillHubProvider(tempDir);

        List<com.huawei.ascend.runtime.engine.spi.SkillSummary> summaries = provider.listSkills(context());
        com.huawei.ascend.runtime.engine.spi.SkillDefinition definition =
                provider.loadSkill(context(), "date-helper");
        com.huawei.ascend.runtime.engine.spi.SkillPackage skillPackage =
                provider.loadSkillPackage(context(), "date-helper");

        assertThat(summaries)
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.skillId()).isEqualTo("date-helper");
                    assertThat(summary.name()).isEqualTo("Date Helper");
                    assertThat(summary.description()).contains("business calendar");
                });
        assertThat(definition.instructions()).contains("Date Helper");
        assertThat(definition.metadata()).containsKey("openjiuwen.skill.path");
        assertThat(skillPackage.mediaType()).isEqualTo("application/zip");
        assertThat(zipEntryNames(skillPackage.content())).contains("SKILL.md");
    }

    private static List<String> zipEntryNames(byte[] content) throws Exception {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(content))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(
                new RuntimeIdentity("tenant", "user", "session", "task", "agent"),
                "USER_MESSAGE",
                List.of(RuntimeMessage.user("date")),
                Map.of());
    }
}
