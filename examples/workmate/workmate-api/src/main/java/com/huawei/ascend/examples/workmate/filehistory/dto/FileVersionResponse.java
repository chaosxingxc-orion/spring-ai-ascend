package com.huawei.ascend.examples.workmate.filehistory.dto;

import com.huawei.ascend.examples.workmate.filehistory.FileVersionEntry;
import java.time.Instant;

public record FileVersionResponse(
        long seq,
        String path,
        String op,
        String versionId,
        Instant ts,
        String runId,
        long bytes,
        String sha256) {

    public static FileVersionResponse from(FileVersionEntry entry) {
        return new FileVersionResponse(
                entry.seq(),
                entry.path(),
                entry.op().wireValue(),
                entry.versionId(),
                entry.ts(),
                entry.runId(),
                entry.bytes(),
                entry.sha256());
    }
}
