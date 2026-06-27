package com.huawei.ascend.examples.workmate.office.dto;

import java.util.List;

public record SkillUploadRequest(
        String id,
        String name,
        String description,
        String category,
        List<String> tags,
        String skillContent,
        boolean install) {}
