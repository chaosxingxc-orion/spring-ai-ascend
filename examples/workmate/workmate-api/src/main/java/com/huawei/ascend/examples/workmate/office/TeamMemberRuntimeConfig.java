package com.huawei.ascend.examples.workmate.office;

import java.util.Map;

public record TeamMemberRuntimeConfig(
        String baseUrl,
        String protocol,
        String cliAgent,
        Map<String, String> adapterConfig) {

    public TeamMemberRuntimeConfig {
        if (adapterConfig == null) {
            adapterConfig = Map.of();
        }
    }
}
