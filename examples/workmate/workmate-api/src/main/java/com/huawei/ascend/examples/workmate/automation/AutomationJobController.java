package com.huawei.ascend.examples.workmate.automation;

import com.huawei.ascend.examples.workmate.automation.dto.AutomationJobResponse;
import com.huawei.ascend.examples.workmate.automation.dto.CreateAutomationJobRequest;
import com.huawei.ascend.examples.workmate.automation.dto.UpdateAutomationJobRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/automation/jobs")
public class AutomationJobController {

    private final AutomationJobService automationJobService;

    public AutomationJobController(AutomationJobService automationJobService) {
        this.automationJobService = automationJobService;
    }

    @GetMapping
    public List<AutomationJobResponse> listJobs() {
        return automationJobService.listJobs();
    }

    @GetMapping("/{id}")
    public AutomationJobResponse getJob(@PathVariable UUID id) {
        return automationJobService.getJob(id);
    }

    @PostMapping
    public AutomationJobResponse createJob(@Valid @RequestBody CreateAutomationJobRequest request) {
        return automationJobService.createJob(request);
    }

    @PatchMapping("/{id}")
    public AutomationJobResponse updateJob(
            @PathVariable UUID id, @Valid @RequestBody UpdateAutomationJobRequest request) {
        return automationJobService.updateJob(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteJob(@PathVariable UUID id) {
        automationJobService.deleteJob(id);
    }

    @PostMapping("/{id}/run")
    public AutomationJobResponse runNow(@PathVariable UUID id) {
        return automationJobService.runNow(id);
    }
}
