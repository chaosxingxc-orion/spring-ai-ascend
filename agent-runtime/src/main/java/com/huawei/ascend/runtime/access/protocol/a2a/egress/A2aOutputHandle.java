package com.huawei.ascend.runtime.access.protocol.a2a.egress;

/**
 * Identifies one stream of A2A outputs. Task-scoped (not just tenant+session) so a finished
 * task's terminal output cannot suppress replay for a later task in the same session.
 */
public record A2aOutputHandle(
        String tenantId,
        String sessionId,
        String taskId) {
}


