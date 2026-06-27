package com.huawei.ascend.examples.workmate.audit.chain.dto;

import com.huawei.ascend.examples.workmate.audit.chain.AuditChainEntry;
import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(
        long seqGlobal,
        UUID runEventId,
        UUID sessionId,
        String runId,
        String eventName,
        String payloadHash,
        String prevHash,
        String entryHash,
        String category,
        String decision,
        Instant createdAt) {

    public static AuditEntryResponse from(AuditChainEntry entry) {
        return new AuditEntryResponse(
                entry.getSeqGlobal(),
                entry.getRunEventId(),
                entry.getSessionId(),
                entry.getRunId(),
                entry.getEventName(),
                entry.getPayloadHash(),
                entry.getPrevHash(),
                entry.getEntryHash(),
                entry.getCategory(),
                entry.getDecision(),
                entry.getCreatedAt());
    }
}
