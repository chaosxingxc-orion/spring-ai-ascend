package com.huawei.ascend.examples.workmate.cloud;

import com.huawei.ascend.examples.workmate.config.WorkmateCloudProperties;
import com.huawei.ascend.examples.workmate.cloud.dto.SessionManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * W43 MVP — local stub provisioner (path C seam). Real K8s/E2B implementation replaces this in production.
 */
@Component
public class LocalStubSandboxLifecycle implements SandboxLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(LocalStubSandboxLifecycle.class);

    private final WorkmateCloudProperties cloud;

    public LocalStubSandboxLifecycle(WorkmateCloudProperties cloud) {
        this.cloud = cloud;
    }

    @Override
    public SandboxHandle provision(SessionManifest manifest) {
        if (!cloud.enabled()) {
            throw new IllegalStateException("Cloud sessions disabled (workmate.cloud.enabled=false)");
        }
        String sessionId = manifest.metadata().cloudSessionId();
        String sandboxId = "local-stub-" + sessionId;
        String runtimeBaseUrl = cloud.stubRuntimeUrl();
        LOG.info(
                "Provisioned stub sandbox id={} runtimeBaseUrl={} expert={}",
                sandboxId,
                runtimeBaseUrl,
                manifest.metadata().expertId());
        return new SandboxHandle(sandboxId, runtimeBaseUrl);
    }

    @Override
    public void wake(CloudSession session) {
        LOG.info("Wake stub sandbox {}", session.getSandboxId());
    }

    @Override
    public void sleep(CloudSession session) {
        LOG.info("Sleep stub sandbox {}", session.getSandboxId());
    }

    @Override
    public void destroy(CloudSession session) {
        LOG.info("Destroy stub sandbox {}", session.getSandboxId());
    }
}
