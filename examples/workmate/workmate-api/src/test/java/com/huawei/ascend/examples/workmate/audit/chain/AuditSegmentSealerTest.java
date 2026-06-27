package com.huawei.ascend.examples.workmate.audit.chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditSegmentSealerTest {

    @Mock
    private AuditChainRepository chainRepository;

    @Mock
    private AuditSegmentRepository segmentRepository;

    @Test
    void recomputesSegmentSha256FromJsonl() {
        AuditChainEntry entry = new AuditChainEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "run-1",
                "approval.decided",
                AuditHashUtil.payloadHash("{\"decision\":\"approve\"}"),
                AuditHashUtil.GENESIS_HASH,
                "abc123",
                "approval",
                "approved",
                Instant.parse("2026-06-20T12:00:00Z"));
        setSeq(entry, 1L);
        LocalDate date = LocalDate.of(2026, 6, 20);
        when(chainRepository.findBySegmentDate(date)).thenReturn(List.of(entry));

        AuditSegmentSealer sealer = new AuditSegmentSealer(chainRepository, segmentRepository, new ObjectMapper());
        String first = sealer.recomputeSegmentSha256(date);
        String second = sealer.recomputeSegmentSha256(date);
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);
    }

    private static void setSeq(AuditChainEntry entry, long seqGlobal) {
        try {
            var field = AuditChainEntry.class.getDeclaredField("seqGlobal");
            field.setAccessible(true);
            field.set(entry, seqGlobal);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
