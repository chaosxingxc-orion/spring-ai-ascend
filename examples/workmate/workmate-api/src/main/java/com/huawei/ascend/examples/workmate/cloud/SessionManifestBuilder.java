package com.huawei.ascend.examples.workmate.cloud;

import com.huawei.ascend.examples.workmate.cloud.dto.SessionManifest;
import com.huawei.ascend.examples.workmate.config.WorkmateCloudProperties;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertService;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SessionManifestBuilder {

    private static final String API_VERSION = "workmate.huawei/v1";
    private static final String KIND = "SessionManifest";

    private final WorkmateCloudProperties cloud;
    private final ExpertService expertService;

    public SessionManifestBuilder(WorkmateCloudProperties cloud, ExpertService expertService) {
        this.cloud = cloud;
        this.expertService = expertService;
    }

    public SessionManifest build(
            UUID cloudSessionId, String expertId, String title, PermissionMode permissionMode) {
        ExpertDefinition expert = expertService.requireExpertDefinition(expertId);
        String resolvedTitle = title != null && !title.isBlank() ? title.trim() : expert.name();
        return new SessionManifest(
                API_VERSION,
                KIND,
                new SessionManifest.SessionManifestMetadata(
                        cloudSessionId.toString(),
                        expertId,
                        resolvedTitle,
                        Instant.now().toString()),
                new SessionManifest.SessionManifestSpec(
                        "cloud",
                        new SessionManifest.RuntimeSpec(cloud.defaultImage(), cloud.sandboxProfile()),
                        new SessionManifest.WorkspaceSpec(cloud.workspaceMountPath(), "standard"),
                        new SessionManifest.AgentSpec(
                                expertId,
                                expert.expertType(),
                                permissionMode.name(),
                                expert.displayName() != null ? expert.displayName() : Map.of())));
    }
}
