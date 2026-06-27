package com.huawei.ascend.examples.workmate.agent;

import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import java.util.UUID;

/**
 * Resolves OpenJiuwen {@code conversation_id} (agent state key) for WorkMate runs.
 *
 * <p>Single-agent sessions use a stable per-session key so multi-turn resume works.
 * Team member sub-runs and team-lead synthesis use per-run keys so ReAct trajectories do
 * not leak across members (ADR-013 §6.3).
 *
 * <p>G2: after conversation edit/retry, {@code conversationGeneration} bumps and the key
 * becomes {@code sessionId + ":g" + generation} so OpenJiuwen forgets superseded turns.
 */
final class AgentConversationKey {

    private AgentConversationKey() {}

    static String resolve(
            UUID sessionId,
            String taskId,
            RunPersistenceContext persistenceContext,
            boolean teamExpertInvocation,
            int conversationGeneration) {
        if (shouldIsolate(persistenceContext, teamExpertInvocation)) {
            return sessionId + ":" + taskId;
        }
        if (conversationGeneration > 0) {
            return sessionId + ":g" + conversationGeneration;
        }
        return sessionId.toString();
    }

    private static boolean shouldIsolate(RunPersistenceContext persistenceContext, boolean teamExpertInvocation) {
        if (persistenceContext != null && persistenceContext.memberId() != null) {
            return true;
        }
        if (persistenceContext != null && persistenceContext.parentRunId() != null) {
            return true;
        }
        return teamExpertInvocation;
    }
}
