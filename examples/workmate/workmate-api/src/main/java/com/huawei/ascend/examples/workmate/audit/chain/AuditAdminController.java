package com.huawei.ascend.examples.workmate.audit.chain;

import com.huawei.ascend.examples.workmate.audit.chain.dto.AuditEntryPageResponse;
import com.huawei.ascend.examples.workmate.audit.chain.dto.AuditEntryResponse;
import com.huawei.ascend.examples.workmate.audit.chain.dto.AuditVerifyResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit")
public class AuditAdminController {

    private static final Instant SEARCH_FROM_MIN = Instant.EPOCH;
    private static final Instant SEARCH_TO_MAX = Instant.parse("9999-12-31T23:59:59.999Z");

    private final AuditChainRepository chainRepository;
    private final AuditChainVerifier verifier;
    private final AuditSegmentSealer segmentSealer;
    private final AuditChainProjector projector;

    public AuditAdminController(
            AuditChainRepository chainRepository,
            AuditChainVerifier verifier,
            AuditSegmentSealer segmentSealer,
            AuditChainProjector projector) {
        this.chainRepository = chainRepository;
        this.verifier = verifier;
        this.segmentSealer = segmentSealer;
        this.projector = projector;
    }

    @GetMapping("/entries")
    public AuditEntryPageResponse listEntries(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "50") int limit) {
        projector.projectPending();
        int pageSize = Math.min(Math.max(limit, 1), 200);
        Instant fromInclusive = from != null ? from : SEARCH_FROM_MIN;
        Instant toExclusive = to != null ? to : SEARCH_TO_MAX;
        long afterSeq = cursor != null ? cursor : -1L;
        String query = blankToNull(q);
        List<AuditChainEntry> rows = query == null
                ? chainRepository.search(
                        blankToNull(category),
                        blankToNull(decision),
                        fromInclusive,
                        toExclusive,
                        afterSeq,
                        PageRequest.of(0, pageSize))
                : chainRepository.searchWithQuery(
                        blankToNull(category),
                        blankToNull(decision),
                        fromInclusive,
                        toExclusive,
                        query,
                        afterSeq,
                        PageRequest.of(0, pageSize));
        List<AuditEntryResponse> entries = rows.stream().map(AuditEntryResponse::from).toList();
        Long nextCursor = rows.isEmpty() ? null : rows.getLast().getSeqGlobal();
        return new AuditEntryPageResponse(entries, rows.size() < pageSize ? null : nextCursor);
    }

    @GetMapping("/verify")
    public AuditVerifyResponse verify(
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to) {
        projector.projectPending();
        if (from != null && to != null) {
            return AuditVerifyResponse.from(verifier.verifyRange(from, to));
        }
        return AuditVerifyResponse.from(verifier.verifyAll());
    }

    @GetMapping(value = "/export", produces = "application/x-ndjson")
    public ResponseEntity<String> export(@RequestParam String segment) {
        projector.projectPending();
        LocalDate segmentDate = LocalDate.parse(segment);
        segmentSealer.sealSegment(segmentDate);
        String body = segmentSealer.exportSegmentJsonl(segmentDate);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-" + segment + ".jsonl\"")
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .body(body);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
