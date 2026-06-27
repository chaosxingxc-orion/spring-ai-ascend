package com.huawei.ascend.examples.workmate.cloud;

import com.huawei.ascend.examples.workmate.config.WorkmateCloudProperties;
import org.springframework.stereotype.Component;

@Component
public class CloudAccessGuard {

    private final WorkmateCloudProperties properties;

    public CloudAccessGuard(WorkmateCloudProperties properties) {
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public void requireEnabled() {
        if (!properties.enabled()) {
            throw new CloudDisabledException();
        }
    }
}
