package com.huawei.ascend.examples.workmate.runtime;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class MemberRuntimeHealthIndicator implements HealthIndicator {

    private final ConfiguredMemberRuntimeLifecycle lifecycle;
    private final com.huawei.ascend.examples.workmate.config.WorkmateMemberRuntimeProperties properties;

    public MemberRuntimeHealthIndicator(
            ConfiguredMemberRuntimeLifecycle lifecycle,
            com.huawei.ascend.examples.workmate.config.WorkmateMemberRuntimeProperties properties) {
        this.lifecycle = lifecycle;
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.up().withDetail("enabled", false).build();
        }
        lifecycle.refreshHealth();
        var snapshot = lifecycle.memberHealthSnapshot();
        boolean allHealthy = snapshot.values().stream().allMatch(Boolean::booleanValue);
        Health.Builder builder = allHealthy ? Health.up() : Health.outOfService();
        return builder.withDetail("enabled", true).withDetail("members", snapshot).build();
    }
}
