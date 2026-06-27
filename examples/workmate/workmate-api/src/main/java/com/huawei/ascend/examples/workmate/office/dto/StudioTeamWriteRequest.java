package com.huawei.ascend.examples.workmate.office.dto;

import java.util.List;

public record StudioTeamWriteRequest(
        String id,
        String name,
        String description,
        String promptContent,
        String defaultInitPrompt,
        String category,
        List<String> tags,
        String collaboration,
        String teamRuntime,
        StudioCoordinationWriteRequest coordination,
        StudioLeadWriteRequest lead,
        StudioTeamAgentWriteRequest teamAgent,
        List<StudioTeamMemberWriteRequest> members,
        Integer maxTurns) {}
