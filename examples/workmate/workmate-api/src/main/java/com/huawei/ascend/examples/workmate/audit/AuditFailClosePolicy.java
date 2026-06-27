package com.huawei.ascend.examples.workmate.audit;

import java.util.Set;

/**
 * Fail-close audit actions: audit must be persisted (or DLQ-preserved) before the guarded operation runs.
 */
public final class AuditFailClosePolicy {

    public static final String APPROVAL_DECIDED = "approval.decided";
    public static final String MCP_APPROVAL_DECIDED = "mcp.approval.decided";

    private static final Set<String> FAIL_CLOSE_EVENTS = Set.of(APPROVAL_DECIDED, MCP_APPROVAL_DECIDED);

    private AuditFailClosePolicy() {}

    public static boolean isFailClose(String eventName) {
        return eventName != null && FAIL_CLOSE_EVENTS.contains(eventName);
    }
}
