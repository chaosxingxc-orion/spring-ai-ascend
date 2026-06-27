package com.huawei.ascend.examples.workmate.runtime;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Probes configured member-runtime Agent Cards on startup (W21). */
@Component
public class MemberRuntimeBootstrap implements ApplicationRunner {

    private final ConfiguredMemberRuntimeLifecycle lifecycle;
    private final com.huawei.ascend.examples.workmate.config.WorkmateMemberRuntimeProperties properties;

    public MemberRuntimeBootstrap(
            ConfiguredMemberRuntimeLifecycle lifecycle,
            com.huawei.ascend.examples.workmate.config.WorkmateMemberRuntimeProperties properties) {
        this.lifecycle = lifecycle;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        lifecycle.refreshHealth();
    }
}
