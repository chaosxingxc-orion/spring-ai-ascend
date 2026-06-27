package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.office.dto.ImportValidationResponse;
import com.huawei.ascend.examples.workmate.office.dto.SkillSummaryResponse;
import com.huawei.ascend.examples.workmate.office.dto.SkillUploadRequest;
import com.huawei.ascend.examples.workmate.skill.SkillScanResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillService skillService;
    private final SkillUploadService skillUploadService;

    public SkillController(SkillService skillService, SkillUploadService skillUploadService) {
        this.skillService = skillService;
        this.skillUploadService = skillUploadService;
    }

    @GetMapping
    public List<SkillSummaryResponse> listSkills() {
        return skillService.listSkills();
    }

    @GetMapping("/{id}")
    public SkillSummaryResponse getSkill(@PathVariable String id) {
        return skillService.getSkill(id);
    }

    @GetMapping("/{id}/security-scan")
    public SkillScanResult securityScan(@PathVariable String id) {
        return skillService.scanSkill(id);
    }

    @PostMapping("/{id}/install")
    public SkillSummaryResponse install(@PathVariable String id) {
        return skillService.install(id);
    }

    @PostMapping("/{id}/uninstall")
    public SkillSummaryResponse uninstall(@PathVariable String id) {
        return skillService.uninstall(id);
    }

    @PostMapping("/upload/validate")
    public ImportValidationResponse validateUpload(@RequestBody SkillUploadRequest request) {
        return skillUploadService.validate(request);
    }

    @PostMapping("/upload")
    public SkillSummaryResponse upload(@RequestBody SkillUploadRequest request) {
        return skillUploadService.uploadSkill(request);
    }
}
