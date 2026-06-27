package com.huawei.ascend.examples.workmate.office.dto;

import java.util.List;

public record StudioSkillWriteRequest(
        String id,
        String name,
        String description,
        String category,
        List<String> tags,
        String skillContent,
        String skillFile,
        String source,
        Boolean defaultInstalled) {}
