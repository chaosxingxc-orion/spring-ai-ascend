package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.ImportValidationResponse;
import com.huawei.ascend.examples.workmate.config.WorkmateStudioProperties;
import com.huawei.ascend.examples.workmate.office.dto.StudioConfigResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioDraftMetaResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioRuntimeResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioDryRunResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioExportPreviewResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioPlaybookListItemResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioPlaybookSourceResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioPlaybookWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioWelcomeSourceResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioWelcomeWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioExpertCapabilitiesResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioExpertListItemResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioExpertSourceResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioExpertWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioReloadResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioSkillFileContentResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioSkillFileEntryResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioSkillListItemResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioSkillSourceResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioSkillWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioAssetDiffResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioCoordinationWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioLeadWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioRuntimePreviewResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioTeamMemberWriteRequest;
import com.huawei.ascend.examples.workmate.office.dto.StudioTeamViewResponse;
import com.huawei.ascend.examples.workmate.office.dto.StudioTeamWriteRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/studio")
public class StudioController {

    private final StudioService studioService;
    private final StudioWriteService studioWriteService;
    private final StudioTeamWriteService studioTeamWriteService;
    private final StudioDiffService studioDiffService;
    private final StudioExportService studioExportService;
    private final StudioWelcomeWriteService studioWelcomeWriteService;
    private final StudioPlaybookWriteService studioPlaybookWriteService;
    private final StudioPublishService studioPublishService;
    private final StudioRuntimeService studioRuntimeService;
    private final StudioSkillFileService studioSkillFileService;
    private final StudioExpertCapabilitiesService studioExpertCapabilitiesService;
    private final StudioDraftMetaStore draftMetaStore;
    private final StudioAccessGuard accessGuard;
    private final WorkmateStudioProperties studioProperties;

    public StudioController(
            StudioService studioService,
            StudioWriteService studioWriteService,
            StudioTeamWriteService studioTeamWriteService,
            StudioDiffService studioDiffService,
            StudioExportService studioExportService,
            StudioWelcomeWriteService studioWelcomeWriteService,
            StudioPlaybookWriteService studioPlaybookWriteService,
            StudioPublishService studioPublishService,
            StudioRuntimeService studioRuntimeService,
            StudioSkillFileService studioSkillFileService,
            StudioExpertCapabilitiesService studioExpertCapabilitiesService,
            StudioDraftMetaStore draftMetaStore,
            StudioAccessGuard accessGuard,
            WorkmateStudioProperties studioProperties) {
        this.studioService = studioService;
        this.studioWriteService = studioWriteService;
        this.studioTeamWriteService = studioTeamWriteService;
        this.studioDiffService = studioDiffService;
        this.studioExportService = studioExportService;
        this.studioWelcomeWriteService = studioWelcomeWriteService;
        this.studioPlaybookWriteService = studioPlaybookWriteService;
        this.studioPublishService = studioPublishService;
        this.studioRuntimeService = studioRuntimeService;
        this.studioSkillFileService = studioSkillFileService;
        this.studioExpertCapabilitiesService = studioExpertCapabilitiesService;
        this.draftMetaStore = draftMetaStore;
        this.accessGuard = accessGuard;
        this.studioProperties = studioProperties;
    }

    @GetMapping("/config")
    public StudioConfigResponse config() {
        return new StudioConfigResponse(studioProperties.enabled(), studioProperties.auditEnabled());
    }

    @GetMapping("/runtime")
    public StudioRuntimeResponse runtime() {
        requireStudio();
        return studioRuntimeService.runtimeOverview();
    }

    @GetMapping("/draft-meta/{assetType}/{assetId}")
    public StudioDraftMetaResponse draftMeta(@PathVariable String assetType, @PathVariable String assetId) {
        requireStudio();
        return draftMetaStore
                .read(assetType, assetId)
                .map(StudioDraftMetaResponse::from)
                .orElse(new StudioDraftMetaResponse(assetType, assetId, StudioDraftMeta.STATUS_DRAFT, "blank", null));
    }

    @PostMapping("/reload")
    public StudioReloadResponse reload() {
        requireStudio();
        return studioService.reload();
    }

