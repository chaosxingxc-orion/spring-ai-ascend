package com.huawei.ascend.examples.workmate.tenant.dto;

public record QuotaMetricResponse(
        String key, String label, long used, long limit, int percentUsed, String status) {}
