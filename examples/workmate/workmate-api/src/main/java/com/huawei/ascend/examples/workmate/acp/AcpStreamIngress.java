package com.huawei.ascend.examples.workmate.acp;

import com.huawei.ascend.examples.workmate.agent.RunEventBroadcaster;
import com.huawei.ascend.examples.workmate.audit.AuditLedgerService;
import com.huawei.ascend.examples.workmate.chat.RecordedRunEvent;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** W38 Phase 3 — persist external ACP sessionUpdate[] to run_events (ADR-008 fact source). */
@Service
public class AcpStreamIngress {

    private static final String INGEST_RUN_PREFIX = "acp-ingest-";

    private final AcpConverterFacade converterFacade;
    private final AuditLedgerService auditLedgerService;
    private final RunEventBroadcaster runEventBroadcaster;

    public AcpStreamIngress(
            AcpConverterFacade converterFacade,
            AuditLedgerService auditLedgerService,
            RunEventBroadcaster runEventBroadcaster) {
        this.converterFacade = converterFacade;
        this.auditLedgerService = auditLedgerService;
        this.runEventBroadcaster = runEventBroadcaster;
    }

    public List<RecordedRunEvent> ingest(UUID sessionId, List<Map<String, Object>> updates) {
        if (updates == null || updates.isEmpty()) {
            return List.of();
        }
        String runId = INGEST_RUN_PREFIX + UUID.randomUUID();
        RunPersistenceContext context = RunPersistenceContext.forAudit(sessionId, runId);
        List<RecordedRunEvent> recorded = new ArrayList<>();
        for (RunEventDraft draft : converterFacade.fromAcpStream(updates)) {
            RecordedRunEvent event = auditLedgerService.record(context, draft.eventName(), draft.payload());
            if (event != null) {
                recorded.add(event);
                runEventBroadcaster.publish(sessionId, runId, event);
            }
        }
        return recorded;
    }
}
