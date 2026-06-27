package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.ExpertImportRequest;
import com.huawei.ascend.examples.workmate.office.dto.ImportValidationResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioDryRunResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioExpertSourceResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioExpertWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioSkillSourceResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioSkillWriteRequest;
import com.huawei.ascend.examples.workmate.session.dto.CreateSessionRequest;
import com.huawei.ascend.examples.workmate.session.dto.CreateSessionResponse;
import com.huawei.ascend.examples.workmate.session.SessionService;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StudioWriteService {

    private final ExpertRegistry expertRegistry;
    private final SkillRegistry skillRegistry;
    private final SkillInstallStore skillInstallStore;
    private final StudioDraftStore draftStore;
    private final SessionService sessionService;
    private final ExpertImportService expertImportService;
    private final StudioDraftCoordinator draftCoordinator;

    public StudioWriteService(
            ExpertRegistry expertRegistry,
            SkillRegistry skillRegistry,
            SkillInstallStore skillInstallStore,
            StudioDraftStore draftStore,
            SessionService sessionService,
            ExpertImportService expertImportService,
            StudioDraftCoordinator draftCoordinator) {
        this.expertRegistry = expertRegistry;
        this.skillRegistry = skillRegistry;
        this.skillInstallStore = skillInstallStore;
        this.draftStore = draftStore;
        this.sessionService = sessionService;
        this.expertImportService = expertImportService;
        this.draftCoordinator = draftCoordinator;
    }

    public ImportValidationResponse validateExpert(StudioExpertWriteRequest request, String pathExpertId) {
        return StudioDraftCoordinator.validate(
                () -> normalizeExpertRequest(request, pathExpertId, pathExpertId == null));
    }

    public ImportValidationResponse validateSkill(StudioSkillWriteRequest request, String pathSkillId) {
        return StudioDraftCoordinator.validate(
                () -> normalizeSkillRequest(request, pathSkillId, pathSkillId == null));
    }

    public StudioExpertSourceResponse createExpert(StudioExpertWriteRequest request) {
        return createExpert(request, "blank");
    }

    private StudioExpertSourceResponse createExpert(StudioExpertWriteRequest request, String origin) {
        NormalizedExpertWrite normalized = normalizeExpertRequest(request, null, true);
        if (expertRegistry.findExpert(normalized.expertId()).isPresent()) {
            throw new IllegalArgumentException("Expert already exists: " + normalized.expertId());
        }
        draftStore.writeExpertDraft(
                normalized.expertId(),
                normalized.definition(),
                normalized.promptFile(),
                normalized.promptContent());
        draftCoordinator.commitSaved("expert", normalized.expertId(), origin, this::reloadExperts);
        return getExpertSource(normalized.expertId());
    }

    public StudioExpertSourceResponse importExpertZip(InputStream zipStream) {
        ExpertImportRequest parsed = expertImportService.parseExpertZip(zipStream);
        StudioExpertWriteRequest request = new StudioExpertWriteRequest(
                parsed.id(),
                parsed.name(),
                parsed.description(),
                "agent",
                parsed.promptContent(),
                "prompt.md",
                parsed.defaultInitPrompt(),
                parsed.category(),
                parsed.tags(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null);
        return createExpert(request, "import");
    }

    public StudioExpertSourceResponse updateExpert(String expertId, StudioExpertWriteRequest request) {
        NormalizedExpertWrite normalized = normalizeExpertRequest(request, expertId, false);
        if (expertRegistry.findExpert(normalized.expertId()).isEmpty()) {
            throw new ExpertNotFoundException(normalized.expertId());
        }
        draftStore.writeExpertDraft(
                normalized.expertId(),
                normalized.definition(),
                normalized.promptFile(),
                normalized.promptContent());
        draftCoordinator.commitSaved(
                "expert",
                normalized.expertId(),
                StudioDraftCoordinator.resolveOrigin(
                        expertRegistry.findBaselineEntry(normalized.expertId()).isPresent()),
                this::reloadExperts);
        return getExpertSource(normalized.expertId());
    }

    public void deleteExpert(String expertId) {
        String safeId = OfficeImportValidator.requireSafeId(expertId, "Expert");
        draftCoordinator.rollbackDraft(
                "expert",
                safeId,
                draftStore::expertDraftExists,
                () -> draftStore.deleteExpertDraft(safeId),
                this::reloadExperts,
                "No draft to delete for expert: " + safeId);
    }

    public StudioExpertSourceResponse forkExpert(String expertId) {
        String safeId = OfficeImportValidator.requireSafeId(expertId, "Expert");
        ExpertRegistryEntry entry =
                expertRegistry.findEntry(safeId).orElseThrow(() -> new ExpertNotFoundException(safeId));
        ExpertDefinition expert = entry.definition();
        draftStore.writeExpertDraft(safeId, expert, entry.promptFile(), expert.systemPrompt());
        draftCoordinator.commitSaved("expert", safeId, "fork", this::reloadExperts);
        return getExpertSource(safeId);
    }

    public StudioDryRunResponse dryRunExpert(String expertId) {
        String safeId = OfficeImportValidator.requireSafeId(expertId, "Expert");
        ExpertDefinition expert = expertRegistry.requireExpert(safeId);
        CreateSessionResponse created = sessionService.createSession(new CreateSessionRequest(
                "Studio dry-run: " + expert.name(),
                null,
                safeId,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
        return new StudioDryRunResponse(created.session().id(), safeId, created.session().title());
    }

    public StudioSkillSourceResponse createSkill(StudioSkillWriteRequest request) {
        NormalizedSkillWrite normalized = normalizeSkillRequest(request, null, true);
        if (skillRegistry.findSkill(normalized.skillId()).isPresent()) {
            throw new IllegalArgumentException("Skill already exists: " + normalized.skillId());
        }
        draftStore.writeSkillDraft(normalized.skillId(), normalized.definition(), normalized.skillFile());
        draftCoordinator.commitSaved("skill", normalized.skillId(), "blank", this::reloadSkills);
        return getSkillSource(normalized.skillId());
    }

    public StudioSkillSourceResponse updateSkill(String skillId, StudioSkillWriteRequest request) {
        NormalizedSkillWrite normalized = normalizeSkillRequest(request, skillId, false);
        if (skillRegistry.findSkill(normalized.skillId()).isEmpty()) {
            throw new SkillNotFoundException(normalized.skillId());
        }
        draftStore.writeSkillDraft(normalized.skillId(), normalized.definition(), normalized.skillFile());
        draftCoordinator.commitSaved(
                "skill",
                normalized.skillId(),
                StudioDraftCoordinator.resolveOrigin(
                        skillRegistry.findBaselineEntry(normalized.skillId()).isPresent()),
                this::reloadSkills);
        return getSkillSource(normalized.skillId());
    }

    public void deleteSkill(String skillId) {
        String safeId = OfficeImportValidator.requireSafeId(skillId, "Skill");
        draftCoordinator.rollbackDraft(
                "skill",
                safeId,
                draftStore::skillDraftExists,
                () -> draftStore.deleteSkillDraft(safeId),
                this::reloadSkills,
                "No draft to delete for skill: " + safeId);
    }

    private StudioExpertSourceResponse getExpertSource(String expertId) {
        ExpertRegistryEntry entry =
                expertRegistry.findEntry(expertId).orElseThrow(() -> new ExpertNotFoundException(expertId));
        return StudioExpertSourceResponse.from(entry);
    }

    private StudioSkillSourceResponse getSkillSource(String skillId) {
        SkillRegistryEntry entry =
                skillRegistry.findEntry(skillId).orElseThrow(() -> new SkillNotFoundException(skillId));
        return StudioSkillSourceResponse.from(entry, skillInstallStore.isInstalled(skillId));
    }

    private void reloadExperts() {
        expertRegistry.reloadAll();
    }

    private void reloadSkills() {
        skillRegistry.reloadAll();
    }

    private NormalizedExpertWrite normalizeExpertRequest(
            StudioExpertWriteRequest request, String pathExpertId, boolean creating) {
        if (request == null) {
            throw new IllegalArgumentException("Request body required");
        }
        String expertId = creating
                ? OfficeImportValidator.requireSafeId(request.id(), "Expert")
                : OfficeImportValidator.requireSafeId(pathExpertId, "Expert");
        String name = OfficeImportValidator.requireText(request.name(), "Expert name");
        String description = OfficeImportValidator.requireText(request.description(), "Expert description");
        String promptContent = OfficeImportValidator.requireText(request.promptContent(), "Prompt content");
        String expertType = request.expertType() == null || request.expertType().isBlank()
                ? "agent"
                : request.expertType().trim();
        if (!"agent".equals(expertType)) {
            throw new IllegalArgumentException("Only agent expertType is supported in W52 Phase 1");
        }
        if (request.maxTurns() != null && request.maxTurns() <= 0) {
            throw new IllegalArgumentException("maxTurns must be positive when provided");
        }
        String promptFile = resolvePromptFile(request.promptFile(), expertType);
        String category = blankToDefault(request.category(), "custom");
        List<String> tags = request.tags() == null ? List.of("draft") : List.copyOf(request.tags());
        List<String> skillCompatibility =
                request.skillCompatibility() == null ? List.of() : List.copyOf(request.skillCompatibility());
        List<String> preloadSkills = request.preloadSkills() == null ? List.of() : List.copyOf(request.preloadSkills());
        List<String> quickPrompts = request.quickPrompts() == null ? List.of() : List.copyOf(request.quickPrompts());
        Map<String, String> displayName = request.displayName() == null ? Map.of() : Map.copyOf(request.displayName());
        Map<String, String> profession = request.profession() == null ? Map.of() : Map.copyOf(request.profession());
        String defaultInitPrompt = request.defaultInitPrompt() == null ? "" : request.defaultInitPrompt().trim();

        ExpertDefinition definition = new ExpertDefinition(
                expertId,
                name,
                description,
                expertType,
                promptContent,
                defaultInitPrompt,
                category,
                tags,
                skillCompatibility,
                List.of(),
                null,
                null,
                null,
                null,
                Map.of(),
                displayName,
                profession,
                request.maxTurns(),
                preloadSkills,
                quickPrompts,
                null,
                null);
        return new NormalizedExpertWrite(expertId, promptFile, promptContent, definition);
    }

    private NormalizedSkillWrite normalizeSkillRequest(
            StudioSkillWriteRequest request, String pathSkillId, boolean creating) {
        if (request == null) {
            throw new IllegalArgumentException("Request body required");
        }
        String skillId = creating
                ? OfficeImportValidator.requireSafeId(request.id(), "Skill")
                : OfficeImportValidator.requireSafeId(pathSkillId, "Skill");
        String name = OfficeImportValidator.requireText(request.name(), "Skill name");
        String description = OfficeImportValidator.requireText(request.description(), "Skill description");
        String skillContent = OfficeImportValidator.requireText(request.skillContent(), "Skill content");
        String category = blankToDefault(request.category(), "custom");
        List<String> tags = request.tags() == null ? List.of("draft") : List.copyOf(request.tags());
        String skillFile = request.skillFile() == null || request.skillFile().isBlank()
                ? SkillYamlWriter.defaultSkillFile()
                : OfficeImportValidator.requireSafeFileName(request.skillFile(), "skillFile");
        String source = request.source() == null || request.source().isBlank() ? "draft" : request.source().trim();
        boolean defaultInstalled = request.defaultInstalled() != null && request.defaultInstalled();

        SkillDefinition definition = new SkillDefinition(
                skillId, name, description, category, tags, source, defaultInstalled, skillContent);
        return new NormalizedSkillWrite(skillId, skillFile, definition);
    }

    private static String resolvePromptFile(String promptFile, String expertType) {
        if (promptFile != null && !promptFile.isBlank()) {
            return OfficeImportValidator.requireSafeFileName(promptFile, "promptFile");
        }
        return "team".equalsIgnoreCase(expertType) ? "lead-prompt.md" : "prompt.md";
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record NormalizedExpertWrite(
            String expertId, String promptFile, String promptContent, ExpertDefinition definition) {}

    private record NormalizedSkillWrite(String skillId, String skillFile, SkillDefinition definition) {}
}
