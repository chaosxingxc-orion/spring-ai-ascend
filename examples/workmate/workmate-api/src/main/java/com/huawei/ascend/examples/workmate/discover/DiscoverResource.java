package com.huawei.ascend.examples.workmate.discover;

import java.time.Instant;

public record DiscoverResource(
        String type, String id, String title, String subtitle, Instant lastUsedAt, boolean favorite) {}
