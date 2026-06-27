package com.huawei.ascend.examples.workmate.tools;

import java.util.UUID;

/**
 * Session-scoped OpenJiuwen tool ids. See ADR-005 — avoids global {@code ToolMgr} collisions
 * when multiple runs register workspace tools in the same JVM.
 */
public final class WorkmateToolIds {

    public static final String READ_BASE = "workmate_read";
    public static final String WRITE_BASE = "workmate_write";
    public static final String BASH_BASE = "workmate_bash";
    public static final String TEAM_BUS_PUBLISH_BASE = "workmate_team_bus_publish";
    public static final String ASK_USER_QUESTION_BASE = "workmate_ask_user_question";
    private static final String SEP = "__";

    private WorkmateToolIds() {
    }

    public static String read(UUID sessionId) {
        return read(sessionId.toString());
    }

    public static String write(UUID sessionId) {
        return write(sessionId.toString());
    }

    public static String bash(UUID sessionId) {
        return bash(sessionId.toString());
    }

    public static String teamBusPublish(UUID sessionId) {
        return teamBusPublish(sessionId.toString());
    }

    public static String read(String sessionId) {
        return READ_BASE + SEP + sessionId;
    }

    public static String write(String sessionId) {
        return WRITE_BASE + SEP + sessionId;
    }

    public static String bash(String sessionId) {
        return BASH_BASE + SEP + sessionId;
    }

    public static String teamBusPublish(String sessionId) {
        return TEAM_BUS_PUBLISH_BASE + SEP + sessionId;
    }

    public static String askUserQuestion(UUID sessionId) {
        return askUserQuestion(sessionId.toString());
    }

    public static String askUserQuestion(String sessionId) {
        return ASK_USER_QUESTION_BASE + SEP + sessionId;
    }

    public static String sendMessage(UUID sessionId) {
        return sendMessage(sessionId.toString());
    }

    public static String sendMessage(String sessionId) {
        return "send_message" + SEP + sessionId;
    }

    /** Per-member handback tool id so concurrent team members do not clobber one shared instance. */
    public static String sendMessage(String sessionId, String memberId) {
        return "send_message" + SEP + sessionId + SEP + memberId;
    }

    public static String sendMessage(UUID sessionId, String memberId) {
        return sendMessage(sessionId.toString(), memberId);
    }

    public static boolean isSendMessage(String toolName) {
        return toolName != null && toolName.startsWith("send_message" + SEP);
    }

    public static boolean isRead(String toolName) {
        return toolName != null && toolName.startsWith(READ_BASE + SEP);
    }

    public static boolean isWrite(String toolName) {
        return toolName != null && toolName.startsWith(WRITE_BASE + SEP);
    }

    public static boolean isBash(String toolName) {
        return toolName != null && toolName.startsWith(BASH_BASE + SEP);
    }

    public static boolean isTeamBusPublish(String toolName) {
        return toolName != null && toolName.startsWith(TEAM_BUS_PUBLISH_BASE + SEP);
    }

    public static boolean isAskUserQuestion(String toolName) {
        return toolName != null && toolName.startsWith(ASK_USER_QUESTION_BASE + SEP);
    }

    public static boolean isWorkspaceTool(String toolName) {
        return isRead(toolName) || isWrite(toolName) || isBash(toolName) || isTeamBusPublish(toolName)
                || isAskUserQuestion(toolName);
    }
}
