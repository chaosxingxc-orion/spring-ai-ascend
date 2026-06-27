package com.huawei.ascend.examples.workmate.capability;

import java.time.Instant;

public record IdleCapabilityItem(String type, String id, String name, Instant lastUsedAt, long idleDays) {}
