package com.huawei.ascend.examples.workmate.team.agent;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Synthesizes {@code tool.start/end} for openjiuwen team tools (the reference workbench TeamCreate / SendMessage cards). */
public final class TeamDelegationToolEmitter {

    private static final String BUILD_TOOL = "team.build_team";
    private static final String SEND_TOOL = "team.send_message";
    /**
     * {@code session_messages.id} is VARCHAR(64); delegation rows use {@code delegation-{toolCallId}}.
     */
    static final int MAX_TOOL_CALL_ID_LENGTH = 53;

    private TeamDelegationToolEmitter() {
    }

    public static void emitBuildTeam(
            AgentRunExecutor agentRunExecutor,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            String parentRunId,
            String teamName,
            String displayName,
            int memberCount) {
        String toolCallId = buildTeamToolCallId(parentRunId);
        Map<String, Object> args = new LinkedHashMap<>();
        if (teamName != null && !teamName.isBlank()) {
            args.put("team_name", teamName);
            args.put("teamName", teamName);
        }
        if (displayName != null && !displayName.isBlank()) {
            args.put("display_name", displayName);
            args.put("displayName", displayName);
        }
        args.put("memberCount", memberCount);
        emitToolStart(agentRunExecutor, emitter, clientConnected, parentContext, BUILD_TOOL, toolCallId, args);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", args);
        emitToolEnd(agentRunExecutor, emitter, clientConnected, parentContext, BUILD_TOOL, toolCallId, result);
    }

    private static final int PREVIEW_LIMIT = 280;

    public static String emitSendMessageStart(
            AgentRunExecutor agentRunExecutor,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            String parentRunId,
            TeamMemberDefinition member,
            int sequence,
            String message,
            String description) {
        return emitSendMessageStart(
                agentRunExecutor,
                emitter,
                clientConnected,
                parentContext,
                parentRunId,
                member,
                sequence,
                message,
                description,
                false);
    }

    public static String emitSendMessageStart(
            AgentRunExecutor agentRunExecutor,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            String parentRunId,
            TeamMemberDefinition member,
            int sequence,
            String message,
            String description,
            boolean reawaken) {
        String toolCallId = sendMessageToolCallId(parentRunId, member.id(), sequence);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("memberId", member.id());
        args.put("to", member.id());
        args.put("memberName", member.name());
        if (reawaken) {
            args.put("reawaken", true);
        }
        args.put("iteration", sequence);
        if (description != null && !description.isBlank()) {
            args.put("description", description);
        }
        if (message != null && !message.isBlank()) {
            args.put("message", message);
            args.put("messagePreview", preview(message));
        }
        emitToolStart(agentRunExecutor, emitter, clientConnected, parentContext, SEND_TOOL, toolCallId, args);
        return toolCallId;
    }

    public static void emitSendMessageEnd(
            AgentRunExecutor agentRunExecutor,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            String toolCallId,
            TeamMemberDefinition member,
            boolean success,
            String error,
            String summary) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("memberId", member.id());
        data.put("memberName", member.name());
        if (summary != null && !summary.isBlank()) {
            data.put("summary", summary);
            data.put("summaryPreview", preview(summary));
        }
        if (!success && error != null) {
            data.put("error", error);
        }
        result.put("data", data);
        emitToolEnd(agentRunExecutor, emitter, clientConnected, parentContext, SEND_TOOL, toolCallId, result);
    }

    private static String preview(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        if (trimmed.length() <= PREVIEW_LIMIT) {
            return trimmed;
        }
        return trimmed.substring(0, PREVIEW_LIMIT) + "…";
    }

    static String buildTeamToolCallId(String parentRunId) {
        return "tb-" + compactParentRunId(parentRunId);
    }

    static String sendMessageToolCallId(String parentRunId, String memberId, int sequence) {
        String id = "ts-" + compactParentRunId(parentRunId) + "-" + memberId + "-" + sequence;
        if (id.length() <= MAX_TOOL_CALL_ID_LENGTH) {
            return id;
        }
        // Extremely long member ids: keep parent prefix + stable hash suffix.
        int hash = (memberId + "#" + sequence).hashCode();
        return "ts-" + compactParentRunId(parentRunId) + "-" + Integer.toHexString(hash);
    }

    private static String compactParentRunId(String parentRunId) {
        if (parentRunId == null || parentRunId.isBlank()) {
            return "00000000";
        }
        String compact = parentRunId.replace("-", "");
        return compact.length() <= 8 ? compact : compact.substring(0, 8);
    }

    private static void emitToolStart(
            AgentRunExecutor agentRunExecutor,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            String toolName,
            String toolCallId,
            Map<String, Object> args) {
        agentRunExecutor.emitLeaderDelegationToolStart(
                emitter, clientConnected, parentContext, toolName, toolCallId, args);
    }

    private static void emitToolEnd(
            AgentRunExecutor agentRunExecutor,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            String toolName,
            String toolCallId,
            Map<String, Object> result) {
        agentRunExecutor.emitLeaderDelegationToolEnd(
                emitter, clientConnected, parentContext, toolName, toolCallId, result);
    }
}
