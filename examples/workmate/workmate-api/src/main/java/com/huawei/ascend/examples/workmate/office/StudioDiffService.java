package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.StudioAssetDiffResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class StudioDiffService {

    private final ExpertRegistry expertRegistry;
    private final SkillRegistry skillRegistry;
    private final PlaybookRegistry playbookRegistry;
    private final WelcomeRegistry welcomeRegistry;
    private final StudioDraftStore draftStore;

    public StudioDiffService(
            ExpertRegistry expertRegistry,
            SkillRegistry skillRegistry,
            PlaybookRegistry playbookRegistry,
            WelcomeRegistry welcomeRegistry,
            StudioDraftStore draftStore) {
        this.expertRegistry = expertRegistry;
        this.skillRegistry = skillRegistry;
        this.playbookRegistry = playbookRegistry;
        this.welcomeRegistry = welcomeRegistry;
        this.draftStore = draftStore;
    }

    public StudioAssetDiffResponse getExpertDiff(String expertId) {
        String safeId = OfficeImportValidator.requireSafeId(expertId, "Expert");
        ExpertRegistryEntry current =
                expertRegistry.findEntry(safeId).orElseThrow(() -> new ExpertNotFoundException(safeId));
        Optional<ExpertRegistryEntry> baseline = expertRegistry.findBaselineEntry(safeId);
        boolean hasDraft = draftStore.expertDraftExists(safeId);
        boolean hasBaseline = baseline.isPresent();
        String draftPrompt = normalizeText(current.definition().systemPrompt());
        String baselinePrompt = baseline.map(entry -> normalizeText(entry.definition().systemPrompt())).orElse(null);
        List<String> changedFields = diffExpertFields(current, baseline.orElse(null));
        return new StudioAssetDiffResponse(
                hasDraft,
                hasBaseline,
                hasDraft,
                current.source(),
                baseline.map(ExpertRegistryEntry::source).orElse(null),
                current.promptFile(),
                baselinePrompt,
                draftPrompt,
                changedFields);
    }

    public StudioAssetDiffResponse getWelcomeDiff() {
        boolean hasDraft = draftStore.welcomeDraftExists();
        String baseline = normalizeText(draftStore.readBuiltinWelcome(welcomeRegistry.officeRoot()));
        String draftYaml = hasDraft ? normalizeText(draftStore.readWelcomeDraft()) : baseline;
        List<String> changedFields = hasDraft && !Objects.equals(draftYaml, baseline) ? List.of("welcomeYaml") : List.of();
        return new StudioAssetDiffResponse(
                hasDraft,
                !baseline.isBlank(),
                hasDraft,
                hasDraft ? OfficeAssetSource.DRAFT : OfficeAssetSource.BUILTIN,
                OfficeAssetSource.BUILTIN,
                "welcome.yaml",
                baseline.isBlank() ? null : baseline,
                draftYaml,
                changedFields);
    }

    public StudioAssetDiffResponse getPlaybookDiff(String playbookId) {
        String safeId = OfficeImportValidator.requireSafeId(playbookId, "Playbook");
        PlaybookRegistryEntry current =
                playbookRegistry.findEntry(safeId).orElseThrow(() -> new PlaybookNotFoundException(safeId));
        Optional<PlaybookRegistryEntry> baseline = playbookRegistry.findBaselineEntry(safeId);
        boolean hasDraft = draftStore.playbookDraftExists(safeId);
        boolean hasBaseline = baseline.isPresent();
        String draftYaml = normalizeText(PlaybookYamlWriter.render(current.definition()));
        String baselineYaml =
                baseline.map(entry -> normalizeText(PlaybookYamlWriter.render(entry.definition()))).orElse(null);
        List<String> changedFields = diffPlaybookFields(current, baseline.orElse(null));
        return new StudioAssetDiffResponse(
                hasDraft,
                hasBaseline,
                hasDraft,
                current.source(),
                baseline.map(PlaybookRegistryEntry::source).orElse(null),
                safeId + ".yaml",
                baselineYaml,
                draftYaml,
                changedFields);
    }

    public StudioAssetDiffResponse getSkillDiff(String skillId) {
        String safeId = OfficeImportValidator.requireSafeId(skillId, "Skill");
        SkillRegistryEntry current =
                skillRegistry.findEntry(safeId).orElseThrow(() -> new SkillNotFoundException(safeId));
        Optional<SkillRegistryEntry> baseline = skillRegistry.findBaselineEntry(safeId);
        boolean hasDraft = draftStore.skillDraftExists(safeId);
        boolean hasBaseline = baseline.isPresent();
        String draftBody = normalizeText(current.definition().skillBody());
        String baselineBody = baseline.map(entry -> normalizeText(entry.definition().skillBody())).orElse(null);
        List<String> changedFields = diffSkillFields(current, baseline.orElse(null));
        return new StudioAssetDiffResponse(
                hasDraft,
                hasBaseline,
                hasDraft,
                current.source(),
                baseline.map(SkillRegistryEntry::source).orElse(null),
                current.skillFile(),
                baselineBody,
                draftBody,
                changedFields);
    }

    private static List<String> diffExpertFields(ExpertRegistryEntry current, ExpertRegistryEntry baseline) {
        if (baseline == null) {
            return List.of("new");
        }
        var draft = current.definition();
        var original = baseline.definition();
        List<String> changed = new ArrayList<>();
        addIfChanged(changed, "name", draft.name(), original.name());
        addIfChanged(changed, "description", draft.description(), original.description());
        addIfChanged(changed, "category", draft.category(), original.category());
        addIfChanged(changed, "promptContent", draft.systemPrompt(), original.systemPrompt());
        addIfChanged(changed, "defaultInitPrompt", draft.defaultInitPrompt(), original.defaultInitPrompt());
        addIfChanged(changed, "maxTurns", draft.maxTurns(), original.maxTurns());
        if (!Objects.equals(draft.tags(), original.tags())) {
            changed.add("tags");
        }
        if (!Objects.equals(draft.skillCompatibility(), original.skillCompatibility())) {
            changed.add("skillCompatibility");
        }
        return List.copyOf(changed);
    }

    private static List<String> diffSkillFields(SkillRegistryEntry current, SkillRegistryEntry baseline) {
        if (baseline == null) {
            return List.of("new");
        }
        var draft = current.definition();
        var original = baseline.definition();
        List<String> changed = new ArrayList<>();
        addIfChanged(changed, "name", draft.name(), original.name());
        addIfChanged(changed, "description", draft.description(), original.description());
        addIfChanged(changed, "category", draft.category(), original.category());
        addIfChanged(changed, "skillContent", draft.skillBody(), original.skillBody());
        if (!Objects.equals(draft.tags(), original.tags())) {
            changed.add("tags");
        }
        return List.copyOf(changed);
    }

    private static List<String> diffPlaybookFields(PlaybookRegistryEntry current, PlaybookRegistryEntry baseline) {
        if (baseline == null) {
            return List.of("new");
        }
        var draft = current.definition();
        var original = baseline.definition();
        List<String> changed = new ArrayList<>();
        addIfChanged(changed, "title", draft.title(), original.title());
        addIfChanged(changed, "description", draft.description(), original.description());
        addIfChanged(changed, "accent", draft.accent(), original.accent());
        addIfChanged(changed, "expertId", draft.expertId(), original.expertId());
        addIfChanged(changed, "initPrompt", draft.initPrompt(), original.initPrompt());
        if (!Objects.equals(draft.placements(), original.placements())) {
            changed.add("placements");
        }
        return List.copyOf(changed);
    }

    private static void addIfChanged(List<String> changed, String field, Object draft, Object baseline) {
        if (!Objects.equals(normalizeNullable(draft), normalizeNullable(baseline))) {
            changed.add(field);
        }
    }

    private static Object normalizeNullable(Object value) {
        if (value instanceof String text) {
            return normalizeText(text);
        }
        return value;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").stripTrailing();
    }
}
