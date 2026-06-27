package com.huawei.ascend.examples.workmate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.github")
public record WorkmateGithubProperties(String token, String apiBase) {

    public WorkmateGithubProperties {
        if (apiBase == null || apiBase.isBlank()) {
            apiBase = "https://api.github.com";
        }
    }
}
