package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.config.WorkmateStudioProperties;
import org.springframework.stereotype.Component;

@Component
public class StudioAccessGuard {

    private final WorkmateStudioProperties properties;

    public StudioAccessGuard(WorkmateStudioProperties properties) {
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public void requireEnabled() {
        if (!properties.enabled()) {
            throw new StudioDisabledException();
        }
    }
}
