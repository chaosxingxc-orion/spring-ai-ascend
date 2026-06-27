package com.huawei.ascend.examples.workmate.myfiles;

import java.time.Instant;
import java.util.UUID;

public record MyFileEntry(
        UUID sessionId,
        String sessionTitle,
        String path,
        String name,
        String mime,
        long size,
        Instant updatedAt,
        boolean favorite) {}
