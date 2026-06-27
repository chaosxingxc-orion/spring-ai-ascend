package com.huawei.ascend.examples.workmate.member;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.llm")
public record WorkmateMemberLlmProperties(
        String modelProvider,
        String apiKey,
        String apiBase,
        String modelName,
        boolean sslVerify,
        int maxIterations) {

    public WorkmateMemberLlmProperties {
        if (maxIterations <= 0) {
            maxIterations = 8;
        }
    }
}
