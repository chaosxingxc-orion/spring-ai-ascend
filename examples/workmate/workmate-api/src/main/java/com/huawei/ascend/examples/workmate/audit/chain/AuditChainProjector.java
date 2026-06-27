package com.huawei.ascend.examples.workmate.audit.chain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.examples.workmate.chat.RunEvent;
import com.huawei.ascend.examples.workmate.chat.RunEventRepository;
import com.huawei.ascend.examples.workmate.config.WorkmateAuditChainProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuditChainProjector {

    private static final Logger LOG = LoggerFactory.getLogger(AuditChainProjector.class);

    private final RunEventRepository runEventRepository;
    private final AuditChainRepository chainRepository;
    private final AuditChainStateRepository stateRepository;
    private final AuditSegmentSealer segmentSealer;
    private final ObjectMapper objectMapper;
    private final WorkmateAuditChainProperties properties;
    private final TransactionTemplate projectTx;

    @PersistenceContext
    private EntityManager entityManager;

    public AuditChainProjector(
            RunEventRepository runEventRepository,
            AuditChainRepository chainRepository,
            AuditChainStateRepository stateRepository,
            AuditSegmentSealer segmentSealer,
            ObjectMapper objectMapper,
            WorkmateAuditChainProperties properties,
            PlatformTransactionManager transactionManager) {
        this.runEventRepository = runEventRepository;
        this.chainRepository = chainRepository;
        this.stateRepository = stateRepository;
        this.segmentSealer = segmentSealer;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.projectTx = new TransactionTemplate(transactionManager);
        this.projectTx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Scheduled(fixedDelayString = "${workmate.audit-chain.projector-fixed-delay-ms:5000}")
    public void scheduledProject() {
        try {
            projectPending();
        } catch (Exception ex) {
            LOG.warn("Audit chain projection failed: {}", ex.getMessage());
        }
    }

    public synchronized int projectPending() {
        Integer projected = projectTx.execute(status -> projectPendingInTransaction());
        return projected != null ? projected : 0;
    }

    private int projectPendingInTransaction() {
        acquireProjectorLock();
        List<RunEvent> pending =
                runEventRepository.findUnprojected(PageRequest.of(0, properties.projectorBatchSize()));
        if (pending.isEmpty()) {
            return 0;
        }

        LocalDate currentSegment = stateRepository.findById((short) 1).orElseThrow().getCurrentSegmentDate();
        int projected = 0;

        for (RunEvent event : pending) {
            AuditChainState state = stateRepository.findById((short) 1).orElseThrow();
            String prevHash = state.getLastHash();
            long lastSeq = state.getLastSeqGlobal();
            LocalDate eventDate = event.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            if (currentSegment != null && eventDate.isAfter(currentSegment)) {
                segmentSealer.sealSegment(currentSegment);
                currentSegment = eventDate;
            } else if (currentSegment == null) {
                currentSegment = eventDate;
            }

            Map<String, Object> payload = readPayload(event.getPayloadJson());
            AuditCategoryMapper.Classification classification =
                    AuditCategoryMapper.classifyWithPayload(event.getEventName(), payload);
            String payloadHash = AuditHashUtil.payloadHash(event.getPayloadJson());
            long nextSeq = lastSeq + 1;
            String entryHash = CanonicalAuditPayload.computeEntryHash(
                    prevHash,
                    event.getSessionId(),
                    event.getRunId(),
                    nextSeq,
                    event.getEventName(),
                    payloadHash,
                    event.getCreatedAt());

            AuditChainEntry entry = new AuditChainEntry(
                    event.getId(),
                    event.getSessionId(),
                    event.getRunId(),
                    event.getEventName(),
                    payloadHash,
                    prevHash,
                    entryHash,
                    classification.category(),
                    classification.decision(),
                    event.getCreatedAt());
            chainRepository.save(entry);
            entityManager.flush();
            entityManager.refresh(entry);

            prevHash = entryHash;
            lastSeq = entry.getSeqGlobal();
            state.setLastSeqGlobal(lastSeq);
            state.setLastHash(prevHash);
            state.setLastRunEventCreatedAt(event.getCreatedAt());
            state.setCurrentSegmentDate(currentSegment);
            stateRepository.save(state);
            entityManager.flush();
            projected += 1;
        }

        return projected;
    }

    private void acquireProjectorLock() {
        try {
            entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(731031)").getSingleResult();
        } catch (RuntimeException ex) {
            LOG.debug("Skipping pg_advisory_xact_lock (non-Postgres runtime): {}", ex.getMessage());
        }
    }

    private Map<String, Object> readPayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
