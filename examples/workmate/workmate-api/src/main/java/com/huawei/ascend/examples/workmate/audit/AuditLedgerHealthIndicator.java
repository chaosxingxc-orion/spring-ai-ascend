package com.huawei.ascend.examples.workmate.audit;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class AuditLedgerHealthIndicator implements HealthIndicator {

    private final AuditDlqService dlqService;

    public AuditLedgerHealthIndicator(AuditDlqService dlqService) {
        this.dlqService = dlqService;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up()
                .withDetail("dlqQueuedEntries", dlqService.queuedEntries())
                .withDetail("dlqDirectory", dlqService.dlqDirectory().toString());
        if (dlqService.lastWriteAt() != null) {
            builder.withDetail("dlqLastWriteAt", dlqService.lastWriteAt().toString());
        }
        if (dlqService.lastWriteFailure() != null) {
            builder.status("DEGRADED").withDetail("dlqLastWriteFailure", dlqService.lastWriteFailure());
        }
        // Full chain verify is exposed at GET /api/v1/admin/audit/verify — too slow for actuator health.
        return builder.build();
    }
}
