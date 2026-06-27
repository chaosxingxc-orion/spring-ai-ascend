package com.huawei.ascend.examples.workmate.session;

import com.huawei.ascend.examples.workmate.session.dto.WorkspacePresetResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public List<WorkspacePresetResponse> listWorkspaces() {
        return workspaceService.presets().stream().map(WorkspacePresetResponse::from).toList();
    }
}
