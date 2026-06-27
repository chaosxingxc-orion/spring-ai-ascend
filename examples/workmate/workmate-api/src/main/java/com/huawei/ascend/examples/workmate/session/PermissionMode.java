package com.huawei.ascend.examples.workmate.session;

/**
 * Session-level agent permission mode (Ask / Plan / Craft).
 */
public enum PermissionMode {
    /** Read-only: workspace read + MCP; no write/bash. */
    ASK,
    /** Plan-first: same tool surface as ASK; prompt instructs plan before execution. */
    PLAN,
    /** Full craft: read, write, bash with HITL on high-risk bash. */
    CRAFT;

    public boolean allowsWrite() {
        return this == CRAFT;
    }

    public boolean allowsBash() {
        return this == CRAFT;
    }

    public boolean requiresApprovalGate() {
        return this == CRAFT;
    }
}
