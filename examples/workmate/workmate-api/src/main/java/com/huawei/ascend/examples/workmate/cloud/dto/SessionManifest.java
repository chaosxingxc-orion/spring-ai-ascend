package com.huawei.ascend.examples.workmate.cloud.dto;

import java.util.Map;

public record SessionManifest(
        String apiVersion,
        String kind,
        SessionManifestMetadata metadata,
        SessionManifestSpec spec) {

    public record SessionManifestMetadata(
            String cloudSessionId, String expertId, String title, String createdAt) {}

    public record SessionManifestSpec(
            String runtimeType,
            RuntimeSpec runtime,
            WorkspaceSpec workspace,
            AgentSpec agent) {}

    public record RuntimeSpec(String image, String sandboxProfile) {}

    public record WorkspaceSpec(String mountPath, String storageClass) {}

    public record AgentSpec(
            String expertId,
            String expertType,
            String permissionMode,
            Map<String, String> displayName) {}
}