    @GetMapping("/export/preview")
    public StudioExportPreviewResponse exportPreview() {
        return studioExportService.previewExport();
    }

    @GetMapping(value = "/export/all", produces = "application/zip")
    public ResponseEntity<byte[]> exportAllDrafts() {
        return zipResponse("workmate-office-drafts.zip", studioExportService.exportAllDraftsZip());
    }

    @GetMapping(value = "/experts/{id}/export", produces = "application/zip")
    public ResponseEntity<byte[]> exportExpert(@PathVariable String id) {
        return zipResponse(id + "-office-export.zip", studioExportService.exportExpertZip(id));
    }

    @GetMapping(value = "/skills/{id}/export", produces = "application/zip")
    public ResponseEntity<byte[]> exportSkill(@PathVariable String id) {
        return zipResponse(id + "-office-export.zip", studioExportService.exportSkillZip(id));
    }

    @GetMapping("/welcome/source")
    public StudioWelcomeSourceResponse getWelcomeSource() {
        return studioWelcomeWriteService.getSource();
    }

    @PutMapping("/welcome")
    public StudioWelcomeSourceResponse updateWelcome(@RequestBody StudioWelcomeWriteRequest request) {
        return studioWelcomeWriteService.updateWelcome(request);
    }

    @PostMapping("/welcome/validate")
    public ImportValidationResponse validateWelcome(@RequestBody StudioWelcomeWriteRequest request) {
        return studioWelcomeWriteService.validateWelcome(request);
    }

    @GetMapping("/welcome/diff")
    public StudioAssetDiffResponse getWelcomeDiff() {
        return studioDiffService.getWelcomeDiff();
    }

    @PostMapping("/welcome/rollback")
    public void rollbackWelcome() {
        studioWelcomeWriteService.rollbackWelcome();
    }

    @GetMapping(value = "/welcome/export", produces = "application/zip")
    public ResponseEntity<byte[]> exportWelcome() {
        return zipResponse("welcome-office-export.zip", studioExportService.exportWelcomeZip());
    }

    @GetMapping("/playbooks")
    public List<StudioPlaybookListItemResponse> listPlaybooks() {
        return studioService.listPlaybooks();
    }

    @GetMapping("/playbooks/{id}/source")
    public StudioPlaybookSourceResponse getPlaybookSource(@PathVariable String id) {
        return studioService.getPlaybookSource(id);
    }

    @PostMapping("/playbooks")
    public StudioPlaybookSourceResponse createPlaybook(@RequestBody StudioPlaybookWriteRequest request) {
        return studioPlaybookWriteService.createPlaybook(request);
    }

    @PutMapping("/playbooks/{id}")
    public StudioPlaybookSourceResponse updatePlaybook(
            @PathVariable String id, @RequestBody StudioPlaybookWriteRequest request) {
        return studioPlaybookWriteService.updatePlaybook(id, request);
    }

    @PostMapping("/playbooks/validate")
    public ImportValidationResponse validatePlaybook(@RequestBody StudioPlaybookWriteRequest request) {
        return studioPlaybookWriteService.validatePlaybook(request, resolveValidatePathId(request.id()));
    }

    @GetMapping("/playbooks/{id}/diff")
    public StudioAssetDiffResponse getPlaybookDiff(@PathVariable String id) {
        return studioDiffService.getPlaybookDiff(id);
    }

    @PostMapping("/playbooks/{id}/rollback")
    public void rollbackPlaybook(@PathVariable String id) {
        studioPlaybookWriteService.rollbackPlaybook(id);
    }

    @GetMapping(value = "/playbooks/{id}/export", produces = "application/zip")
    public ResponseEntity<byte[]> exportPlaybook(@PathVariable String id) {
        return zipResponse(id + "-office-export.zip", studioExportService.exportPlaybookZip(id));
    }

    @PostMapping("/experts/{id}/publish")
    public StudioDraftMetaResponse publishExpert(@PathVariable String id) {
        requireStudio();
        return studioPublishService.publishExpert(id);
    }

    @PostMapping("/skills/{id}/publish")
    public StudioDraftMetaResponse publishSkill(@PathVariable String id) {
        requireStudio();
        return studioPublishService.publishSkill(id);
    }

