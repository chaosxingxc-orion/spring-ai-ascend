package com.huawei.ascend.examples.workmate.office;

import com.huawei.ascend.examples.workmate.audit.AuditLedgerService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.config.WorkmateStudioProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StudioAuditService {

    static final UUID STUDIO_AUDIT_SESSION_ID = UUID.fromString("00000000-0000-4000-8000-000000000052");

    private final AuditLedgerService auditLedgerService;
    private final WorkmateStudioProperties properties;

    public StudioAuditService(AuditLedgerService auditLedgerService, WorkmateStudioProperties properties) {
        this.auditLedgerService = auditLedgerService;
        this.properties = properties;
    }

    public void record(String eventName, String assetType, String assetId, Map<String, Object> extra) {
        if (!properties.auditEnabled()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("assetType", assetType);
        payload.put("assetId", assetId);
        if (extra != null) {
            payload.putAll(extra);
        }
        auditLedgerService.record(
                RunPersistenceContext.forAudit(STUDIO_AUDIT_SESSION_ID, "studio"), eventName, payload);
    }

    public void draftSaved(String assetType, String assetId, String origin) {
        record("studio.draft.saved", assetType, assetId, Map.of("origin", origin == null ? "blank" : origin));
    }

    public void draftPublished(String assetType, String assetId) {
        record("studio.draft.published", assetType, assetId, Map.of());
    }

    public void draftRollback(String assetType, String assetId) {
        record("studio.draft.rollback", assetType, assetId, Map.of());
    }

    public void reload(int experts, int skills) {
        record("studio.reload", "studio", "reload", Map.of("experts", experts, "skills", skills));
    }
}
