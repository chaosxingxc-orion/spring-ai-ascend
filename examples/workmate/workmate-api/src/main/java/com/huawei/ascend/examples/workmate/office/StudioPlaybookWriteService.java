package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.ImportValidationResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioPlaybookSourceResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioPlaybookWriteRequest;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StudioPlaybookWriteService {

    private final PlaybookRegistry playbookRegistry;
    private final StudioDraftStore draftStore;
    private final StudioDraftCoordinator draftCoordinator;

    public StudioPlaybookWriteService(
            PlaybookRegistry playbookRegistry,
            StudioDraftStore draftStore,
            StudioDraftCoordinator draftCoordinator) {
        this.playbookRegistry = playbookRegistry;
        this.draftStore = draftStore;
        this.draftCoordinator = draftCoordinator;
    }

    public StudioPlaybookSourceResponse getSource(String playbookId) {
        String safeId = OfficeImportValidator.requireSafeId(playbookId, "Playbook");
        PlaybookRegistryEntry entry =
                playbookRegistry.findEntry(safeId).orElseThrow(() -> new PlaybookNotFoundException(safeId));
        return StudioPlaybookSourceResponse.from(entry);
    }

    public StudioPlaybookSourceResponse createPlaybook(StudioPlaybookWriteRequest request) {
        NormalizedPlaybookWrite normalized = normalizeRequest(request, null, true);
        if (playbookRegistry.findEntry(normalized.playbookId()).isPresent()) {
            throw new IllegalArgumentException("Playbook already exists: " + normalized.playbookId());
        }
        draftStore.writePlaybookDraft(normalized.playbookId(), PlaybookYamlWriter.render(normalized.definition()));
        draftCoordinator.commitSaved("playbook", normalized.playbookId(), "blank", this::reloadPlaybooks);
        return getSource(normalized.playbookId());
    }

    public StudioPlaybookSourceResponse updatePlaybook(String playbookId, StudioPlaybookWriteRequest request) {
        NormalizedPlaybookWrite normalized = normalizeRequest(request, playbookId, false);
        if (playbookRegistry.findEntry(normalized.playbookId()).isEmpty()) {
            throw new PlaybookNotFoundException(normalized.playbookId());
        }
        draftStore.writePlaybookDraft(normalized.playbookId(), PlaybookYamlWriter.render(normalized.definition()));
        draftCoordinator.commitSaved(
                "playbook",
                normalized.playbookId(),
                StudioDraftCoordinator.resolveOrigin(
                        playbookRegistry.findBaselineEntry(normalized.playbookId()).isPresent()),
                this::reloadPlaybooks);
        return getSource(normalized.playbookId());
    }

    public ImportValidationResponse validatePlaybook(StudioPlaybookWriteRequest request, String playbookId) {
        return StudioDraftCoordinator.validate(
                () -> normalizeRequest(request, playbookId, playbookId == null));
    }

    public void rollbackPlaybook(String playbookId) {
        String safeId = OfficeImportValidator.requireSafeId(playbookId, "Playbook");
        draftCoordinator.rollbackDraft(
                "playbook",
                safeId,
                draftStore::playbookDraftExists,
                () -> draftStore.deletePlaybookDraft(safeId),
                this::reloadPlaybooks,
                "No playbook draft to rollback: " + safeId);
    }

    private void reloadPlaybooks() {
        playbookRegistry.reloadAll();
    }

    private NormalizedPlaybookWrite normalizeRequest(
            StudioPlaybookWriteRequest request, String pathPlaybookId, boolean creating) {
        if (request == null) {
            throw new IllegalArgumentException("Request body required");
        }
        String playbookId = creating
                ? OfficeImportValidator.requireSafeId(request.id(), "Playbook")
                : OfficeImportValidator.requireSafeId(pathPlaybookId, "Playbook");
        if (!creating && request.id() != null && !request.id().isBlank() && !playbookId.equals(request.id().trim())) {
            throw new IllegalArgumentException("Playbook id cannot be changed");
        }
        String title = requireText(request.title(), "title required");
        String initPrompt = requireText(request.initPrompt(), "initPrompt required");
        List<String> placements = request.placements() == null ? List.of() : List.copyOf(request.placements());
        PlaybookDefinition definition = new PlaybookDefinition(
                playbookId,
                title,
                trimToNull(request.description()),
                trimToNull(request.accent()),
                trimToNull(request.expertId()),
                initPrompt,
                placements);
        playbookRegistry.parsePlaybookYaml(PlaybookYamlWriter.render(definition));
        return new NormalizedPlaybookWrite(playbookId, definition);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record NormalizedPlaybookWrite(String playbookId, PlaybookDefinition definition) {}
}
