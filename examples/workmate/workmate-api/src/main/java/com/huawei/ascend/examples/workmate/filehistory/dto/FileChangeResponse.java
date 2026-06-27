package com.huawei.ascend.examples.workmate.filehistory.dto;

import com.huawei.ascend.examples.workmate.filehistory.FileVersionEntry;
import java.time.Instant;

public record FileChangeResponse(
        String path,
        String op,
        Instant ts,
        String runId,
        long bytes,
        long seq) {

    public static FileChangeResponse from(FileVersionEntry entry) {
        return new FileChangeResponse(
                entry.path(),
                entry.op().wireValue(),
                entry.ts(),
                entry.runId(),
                entry.bytes(),
                entry.seq());
    }
}
