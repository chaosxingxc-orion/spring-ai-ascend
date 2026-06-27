package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.ImportValidationResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioWelcomeSourceResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioWelcomeWriteRequest;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class StudioWelcomeWriteService {

    private final WelcomeRegistry welcomeRegistry;
    private final StudioDraftStore draftStore;
    private final StudioDraftCoordinator draftCoordinator;

    public StudioWelcomeWriteService(
            WelcomeRegistry welcomeRegistry,
            StudioDraftStore draftStore,
            StudioDraftCoordinator draftCoordinator) {
        this.welcomeRegistry = welcomeRegistry;
        this.draftStore = draftStore;
        this.draftCoordinator = draftCoordinator;
    }

    public StudioWelcomeSourceResponse getSource() {
        String baseline = draftStore.readBuiltinWelcome(welcomeRegistry.officeRoot());
        if (draftStore.welcomeDraftExists()) {
            return new StudioWelcomeSourceResponse(
                    draftStore.readWelcomeDraft(),
                    baseline,
                    OfficeAssetSource.DRAFT,
                    welcomeRegistry.loadedPath().toString());
        }
        Path builtin = welcomeRegistry.officeRoot().resolve("welcome.yaml");
        return new StudioWelcomeSourceResponse(
                baseline, baseline, OfficeAssetSource.BUILTIN, builtin.toString());
    }

    public StudioWelcomeSourceResponse updateWelcome(StudioWelcomeWriteRequest request) {
        String yaml = normalizeYaml(request == null ? null : request.welcomeYaml());
        validateYaml(yaml);
        draftStore.writeWelcomeDraft(yaml);
        draftCoordinator.commitSaved("welcome", "welcome", "override", welcomeRegistry::reloadAll);
        return getSource();
    }

    public ImportValidationResponse validateWelcome(StudioWelcomeWriteRequest request) {
        return StudioDraftCoordinator.validate(
                () -> validateYaml(normalizeYaml(request == null ? null : request.welcomeYaml())));
    }

    public void rollbackWelcome() {
        draftCoordinator.rollbackDraft(
                "welcome",
                "welcome",
                ignored -> draftStore.welcomeDraftExists(),
                draftStore::deleteWelcomeDraft,
                welcomeRegistry::reloadAll,
                "No welcome draft to rollback");
    }

    private void validateYaml(String yaml) {
        welcomeRegistry.parseWelcomeYaml(yaml);
    }

    private static String normalizeYaml(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            throw new IllegalArgumentException("welcome.yaml content required");
        }
        return yaml.endsWith(System.lineSeparator()) ? yaml : yaml + System.lineSeparator();
    }
}
