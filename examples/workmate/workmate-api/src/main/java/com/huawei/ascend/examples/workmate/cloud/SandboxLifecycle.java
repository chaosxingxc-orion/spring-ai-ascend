package com.huawei.ascend.examples.workmate.cloud;

import com.huawei.ascend.examples.workmate.cloud.dto.SessionManifest;

public interface SandboxLifecycle {

    SandboxHandle provision(SessionManifest manifest);

    void wake(CloudSession session);

    void sleep(CloudSession session);

    void destroy(CloudSession session);
}
