package com.huawei.ascend.examples.workmate.office.dto;

import java.util.Map;

public record StudioTeamMemberWriteRequest(
        String id,
        String name,
        String expertId,
        String role,
        Integer order,
        String avatar,
        Map<String, String> profession,
        String backend,
        String promptContent) {}
