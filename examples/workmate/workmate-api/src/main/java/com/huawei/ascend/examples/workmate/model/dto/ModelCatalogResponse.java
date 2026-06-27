package com.huawei.ascend.examples.workmate.model.dto;

import java.util.List;

public record ModelCatalogResponse(
        String defaultModelId,
        List<ModelOption> models,
        List<EffortOption> efforts) {

    public record ModelOption(
            String id,
            String displayName,
            String provider,
            String modelName,
            List<String> capabilities) {}

    public record EffortOption(String id, String label) {}
}
