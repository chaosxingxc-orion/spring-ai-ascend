package com.huawei.ascend.examples.workmate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workmate.cloud")
public record WorkmateCloudProperties(
        boolean enabled,
        String stubRuntimeUrl,
        String defaultImage,
        String sandboxProfile,
        String workspaceMountPath,
        boolean routePrompts,
        int requestTimeoutSeconds) {

    public WorkmateCloudProperties {
        if (stubRuntimeUrl == null || stubRuntimeUrl.isBlank()) {
            stubRuntimeUrl = "http://localhost:8080";
        }
        if (defaultImage == null || defaultImage.isBlank()) {
            defaultImage = "workmate-agent-runtime:latest";
        }
        if (sandboxProfile == null || sandboxProfile.isBlank()) {
            sandboxProfile = "local-stub";
        }
        if (workspaceMountPath == null || workspaceMountPath.isBlank()) {
            workspaceMountPath = "/workspace";
        }
        if (requestTimeoutSeconds <= 0) {
            requestTimeoutSeconds = 300;
        }
    }
}
