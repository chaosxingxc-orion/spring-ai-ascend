package com.huawei.ascend.examples.workmate.audit.chain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditSegmentSealer {

    private final AuditChainRepository chainRepository;
    private final AuditSegmentRepository segmentRepository;
    private final ObjectMapper objectMapper;

    public AuditSegmentSealer(
            AuditChainRepository chainRepository,
            AuditSegmentRepository segmentRepository,
            ObjectMapper objectMapper) {
        this.chainRepository = chainRepository;
        this.segmentRepository = segmentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditSegment sealSegment(LocalDate segmentDate) {
        if (segmentRepository.findBySegmentDate(segmentDate).isPresent()) {
            return segmentRepository.findBySegmentDate(segmentDate).orElseThrow();
        }
        List<AuditChainEntry> entries = chainRepository.findBySegmentDate(segmentDate);
        if (entries.isEmpty()) {
            return null;
        }
        StringBuilder jsonl = new StringBuilder();
        for (AuditChainEntry entry : entries) {
            jsonl.append(toJsonLine(entry)).append('\n');
        }
        String fileSha256 = AuditHashUtil.sha256Hex(jsonl.toString());
        AuditSegment segment = new AuditSegment(
                segmentDate,
                entries.size(),
                entries.getFirst().getEntryHash(),
                entries.getLast().getEntryHash(),
                fileSha256,
                Instant.now());
        return segmentRepository.save(segment);
    }

    public String exportSegmentJsonl(LocalDate segmentDate) {
        List<AuditChainEntry> entries = chainRepository.findBySegmentDate(segmentDate);
        StringBuilder jsonl = new StringBuilder();
        for (AuditChainEntry entry : entries) {
            jsonl.append(toJsonLine(entry)).append('\n');
        }
        return jsonl.toString();
    }

    public String recomputeSegmentSha256(LocalDate segmentDate) {
        return AuditHashUtil.sha256Hex(exportSegmentJsonl(segmentDate));
    }

    private String toJsonLine(AuditChainEntry entry) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("seqGlobal", entry.getSeqGlobal());
        row.put("runEventId", entry.getRunEventId().toString());
        row.put("sessionId", entry.getSessionId().toString());
        row.put("runId", entry.getRunId());
        row.put("eventName", entry.getEventName());
        row.put("payloadHash", entry.getPayloadHash());
        row.put("prevHash", entry.getPrevHash());
        row.put("entryHash", entry.getEntryHash());
        row.put("category", entry.getCategory());
        row.put("decision", entry.getDecision());
        row.put("createdAt", entry.getCreatedAt().toString());
        try {
            return objectMapper.writeValueAsString(row);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize audit segment row", ex);
        }
    }
}
