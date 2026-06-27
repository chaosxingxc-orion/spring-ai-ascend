package com.huawei.ascend.examples.workmate.audit;

import com.huawei.ascend.examples.workmate.chat.RecordedRunEvent;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.config.WorkmateAuditProperties;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditLedgerService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditLedgerService.class);

    private final SessionPersistenceService sessionPersistenceService;
    private final RunEventPayloadRedactor redactor;
    private final AuditDlqService dlqService;
    private final WorkmateAuditProperties auditProperties;

    public AuditLedgerService(
            SessionPersistenceService sessionPersistenceService,
            RunEventPayloadRedactor redactor,
            AuditDlqService dlqService,
            WorkmateAuditProperties auditProperties) {
        this.sessionPersistenceService = sessionPersistenceService;
        this.redactor = redactor;
        this.dlqService = dlqService;
        this.auditProperties = auditProperties;
    }

    /**
     * Persist a redacted audit event. Non-fail-close failures fall back to DLQ; fail-close failures throw.
     */
    public RecordedRunEvent record(RunPersistenceContext context, String eventName, Map<String, Object> payload) {
        if (context == null) {
            return null;
        }
        Map<String, Object> auditPayload = redactor.redact(eventName, payload);
        try {
            return sessionPersistenceService.persistRunEvent(
                    context, eventName, auditPayload);
        } catch (RuntimeException ex) {
            return handlePersistenceFailure(context, eventName, auditPayload, ex);
        }
    }

    /**
     * Fail-close: audit must be written (or DLQ-preserved) before the guarded operation proceeds.
     */
    public void recordFailClose(UUID sessionId, String runId, String eventName, Map<String, Object> payload) {
        if (!auditProperties.failCloseEnabled()) {
            record(RunPersistenceContext.forAudit(sessionId, runId), eventName, payload);
            return;
        }
        if (!AuditFailClosePolicy.isFailClose(eventName)) {
            throw new IllegalArgumentException("Not a fail-close audit event: " + eventName);
        }
        RunPersistenceContext context = RunPersistenceContext.forAudit(sessionId, runId);
        Map<String, Object> auditPayload = redactor.redact(eventName, payload);
        try {
            sessionPersistenceService.persistRunEvent(context, eventName, auditPayload);
        } catch (RuntimeException ex) {
            boolean dlqOk = dlqService.append(sessionId, runId, eventName, auditPayload, ex.getMessage());
            if (!dlqOk) {
                throw new AuditLedgerException(
                        "Fail-close audit could not be persisted or DLQ'd for event " + eventName, ex);
            }
            LOG.warn(
                    "Fail-close audit persisted to DLQ sessionId={} event={}",
                    sessionId,
                    eventName);
        }
    }

    private RecordedRunEvent handlePersistenceFailure(
            RunPersistenceContext context,
            String eventName,
            Map<String, Object> auditPayload,
            RuntimeException ex) {
        boolean failClose = AuditFailClosePolicy.isFailClose(eventName);
        boolean dlqOk = dlqService.append(
                context.sessionId(),
                context.runId(),
                eventName,
                auditPayload,
                ex.getMessage());
        if (failClose && auditProperties.failCloseEnabled()) {
            if (!dlqOk) {
                throw new AuditLedgerException(
                        "Fail-close audit could not be persisted or DLQ'd for event " + eventName, ex);
            }
            LOG.warn("Fail-close audit event {} routed to DLQ", eventName);
            return null;
        }
        if (dlqOk) {
            LOG.warn(
                    "Audit event {} routed to DLQ sessionId={}",
                    eventName,
                    context.sessionId());
        } else {
            LOG.error(
                    "Audit event {} lost (DB + DLQ failed) sessionId={}",
                    eventName,
                    context.sessionId());
        }
        return null;
    }
}
