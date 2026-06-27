package com.huawei.ascend.examples.workmate.session.dto;

public record SessionLimitsResponse(
        int activeCount, int maxActive, boolean autoArchiveOnCreate, int archivableCount) {}
