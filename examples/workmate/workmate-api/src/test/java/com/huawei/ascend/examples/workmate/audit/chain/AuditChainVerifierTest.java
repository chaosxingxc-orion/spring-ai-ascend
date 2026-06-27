package com.huawei.ascend.examples.workmate.audit.chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.huawei.ascend.examples.workmate.audit.chain.AuditChainVerifier.AuditVerifyResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditChainVerifierTest {

    @Mock
    private AuditChainRepository chainRepository;

    @Test
    void verifiesContinuousChain() {
        UUID sessionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-20T10:00:00Z");
        String payloadHash = AuditHashUtil.payloadHash("{\"ok\":true}");
        String entry1Hash = CanonicalAuditPayload.computeEntryHash(
                AuditHashUtil.GENESIS_HASH, sessionId, "run-1", 1L, "tool.start", payloadHash, createdAt);
        String entry2Hash = CanonicalAuditPayload.computeEntryHash(
                entry1Hash, sessionId, "run-1", 2L, "tool.end", payloadHash, createdAt.plusSeconds(1));

        AuditChainEntry first = entry(1L, sessionId, "tool.start", payloadHash, AuditHashUtil.GENESIS_HASH, entry1Hash, createdAt);
        AuditChainEntry second = entry(2L, sessionId, "tool.end", payloadHash, entry1Hash, entry2Hash, createdAt.plusSeconds(1));
        when(chainRepository.findAll()).thenReturn(List.of(first, second));

        AuditVerifyResult result = new AuditChainVerifier(chainRepository).verifyAll();
        assertThat(result.ok()).isTrue();
        assertThat(result.verifiedThroughSeq()).isEqualTo(2L);
    }

    @Test
    void detectsTamperedEntryHash() {
        UUID sessionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-20T10:00:00Z");
        String payloadHash = AuditHashUtil.payloadHash("{}");
        AuditChainEntry tampered = entry(
                1L, sessionId, "tool.start", payloadHash, AuditHashUtil.GENESIS_HASH, "deadbeef", createdAt);
        when(chainRepository.findAll()).thenReturn(List.of(tampered));

        AuditVerifyResult result = new AuditChainVerifier(chainRepository).verifyAll();
        assertThat(result.ok()).isFalse();
        assertThat(result.brokenSeqGlobal()).isEqualTo(1L);
        assertThat(result.field()).isEqualTo("entry_hash");
    }

    private static AuditChainEntry entry(
            long seqGlobal,
            UUID sessionId,
            String eventName,
            String payloadHash,
            String prevHash,
            String entryHash,
            Instant createdAt) {
        AuditChainEntry row = new AuditChainEntry(
                UUID.randomUUID(),
                sessionId,
                "run-1",
                eventName,
                payloadHash,
                prevHash,
                entryHash,
                "sandbox",
                "info",
                createdAt);
        try {
            var field = AuditChainEntry.class.getDeclaredField("seqGlobal");
            field.setAccessible(true);
            field.set(row, seqGlobal);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return row;
    }
}
