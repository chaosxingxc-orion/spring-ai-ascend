package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class OfficeImportPaths {

    private final Path importsRoot;
    private final Path draftsRoot;
    private final Path expertsDir;
    private final Path skillsDir;
    private final Path expertsDraftsDir;
    private final Path skillsDraftsDir;
    private final Path playbooksDraftsDir;
    private final Path draftsMetaDir;

    public OfficeImportPaths(WorkmateDataProperties dataProperties) {
        Path dataRoot = dataProperties.resolvedPath();
        this.importsRoot = dataRoot.resolve("office-imports");
        this.draftsRoot = dataRoot.resolve("office-drafts");
        this.expertsDir = importsRoot.resolve("experts");
        this.skillsDir = importsRoot.resolve("skills");
        this.expertsDraftsDir = draftsRoot.resolve("experts");
        this.skillsDraftsDir = draftsRoot.resolve("skills");
        this.playbooksDraftsDir = draftsRoot.resolve("playbooks");
        this.draftsMetaDir = draftsRoot.resolve(".meta");
    }

    public Path importsRoot() {
        return importsRoot;
    }

    public Path draftsRoot() {
        return draftsRoot;
    }

    public Path expertsDir() {
        return expertsDir;
    }

    public Path skillsDir() {
        return skillsDir;
    }

    public Path expertsDraftsDir() {
        return expertsDraftsDir;
    }

    public Path skillsDraftsDir() {
        return skillsDraftsDir;
    }

    public Path playbooksDraftsDir() {
        return playbooksDraftsDir;
    }

    public Path draftsMetaDir() {
        return draftsMetaDir;
    }

    public Path expertDir(String expertId) {
        return expertsDir.resolve(expertId);
    }

    public Path skillDir(String skillId) {
        return skillsDir.resolve(skillId);
    }

    public Path expertDraftDir(String expertId) {
        return expertsDraftsDir.resolve(expertId);
    }

    public Path skillDraftDir(String skillId) {
        return skillsDraftsDir.resolve(skillId);
    }

    public Path draftMetaFile(String assetId) {
        return draftsMetaDir.resolve(assetId + ".json");
    }

    public Path welcomeDraftFile() {
        return draftsRoot.resolve("welcome.yaml");
    }

    public Path welcomeBuiltinFile(Path officeRoot) {
        return officeRoot.resolve("welcome.yaml");
    }

    public Path playbookDraftFile(String playbookId) {
        return playbooksDraftsDir.resolve(playbookId + ".yaml");
    }

    public Path playbookBuiltinFile(Path officeRoot, String playbookId) {
        return officeRoot.resolve("playbooks").resolve(playbookId + ".yaml");
    }
}
