package com.huawei.ascend.examples.workmate.audit.chain;

import java.time.Instant;
import java.util.UUID;

public final class CanonicalAuditPayload {

    private CanonicalAuditPayload() {
    }

    public static String build(
            UUID sessionId,
            String runId,
            long seqGlobal,
            String eventName,
            String payloadHash,
            Instant createdAt) {
        return "{"
                + "\"sessionId\":\"" + sessionId + "\","
                + "\"runId\":\"" + escape(runId) + "\","
                + "\"seqGlobal\":" + seqGlobal + ","
                + "\"eventName\":\"" + escape(eventName) + "\","
                + "\"payloadHash\":\"" + payloadHash + "\","
                + "\"createdAt\":\"" + createdAt + "\""
                + "}";
    }

    public static String computeEntryHash(
            String prevHash,
            UUID sessionId,
            String runId,
            long seqGlobal,
            String eventName,
            String payloadHash,
            Instant createdAt) {
        return AuditHashUtil.entryHash(prevHash, build(sessionId, runId, seqGlobal, eventName, payloadHash, createdAt));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
