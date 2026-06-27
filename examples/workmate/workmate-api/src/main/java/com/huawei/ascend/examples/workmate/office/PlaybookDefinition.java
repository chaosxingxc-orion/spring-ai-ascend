package com.huawei.ascend.examples.workmate.office;

import java.util.List;

public record PlaybookDefinition(
        String id,
        String title,
        String description,
        String accent,
        String expertId,
        String initPrompt,
        List<String> placements) {}