    @PostMapping("/playbooks/{id}/publish")
    public StudioDraftMetaResponse publishPlaybook(@PathVariable String id) {
        requireStudio();
        return studioPublishService.publishPlaybook(id);
    }

    @PostMapping("/welcome/publish")
    public StudioDraftMetaResponse publishWelcome() {
        requireStudio();
        return studioPublishService.publishWelcome();
    }

    private void requireStudio() {
        accessGuard.requireEnabled();
    }

    private static ResponseEntity<byte[]> zipResponse(String filename, byte[] body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    @GetMapping("/experts")
    public List<StudioExpertListItemResponse> listExperts() {
        return studioService.listExperts();
    }

    @GetMapping("/experts/{id}/source")
    public StudioExpertSourceResponse getExpertSource(@PathVariable String id) {
        return studioService.getExpertSource(id);
    }

    @GetMapping("/experts/{id}/capabilities")
    public StudioExpertCapabilitiesResponse getExpertCapabilities(@PathVariable String id) {
        requireStudio();
        return studioExpertCapabilitiesService.resolve(id);
    }

    @PostMapping("/experts")
    public StudioExpertSourceResponse createExpert(@RequestBody StudioExpertWriteRequest request) {
        return studioWriteService.createExpert(request);
    }

    @PutMapping("/experts/{id}")
    public StudioExpertSourceResponse updateExpert(
            @PathVariable String id, @RequestBody StudioExpertWriteRequest request) {
        return studioWriteService.updateExpert(id, request);
    }

    @DeleteMapping("/experts/{id}")
    public void deleteExpert(@PathVariable String id) {
        studioWriteService.deleteExpert(id);
    }

    @PostMapping("/experts/{id}/fork")
    public StudioExpertSourceResponse forkExpert(@PathVariable String id) {
        return studioWriteService.forkExpert(id);
    }

    @PostMapping("/experts/validate")
    public ImportValidationResponse validateExpert(@RequestBody StudioExpertWriteRequest request) {
        return studioWriteService.validateExpert(request, resolveValidatePathId(request.id()));
    }

    @PostMapping("/experts/{id}/dry-run")
    public StudioDryRunResponse dryRunExpert(@PathVariable String id) {
        return studioWriteService.dryRunExpert(id);
    }

    @PostMapping("/experts/import/zip")
    public StudioExpertSourceResponse importExpertZip(@RequestPart("file") MultipartFile file) throws java.io.IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Zip file required");
        }
        return studioWriteService.importExpertZip(file.getInputStream());
    }

    @GetMapping("/experts/{id}/diff")
    public StudioAssetDiffResponse getExpertDiff(@PathVariable String id) {
        return studioDiffService.getExpertDiff(id);
    }

    @PostMapping("/experts/{id}/rollback")
    public void rollbackExpert(@PathVariable String id) {
        studioWriteService.deleteExpert(id);
    }

    @GetMapping("/skills")
    public List<StudioSkillListItemResponse> listSkills() {
        return studioService.listSkills();
    }

    @GetMapping("/skills/{id}/files")
    public List<StudioSkillFileEntryResponse> listSkillFiles(@PathVariable String id) {
        requireStudio();
        return studioSkillFileService.listFiles(id);
    }

    @GetMapping("/skills/{id}/files/content")
    public StudioSkillFileContentResponse getSkillFileContent(
            @PathVariable String id, @RequestParam("path") String path) {
        requireStudio();
        return studioSkillFileService.readFile(id, path);
    }

    @GetMapping("/skills/{id}/source")
    public StudioSkillSourceResponse getSkillSource(@PathVariable String id) {
        return studioService.getSkillSource(id);
    }

    @PostMapping("/skills")
    public StudioSkillSourceResponse createSkill(@RequestBody StudioSkillWriteRequest request) {
        return studioWriteService.createSkill(request);
    }

    @PutMapping("/skills/{id}")
    public StudioSkillSourceResponse updateSkill(@PathVariable String id, @RequestBody StudioSkillWriteRequest request) {
        return studioWriteService.updateSkill(id, request);
    }

    @DeleteMapping("/skills/{id}")
    public void deleteSkill(@PathVariable String id) {
        studioWriteService.deleteSkill(id);
    }

    @PostMapping("/skills/validate")
    public ImportValidationResponse validateSkill(@RequestBody StudioSkillWriteRequest request) {
        return studioWriteService.validateSkill(request, resolveValidatePathId(request.id()));
    }

    @GetMapping("/skills/{id}/diff")
    public StudioAssetDiffResponse getSkillDiff(@PathVariable String id) {
        return studioDiffService.getSkillDiff(id);
    }

    @PostMapping("/skills/{id}/rollback")
    public void rollbackSkill(@PathVariable String id) {
        studioWriteService.deleteSkill(id);
    }

    @GetMapping("/teams/{id}")
    public StudioTeamViewResponse getTeam(@PathVariable String id) {
        return studioTeamWriteService.getTeam(id);
    }

    @PostMapping("/teams")
    public StudioTeamViewResponse createTeam(@RequestBody StudioTeamWriteRequest request) {
        return studioTeamWriteService.createTeam(request);
    }

    @PutMapping("/teams/{id}")
    public StudioTeamViewResponse updateTeam(@PathVariable String id, @RequestBody StudioTeamWriteRequest request) {
        return studioTeamWriteService.updateTeam(id, request);
    }

    @PostMapping("/teams/validate")
    public ImportValidationResponse validateTeam(@RequestBody StudioTeamWriteRequest request) {
        return studioTeamWriteService.validateTeam(request, resolveValidatePathId(request.id()));
    }

    @GetMapping("/teams/{id}/runtime-preview")
    public StudioRuntimePreviewResponse previewTeamRuntime(@PathVariable String id) {
        return studioTeamWriteService.previewRuntimeForTeam(id);
    }

    @PostMapping("/teams/{id}/runtime-preview")
    public StudioRuntimePreviewResponse previewTeamRuntimeDraft(
            @PathVariable String id, @RequestBody StudioTeamWriteRequest request) {
        return studioTeamWriteService.previewRuntime(request, id);
    }

    @PutMapping("/teams/{id}/coordination")
    public StudioTeamViewResponse updateTeamCoordination(
            @PathVariable String id, @RequestBody StudioCoordinationWriteRequest coordination) {
        return studioTeamWriteService.updateCoordination(id, coordination);
    }

    @PutMapping("/teams/{id}/runtime")
    public StudioTeamViewResponse updateTeamRuntime(@PathVariable String id, @RequestBody MapBody body) {
        return studioTeamWriteService.updateRuntime(id, body.runtime());
    }

    @PutMapping("/teams/{id}/lead")
    public StudioTeamViewResponse updateTeamLead(@PathVariable String id, @RequestBody StudioLeadUpdateRequest body) {
        return studioTeamWriteService.updateLead(id, body.lead(), body.promptContent());
    }

    @PostMapping("/teams/{id}/members")
    public StudioTeamViewResponse addTeamMember(
            @PathVariable String id, @RequestBody StudioTeamMemberWriteRequest member) {
        return studioTeamWriteService.addMember(id, member);
    }

    @PutMapping("/teams/{id}/members/{memberId}")
    public StudioTeamViewResponse updateTeamMember(
            @PathVariable String id,
            @PathVariable String memberId,
            @RequestBody StudioTeamMemberWriteRequest member) {
        return studioTeamWriteService.updateMember(id, memberId, member);
    }

    @DeleteMapping("/teams/{id}/members/{memberId}")
    public StudioTeamViewResponse deleteTeamMember(@PathVariable String id, @PathVariable String memberId) {
        return studioTeamWriteService.deleteMember(id, memberId);
    }

    @GetMapping("/teams/{id}/diff")
    public StudioAssetDiffResponse getTeamDiff(@PathVariable String id) {
        return studioDiffService.getExpertDiff(id);
    }

    @PostMapping("/teams/{id}/rollback")
    public void rollbackTeam(@PathVariable String id) {
        studioWriteService.deleteExpert(id);
    }

    public record MapBody(String runtime) {}

    public record StudioLeadUpdateRequest(StudioLeadWriteRequest lead, String promptContent) {}

    private static String resolveValidatePathId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return id.trim();
    }
}
