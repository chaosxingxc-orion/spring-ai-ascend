package com.huawei.ascend.examples.workmate.office.dto;

import java.util.List;

public record ExpertImportRequest(
        String id,
        String name,
        String description,
        String expertType,
        String category,
        List<String> tags,
        String promptContent,
        String defaultInitPrompt) {}
