package com.huawei.ascend.examples.workmate.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** In-memory state for an active agent run while messages and events are persisted. */
public final class RunPersistenceContext {

    private final UUID sessionId;
    private final String runId;
    /** Id of the currently open assistant turn; null when no turn is open (closed on each tool). */
    private String assistantMessageId;
    private final String parentRunId;
    private final String memberId;
    private final String memberName;
    /** Full-run assistant text (used for plan detection and run outcome / member summary). */
    private final StringBuilder assistantText = new StringBuilder();
    /** Text of the currently open assistant turn (what is persisted into the open message). */
    private final StringBuilder currentTurnText = new StringBuilder();
    private final Map<String, Deque<String>> runningToolStacks = new LinkedHashMap<>();

    RunPersistenceContext(
            UUID sessionId,
            String runId,
            String assistantMessageId,
            String parentRunId,
            String memberId,
            String memberName) {
        this.sessionId = sessionId;
        this.runId = runId;
        this.assistantMessageId = assistantMessageId;
        this.parentRunId = parentRunId;
        this.memberId = memberId;
        this.memberName = memberName;
    }

    public static RunPersistenceContext forMember(
            UUID sessionId, String subRunId, String parentRunId, String memberId) {
        return forMember(sessionId, subRunId, parentRunId, memberId, null);
    }

    public static RunPersistenceContext forMember(
            UUID sessionId,
            String subRunId,
            String parentRunId,
            String memberId,
            String memberName) {
        return new RunPersistenceContext(sessionId, subRunId, null, parentRunId, memberId, memberName);
    }

    public static RunPersistenceContext forAudit(UUID sessionId, String runId) {
        return new RunPersistenceContext(sessionId, runId, null, null, null, null);
    }

    public UUID sessionId() {
        return sessionId;
    }

    public String runId() {
        return runId;
    }

    public String assistantMessageId() {
        return assistantMessageId;
    }

    public String parentRunId() {
        return parentRunId;
    }

    public String memberId() {
        return memberId;
    }

    public String memberName() {
        return memberName;
    }

    public String assistantText() {
        return assistantText.toString();
    }

    void appendAssistantText(String text) {
        assistantText.append(text);
    }

    /** Open a new assistant turn with the given message id and reset the per-turn text. */
    public void beginAssistantTurn(String messageId) {
        this.assistantMessageId = messageId;
        this.currentTurnText.setLength(0);
    }

    /** Close the current assistant turn so the next delta opens a fresh message. */
    public void closeAssistantTurn() {
        this.assistantMessageId = null;
        this.currentTurnText.setLength(0);
    }

    void appendCurrentTurnText(String text) {
        currentTurnText.append(text);
    }

    public String currentTurnText() {
        return currentTurnText.toString();
    }

    void pushRunningTool(String toolName, String toolCallId) {
        runningToolStacks.computeIfAbsent(toolName, ignored -> new ArrayDeque<>()).push(toolCallId);
    }

    String popRunningTool(String toolName) {
        Deque<String> stack = runningToolStacks.get(toolName);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String toolCallId = stack.pop();
        if (stack.isEmpty()) {
            runningToolStacks.remove(toolName);
        }
        return toolCallId;
    }

    static boolean isTeamSurface(RunPersistenceContext context) {
        return context != null && context.memberId() != null && context.parentRunId() != null;
    }
}
