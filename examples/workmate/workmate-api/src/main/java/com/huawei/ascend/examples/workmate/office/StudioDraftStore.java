package com.huawei.ascend.examples.workmate.office;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StudioDraftStore {

    private final OfficeImportPaths importPaths;

    public StudioDraftStore(OfficeImportPaths importPaths) {
        this.importPaths = importPaths;
    }

    public Path expertDraftDir(String expertId) {
        return importPaths.expertDraftDir(expertId);
    }

    public Path skillDraftDir(String skillId) {
        return importPaths.skillDraftDir(skillId);
    }

    public boolean expertDraftExists(String expertId) {
        return Files.isRegularFile(expertDraftDir(expertId).resolve("expert.yaml"));
    }

    public boolean skillDraftExists(String skillId) {
        return Files.isRegularFile(skillDraftDir(skillId).resolve("skill.yaml"));
    }

    public void writeExpertDraft(String expertId, ExpertDefinition expert, String promptFile, String promptContent) {
        Path dir = expertDraftDir(expertId);
        try {
            Files.createDirectories(dir);
            Files.writeString(resolveWithin(dir, promptFile), normalizeContent(promptContent));
            Files.writeString(resolveWithin(dir, "expert.yaml"), ExpertYamlWriter.render(expert, promptFile));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write expert draft " + expertId, ex);
        }
    }

    public void writeSkillDraft(String skillId, SkillDefinition skill, String skillFile) {
        Path dir = skillDraftDir(skillId);
        try {
            Files.createDirectories(dir);
            Files.writeString(resolveWithin(dir, skillFile), normalizeContent(skill.skillBody()));
            Files.writeString(resolveWithin(dir, "skill.yaml"), SkillYamlWriter.render(skill, skillFile));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write skill draft " + skillId, ex);
        }
    }

    /**
     * Resolve {@code fileName} under {@code dir} and verify the normalized result stays inside the
     * draft directory. Defense-in-depth: even if upstream validation is bypassed, a path-traversal
     * file name can never escape the drafts root.
     */
    private static Path resolveWithin(Path dir, String fileName) {
        Path base = dir.toAbsolutePath().normalize();
        Path target = base.resolve(fileName).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("Draft file escapes drafts directory: " + fileName);
        }
        return target;
    }

    public void deleteExpertDraft(String expertId) {
        deleteDirectory(expertDraftDir(expertId));
    }

    public void deleteSkillDraft(String skillId) {
        deleteDirectory(skillDraftDir(skillId));
    }

    public boolean welcomeDraftExists() {
        return Files.isRegularFile(importPaths.welcomeDraftFile());
    }

    public void writeWelcomeDraft(String yamlContent) {
        try {
            Files.createDirectories(importPaths.draftsRoot());
            Files.writeString(importPaths.welcomeDraftFile(), normalizeContent(yamlContent));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write welcome draft", ex);
        }
    }

    public void deleteWelcomeDraft() {
        try {
            Files.deleteIfExists(importPaths.welcomeDraftFile());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete welcome draft", ex);
        }
    }

    public String readWelcomeDraft() {
        try {
            return Files.readString(importPaths.welcomeDraftFile());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read welcome draft", ex);
        }
    }

    public String readBuiltinWelcome(Path officeRoot) {
        Path file = importPaths.welcomeBuiltinFile(officeRoot);
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try {
            return Files.readString(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read welcome.yaml from office", ex);
        }
    }

    public List<String> listExpertDraftIds() {
        return listDraftIds(importPaths.expertsDraftsDir());
    }

    public List<String> listSkillDraftIds() {
        return listDraftIds(importPaths.skillsDraftsDir());
    }

    public boolean playbookDraftExists(String playbookId) {
        return Files.isRegularFile(importPaths.playbookDraftFile(playbookId));
    }

    public void writePlaybookDraft(String playbookId, String yamlContent) {
        try {
            Files.createDirectories(importPaths.playbooksDraftsDir());
            Files.writeString(importPaths.playbookDraftFile(playbookId), normalizeContent(yamlContent));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write playbook draft " + playbookId, ex);
        }
    }

    public void deletePlaybookDraft(String playbookId) {
        try {
            Files.deleteIfExists(importPaths.playbookDraftFile(playbookId));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete playbook draft " + playbookId, ex);
        }
    }

    public String readPlaybookDraft(String playbookId) {
        try {
            return Files.readString(importPaths.playbookDraftFile(playbookId));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read playbook draft " + playbookId, ex);
        }
    }

    public String readBuiltinPlaybook(Path officeRoot, String playbookId) {
        Path file = importPaths.playbookBuiltinFile(officeRoot, playbookId);
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try {
            return Files.readString(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read playbook from office: " + playbookId, ex);
        }
    }

    public List<String> listPlaybookDraftIds() {
        Path dir = importPaths.playbooksDraftsDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".yaml"))
                    .map(name -> name.substring(0, name.length() - ".yaml".length()))
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list playbook drafts", ex);
        }
    }

    private List<String> listDraftIds(Path draftsDir) {
        if (!Files.isDirectory(draftsDir)) {
            return List.of();
        }
        try (var stream = Files.list(draftsDir)) {
            return stream.filter(Files::isDirectory)
                    .filter(dir -> Files.isRegularFile(dir.resolve("expert.yaml"))
                            || Files.isRegularFile(dir.resolve("skill.yaml")))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list drafts under " + draftsDir, ex);
        }
    }

    private static void deleteDirectory(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed to delete draft path " + path, ex);
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete draft directory " + dir, ex);
        }
    }

    private static String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        return content.endsWith(System.lineSeparator()) ? content : content + System.lineSeparator();
    }
}
