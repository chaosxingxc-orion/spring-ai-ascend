package com.huawei.ascend.examples.workmate.tenant;

import com.huawei.ascend.examples.workmate.config.WorkmateDataProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateSessionProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateTenantProperties;
import com.huawei.ascend.examples.workmate.session.WorkmateSessionRepository;
import com.huawei.ascend.examples.workmate.session.WorkspaceService;
import com.huawei.ascend.examples.workmate.storage.StorageProbeService;
import com.huawei.ascend.examples.workmate.tenant.dto.QuotaAlertResponse;
import com.huawei.ascend.examples.workmate.tenant.dto.QuotaMetricResponse;
import com.huawei.ascend.examples.workmate.tenant.dto.TenantQuotaResponse;
import com.huawei.ascend.examples.workmate.usage.SessionUsageRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantQuotaService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final WorkmateTenantProperties tenantProperties;
    private final WorkmateSessionProperties sessionProperties;
    private final WorkmateDataProperties dataProperties;
    private final WorkspaceService workspaceService;
    private final WorkmateSessionRepository sessionRepository;
    private final SessionUsageRepository sessionUsageRepository;
    private final StorageProbeService storageProbeService;

    public TenantQuotaService(
            WorkmateTenantProperties tenantProperties,
            WorkmateSessionProperties sessionProperties,
            WorkmateDataProperties dataProperties,
            WorkspaceService workspaceService,
            WorkmateSessionRepository sessionRepository,
            SessionUsageRepository sessionUsageRepository,
            StorageProbeService storageProbeService) {
        this.tenantProperties = tenantProperties;
        this.sessionProperties = sessionProperties;
        this.dataProperties = dataProperties;
        this.workspaceService = workspaceService;
        this.sessionRepository = sessionRepository;
        this.sessionUsageRepository = sessionUsageRepository;
        this.storageProbeService = storageProbeService;
    }

    @Transactional(readOnly = true)
    public TenantQuotaResponse currentQuota() {
        var quota = tenantProperties.quota();
        int warnThreshold = quota.warnThresholdPercent();

        int maxActive = quota.maxActiveSessions() > 0 ? quota.maxActiveSessions() : sessionProperties.maxActive();
        int activeCount = (int) sessionRepository.countByArchivedAtIsNull();

        long monthlyTokens = sessionUsageRepository.sumTotalTokensSince(monthStart());
        long maxMonthlyTokens = quota.maxMonthlyTokens();

        long storageBytes =
                storageProbeService.directorySizeBytes(dataProperties.resolvedPath())
                        + storageProbeService.directorySizeBytes(workspaceService.basePath());
        long maxStorageBytes = quota.maxStorageBytes();

        List<QuotaMetricResponse> metrics = List.of(
                metric("activeSessions", "活跃会话", activeCount, maxActive, warnThreshold),
                metric("monthlyTokens", "本月 Token", monthlyTokens, maxMonthlyTokens, warnThreshold),
                metric("storageBytes", "存储占用", storageBytes, maxStorageBytes, warnThreshold));

        List<QuotaAlertResponse> alerts = new ArrayList<>();
        for (QuotaMetricResponse item : metrics) {
            if ("exceeded".equals(item.status())) {
                alerts.add(new QuotaAlertResponse("exceeded", item.key(), item.label() + " 已超限"));
            } else if ("warn".equals(item.status())) {
                alerts.add(new QuotaAlertResponse("warn", item.key(), item.label() + " 接近上限"));
            }
        }

        YearMonth period = YearMonth.now(ZONE);
        return new TenantQuotaResponse(tenantProperties.id(), period.toString(), metrics, alerts);
    }

    public void assertWithinTokenQuota() {
        var quota = tenantProperties.quota();
        if (quota.maxMonthlyTokens() <= 0) {
            return;
        }
        long used = sessionUsageRepository.sumTotalTokensSince(monthStart());
        if (used >= quota.maxMonthlyTokens()) {
            throw new QuotaExceededException(
                    "monthlyTokens", "Monthly token quota exceeded: " + used + "/" + quota.maxMonthlyTokens());
        }
    }

    private static QuotaMetricResponse metric(
            String key, String label, long used, long limit, int warnThreshold) {
        if (limit <= 0) {
            return new QuotaMetricResponse(key, label, used, 0L, -1, "ok");
        }
        int percent = (int) Math.min(100L, used * 100L / limit);
        String status = "ok";
        if (used >= limit) {
            status = "exceeded";
        } else if (percent >= warnThreshold) {
            status = "warn";
        }
        return new QuotaMetricResponse(key, label, used, limit, percent, status);
    }

    private static Instant monthStart() {
        return YearMonth.now(ZONE).atDay(1).atStartOfDay(ZONE).toInstant();
    }
}
