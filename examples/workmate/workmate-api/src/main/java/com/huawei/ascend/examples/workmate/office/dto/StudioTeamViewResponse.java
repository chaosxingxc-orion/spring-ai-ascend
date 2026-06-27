package com.huawei.ascend.examples.workmate.office.dto;

import java.util.List;

public record StudioTeamViewResponse(
        StudioExpertSourceResponse team,
        List<StudioTeamMemberView> members,
        StudioRuntimePreviewResponse runtimePreview,
        List<String> warnings) {}
