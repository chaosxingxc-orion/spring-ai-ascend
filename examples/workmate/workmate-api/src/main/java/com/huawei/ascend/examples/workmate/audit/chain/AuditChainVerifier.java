package com.huawei.ascend.examples.workmate.audit.chain;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AuditChainVerifier {

    private final AuditChainRepository chainRepository;

    public AuditChainVerifier(AuditChainRepository chainRepository) {
        this.chainRepository = chainRepository;
    }

    public AuditVerifyResult verifyAll() {
        List<AuditChainEntry> entries = chainRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(a.getSeqGlobal(), b.getSeqGlobal()))
                .toList();
        return verifyEntries(entries);
    }

    public AuditVerifyResult verifyRange(long fromSeq, long toSeq) {
        List<AuditChainEntry> entries = chainRepository.findAll().stream()
                .filter(entry -> entry.getSeqGlobal() >= fromSeq && entry.getSeqGlobal() <= toSeq)
                .sorted((a, b) -> Long.compare(a.getSeqGlobal(), b.getSeqGlobal()))
                .toList();
        return verifyEntries(entries);
    }

    private AuditVerifyResult verifyEntries(List<AuditChainEntry> entries) {
        if (entries.isEmpty()) {
            return AuditVerifyResult.ok(0);
        }
        String prevHash = AuditHashUtil.GENESIS_HASH;
        for (AuditChainEntry entry : entries) {
            if (!prevHash.equals(entry.getPrevHash())) {
                return AuditVerifyResult.broken(
                        entry.getSeqGlobal(),
                        "prev_hash",
                        prevHash,
                        entry.getPrevHash());
            }
            String expected = CanonicalAuditPayload.computeEntryHash(
                    entry.getPrevHash(),
                    entry.getSessionId(),
                    entry.getRunId(),
                    entry.getSeqGlobal(),
                    entry.getEventName(),
                    entry.getPayloadHash(),
                    entry.getCreatedAt());
            if (!expected.equals(entry.getEntryHash())) {
                return AuditVerifyResult.broken(
                        entry.getSeqGlobal(),
                        "entry_hash",
                        expected,
                        entry.getEntryHash());
            }
            prevHash = entry.getEntryHash();
        }
        return AuditVerifyResult.ok(entries.getLast().getSeqGlobal());
    }

    public record AuditVerifyResult(
            boolean ok,
            long verifiedThroughSeq,
            Long brokenSeqGlobal,
            String field,
            String expected,
            String actual) {

        public static AuditVerifyResult ok(long verifiedThroughSeq) {
            return new AuditVerifyResult(true, verifiedThroughSeq, null, null, null, null);
        }

        public static AuditVerifyResult broken(
                long brokenSeqGlobal, String field, String expected, String actual) {
            return new AuditVerifyResult(false, 0, brokenSeqGlobal, field, expected, actual);
        }
    }
}
