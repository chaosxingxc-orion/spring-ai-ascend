package com.huawei.ascend.examples.deepresearch.search.a2a;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenSkillHubInstaller;
import com.huawei.ascend.runtime.engine.spi.SkillDefinition;
import com.huawei.ascend.runtime.engine.spi.SkillHubProvider;
import com.huawei.ascend.runtime.engine.spi.SkillPackage;
import com.huawei.ascend.runtime.engine.spi.SkillSummary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Filesystem-backed SkillHub provider. Each immediate subdirectory of
 * {@code root} containing a {@code SKILL.md} file is exposed as one skill;
 * the skill id equals the directory name and {@code SKILL.md} contents become
 * the skill instructions. The directory path is forwarded to the OpenJiuwen
 * installer via the {@code openjiuwen.skill.path} metadata key so {@link
 * com.openjiuwen.core.singleagent.BaseAgent#registerSkill} can load it.
 *
 * <p>Mirrors the {@code skillhub-local} example provider but lives inside the
 * search-agent-a2a module so the demo has no cross-example dependency. The
 * package operation is intentionally unsupported here — this demo never
 * downloads skill zips.
 */
final class LocalDirectorySkillHubProvider implements SkillHubProvider {
    private static final String SKILL_FILE = "SKILL.md";

    private final Path root;

    LocalDirectorySkillHubProvider(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public List<SkillSummary> listSkills(AgentExecutionContext context) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(root)) {
            return paths.filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve(SKILL_FILE)))
                    .map(this::toSummary)
                    .toList();
        } catch (IOException error) {
            throw new IllegalStateException("Failed to list local skills from " + root, error);
        }
    }

    @Override
    public SkillDefinition loadSkill(AgentExecutionContext context, String skillId) {
        Path skillDir = skillDir(skillId);
        Path skillFile = skillDir.resolve(SKILL_FILE);
        if (!Files.isRegularFile(skillFile)) {
            throw new IllegalArgumentException("Unknown skill: " + skillId);
        }
        try {
            String instructions = Files.readString(skillFile, StandardCharsets.UTF_8);
            return new SkillDefinition(
                    skillId,
                    title(instructions, skillId),
                    description(instructions),
                    instructions,
                    List.of(skillFile.toString()),
                    List.of(),
                    Map.of(OpenJiuwenSkillHubInstaller.METADATA_OPENJIUWEN_SKILL_PATH, skillDir.toString()));
        } catch (IOException error) {
            throw new IllegalStateException("Failed to load skill: " + skillId, error);
        }
    }

    @Override
    public SkillPackage loadSkillPackage(AgentExecutionContext context, String skillId) {
        throw new UnsupportedOperationException(
                "Skill package download is not supported in the search-agent demo provider");
    }

    Path root() {
        return root;
    }

    private SkillSummary toSummary(Path skillDir) {
        String skillId = skillDir.getFileName().toString();
        try {
            String instructions = Files.readString(skillDir.resolve(SKILL_FILE), StandardCharsets.UTF_8);
            return new SkillSummary(
                    skillId,
                    title(instructions, skillId),
                    description(instructions),
                    List.of("local"),
                    Map.of(OpenJiuwenSkillHubInstaller.METADATA_OPENJIUWEN_SKILL_PATH, skillDir.toString()));
        } catch (IOException error) {
            throw new IllegalStateException("Failed to read skill: " + skillId, error);
        }
    }

    private Path skillDir(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must not be blank");
        }
        Path skillDir = root.resolve(skillId).normalize();
        if (!skillDir.startsWith(root)) {
            throw new IllegalArgumentException("skillId escapes skill root: " + skillId);
        }
        return skillDir;
    }

    private static String title(String markdown, String fallback) {
        return markdown.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("# "))
                .map(line -> line.substring(2).trim())
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse(fallback);
    }

    private static String description(String markdown) {
        return markdown.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("#"))
                .findFirst()
                .orElse("");
    }
}