package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.StudioExportItemResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioExportPreviewResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class StudioExportService {

    private static final String EXPORT_README = """
            # WorkMate Studio Export

            Unzip this archive into `examples/workmate/office/` (merge directories), then open a PR.

            Paths mirror the office SSOT layout (`experts/`, `experts-market/`, `skills/`, `skills-market/`, `playbooks/`, `welcome.yaml`).
            """;

    private final ExpertRegistry expertRegistry;
    private final SkillRegistry skillRegistry;
    private final PlaybookRegistry playbookRegistry;
    private final StudioDraftStore draftStore;

    public StudioExportService(
            ExpertRegistry expertRegistry,
            SkillRegistry skillRegistry,
            PlaybookRegistry playbookRegistry,
            StudioDraftStore draftStore) {
        this.expertRegistry = expertRegistry;
        this.skillRegistry = skillRegistry;
        this.playbookRegistry = playbookRegistry;
        this.draftStore = draftStore;
    }

    public StudioExportPreviewResponse previewExport() {
        List<StudioExportItemResponse> items = new ArrayList<>();
        int expertCount = 0;
        int skillCount = 0;
        for (String expertId : listTopLevelExpertDraftIds()) {
            items.add(describeExpertExport(expertId));
            expertCount++;
        }
        for (String skillId : draftStore.listSkillDraftIds()) {
            items.add(describeSkillExport(skillId));
            skillCount++;
        }
        for (String playbookId : draftStore.listPlaybookDraftIds()) {
            items.add(describePlaybookExport(playbookId));
        }
        if (draftStore.welcomeDraftExists()) {
            items.add(describeWelcomeExport());
        }
        return new StudioExportPreviewResponse(List.copyOf(items), expertCount, skillCount);
    }

    public byte[] exportExpertZip(String expertId) {
        String safeId = OfficeImportValidator.requireSafeId(expertId, "Expert");
        if (!draftStore.expertDraftExists(safeId) && !hasMemberDrafts(safeId)) {
            throw new IllegalArgumentException("No draft to export for expert: " + safeId);
        }
        Map<String, byte[]> entries = new LinkedHashMap<>();
        addExpertDraftEntries(safeId, entries);
        entries.put("README.md", StudioZipWriter.readme(EXPORT_README));
        return StudioZipWriter.buildZip(entries);
    }

    public byte[] exportSkillZip(String skillId) {
        String safeId = OfficeImportValidator.requireSafeId(skillId, "Skill");
        if (!draftStore.skillDraftExists(safeId)) {
            throw new IllegalArgumentException("No draft to export for skill: " + safeId);
        }
        Map<String, byte[]> entries = new LinkedHashMap<>();
        addSkillDraftEntries(safeId, entries);
        entries.put("README.md", StudioZipWriter.readme(EXPORT_README));
        return StudioZipWriter.buildZip(entries);
    }

    public byte[] exportWelcomeZip() {
        if (!draftStore.welcomeDraftExists()) {
            throw new IllegalArgumentException("No welcome draft to export");
        }
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("office/welcome.yaml", draftStore.readWelcomeDraft().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("README.md", StudioZipWriter.readme(EXPORT_README));
        return StudioZipWriter.buildZip(entries);
    }

    public byte[] exportPlaybookZip(String playbookId) {
        String safeId = OfficeImportValidator.requireSafeId(playbookId, "Playbook");
        if (!draftStore.playbookDraftExists(safeId)) {
            throw new IllegalArgumentException("No playbook draft to export: " + safeId);
        }
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(
                "office/playbooks/" + safeId + ".yaml",
                draftStore.readPlaybookDraft(safeId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("README.md", StudioZipWriter.readme(EXPORT_README));
        return StudioZipWriter.buildZip(entries);
    }

    public byte[] exportAllDraftsZip() {
        StudioExportPreviewResponse preview = previewExport();
        if (preview.items().isEmpty()) {
            throw new IllegalArgumentException("No drafts to export");
        }
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (StudioExportItemResponse item : preview.items()) {
            if ("skill".equals(item.assetType())) {
                addSkillDraftEntries(item.id(), entries);
            } else if ("playbook".equals(item.assetType())) {
                entries.put(
                        "office/playbooks/" + item.id() + ".yaml",
                        draftStore.readPlaybookDraft(item.id()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else if ("welcome".equals(item.assetType())) {
                entries.put(
                        "office/welcome.yaml",
                        draftStore.readWelcomeDraft().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else {
                addExpertDraftEntries(item.id(), entries);
            }
        }
        entries.put("README.md", StudioZipWriter.readme(EXPORT_README));
        return StudioZipWriter.buildZip(entries);
    }

    private void addExpertDraftEntries(String expertId, Map<String, byte[]> entries) {
        String prefix = resolveExpertOfficePrefix(expertId) + "/" + expertId;
        copyDraftDir(draftStore.expertDraftDir(expertId), prefix, entries);
        String memberPrefix = expertId + "__";
        for (String memberExpertId : draftStore.listExpertDraftIds()) {
            if (memberExpertId.startsWith(memberPrefix)) {
                copyDraftDir(
                        draftStore.expertDraftDir(memberExpertId),
                        resolveExpertOfficePrefix(memberExpertId) + "/" + memberExpertId,
                        entries);
            }
        }
    }

    private void addSkillDraftEntries(String skillId, Map<String, byte[]> entries) {
        copyDraftDir(draftStore.skillDraftDir(skillId), resolveSkillOfficePrefix(skillId) + "/" + skillId, entries);
    }

    private StudioExportItemResponse describeExpertExport(String expertId) {
        ExpertRegistryEntry entry =
                expertRegistry.findEntry(expertId).orElseThrow(() -> new ExpertNotFoundException(expertId));
        String prefix = resolveExpertOfficePrefix(expertId) + "/" + expertId;
        List<String> files = listDraftFiles(draftStore.expertDraftDir(expertId), prefix);
        String memberPrefix = expertId + "__";
        for (String memberExpertId : draftStore.listExpertDraftIds()) {
            if (memberExpertId.startsWith(memberPrefix)) {
                String memberPrefixPath = resolveExpertOfficePrefix(memberExpertId) + "/" + memberExpertId;
                files.addAll(listDraftFiles(draftStore.expertDraftDir(memberExpertId), memberPrefixPath));
            }
        }
        return new StudioExportItemResponse(
                expertId,
                entry.definition().expertType() == null ? "agent" : entry.definition().expertType(),
                entry.definition().name(),
                prefix,
                List.copyOf(files));
    }

    private StudioExportItemResponse describeSkillExport(String skillId) {
        SkillRegistryEntry entry =
                skillRegistry.findEntry(skillId).orElseThrow(() -> new SkillNotFoundException(skillId));
        String prefix = resolveSkillOfficePrefix(skillId) + "/" + skillId;
        return new StudioExportItemResponse(
                skillId,
                "skill",
                entry.definition().name(),
                prefix,
                listDraftFiles(draftStore.skillDraftDir(skillId), prefix));
    }

    private StudioExportItemResponse describePlaybookExport(String playbookId) {
        PlaybookRegistryEntry entry =
                playbookRegistry.findEntry(playbookId).orElseThrow(() -> new PlaybookNotFoundException(playbookId));
        String path = "office/playbooks/" + playbookId + ".yaml";
        return new StudioExportItemResponse(
                playbookId,
                "playbook",
                entry.definition().title(),
                path,
                List.of(path));
    }

    private StudioExportItemResponse describeWelcomeExport() {
        return new StudioExportItemResponse(
                "welcome",
                "welcome",
                "welcome.yaml",
                "office/welcome.yaml",
                List.of("office/welcome.yaml"));
    }

    private List<String> listTopLevelExpertDraftIds() {
        List<String> ids = new ArrayList<>();
        for (String expertId : draftStore.listExpertDraftIds()) {
            if (!isMemberSubExpert(expertId)) {
                ids.add(expertId);
            }
        }
        return ids;
    }

    private boolean hasMemberDrafts(String teamId) {
        String prefix = teamId + "__";
        return draftStore.listExpertDraftIds().stream().anyMatch(id -> id.startsWith(prefix));
    }

    private static boolean isMemberSubExpert(String expertId) {
        return expertId.contains("__");
    }

    private String resolveExpertOfficePrefix(String expertId) {
        return expertRegistry
                .findBaselineEntry(expertId)
                .map(entry -> officeSegmentFromDir(entry.sourceDir(), "experts"))
                .orElseGet(() -> defaultExpertOfficeSegment(expertId));
    }

    private String resolveSkillOfficePrefix(String skillId) {
        return skillRegistry
                .findBaselineEntry(skillId)
                .map(entry -> officeSegmentFromDir(entry.sourceDir(), "skills"))
                .orElse("office/skills-market");
    }

    private String defaultExpertOfficeSegment(String expertId) {
        ExpertRegistryEntry current = expertRegistry.findEntry(expertId).orElse(null);
        if (current != null && "team".equalsIgnoreCase(current.definition().expertType())) {
            return "office/experts-market";
        }
        if (expertId.contains("__")) {
            String teamId = expertId.substring(0, expertId.indexOf("__"));
            return resolveExpertOfficePrefix(teamId);
        }
        return "office/experts-market";
    }

    private static String officeSegmentFromDir(Path sourceDir, String kind) {
        String normalized = sourceDir.toString().replace('\\', '/');
        if (normalized.contains("/" + kind + "-market/") || normalized.endsWith("/" + kind + "-market")) {
            return "office/" + kind + "-market";
        }
        return "office/" + kind;
    }

    private static List<String> listDraftFiles(Path draftDir, String officePrefix) {
        if (!Files.isDirectory(draftDir)) {
            return List.of();
        }
        List<String> files = new ArrayList<>();
        try (Stream<Path> walk = Files.list(draftDir)) {
            walk.filter(Files::isRegularFile)
                    .forEach(path -> files.add(officePrefix + "/" + path.getFileName()));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list draft files under " + draftDir, ex);
        }
        return files;
    }

    private static void copyDraftDir(Path draftDir, String officePrefix, Map<String, byte[]> entries) {
        if (!Files.isDirectory(draftDir)) {
            return;
        }
        try {
            StudioZipWriter.addDirectoryFiles(draftDir, officePrefix, entries);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to export draft directory " + draftDir, ex);
        }
    }
}
