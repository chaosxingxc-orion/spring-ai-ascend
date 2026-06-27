package com.huawei.ascend.examples.workmate.myfiles.dto;

import com.huawei.ascend.examples.workmate.myfiles.MyFileEntry;
import java.time.Instant;
import java.util.UUID;

public record MyFileResponse(
        UUID sessionId,
        String sessionTitle,
        String path,
        String name,
        String mime,
        long size,
        Instant updatedAt,
        boolean favorite) {

    public static MyFileResponse from(MyFileEntry entry) {
        return new MyFileResponse(
                entry.sessionId(),
                entry.sessionTitle(),
                entry.path(),
                entry.name(),
                entry.mime(),
                entry.size(),
                entry.updatedAt(),
                entry.favorite());
    }
}
