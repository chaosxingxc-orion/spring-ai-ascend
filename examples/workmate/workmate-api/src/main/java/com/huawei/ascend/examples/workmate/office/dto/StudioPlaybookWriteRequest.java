package com.huawei.ascend.examples.workmate.office.dto;

import java.util.List;

public record StudioPlaybookWriteRequest(
        String id,
        String title,
        String description,
        String accent,
        String expertId,
        String initPrompt,
        List<String> placements) {}
