package com.huawei.ascend.examples.workmate.tenant.dto;

import java.util.List;

public record TenantQuotaResponse(
        String tenantId, String period, List<QuotaMetricResponse> metrics, List<QuotaAlertResponse> alerts) {}
