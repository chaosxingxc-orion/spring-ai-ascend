package com.huawei.ascend.examples.workmate.filehistory;

import java.time.Instant;

public record FileVersionEntry(
        long seq,
        String path,
        FileVersionOp op,
        String versionId,
        Instant ts,
        String runId,
        long bytes,
        String sha256) {
}
