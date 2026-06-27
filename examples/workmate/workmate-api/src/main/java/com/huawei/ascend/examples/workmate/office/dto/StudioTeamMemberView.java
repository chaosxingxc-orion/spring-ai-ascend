package com.huawei.ascend.examples.workmate.office.dto;

import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;

public record StudioTeamMemberView(
        TeamMemberDefinition member,
        boolean expertResolved,
        String expertSource,
        String promptFile,
        String promptContent,
        String expertYaml) {}
