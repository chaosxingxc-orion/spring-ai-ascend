package com.huawei.ascend.service.platform.web;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HealthResponse(
        String status,
        String sha,
        @JsonProperty("db_ping_ns") long dbPingNs,
        String ts) {
}
