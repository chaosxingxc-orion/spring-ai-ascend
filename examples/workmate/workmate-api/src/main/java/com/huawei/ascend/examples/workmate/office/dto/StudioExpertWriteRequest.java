package com.huawei.ascend.examples.workmate.office.dto;

import java.util.List;
import java.util.Map;

public record StudioExpertWriteRequest(
        String id,
        String name,
        String description,
        String expertType,
        String promptContent,
        String promptFile,
        String defaultInitPrompt,
        String category,
        List<String> tags,
        List<String> skillCompatibility,
        List<String> preloadSkills,
        List<String> quickPrompts,
        Integer maxTurns,
        Map<String, String> displayName,
        Map<String, String> profession) {}
