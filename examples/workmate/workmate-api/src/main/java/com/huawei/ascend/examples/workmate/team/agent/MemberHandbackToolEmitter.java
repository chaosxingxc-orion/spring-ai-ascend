package com.huawei.ascend.examples.workmate.team.agent;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.tools.WorkmateToolIds;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Synthesizes member-scoped {@code send_message} tool cards when handback is implicit (remote A2A
 * backends that cannot invoke the local mailbox tool) or ingested via the proactive push API.
 * Local members must call {@code send_message} explicitly so trajectory emits real tool events.
 */
public final class MemberHandbackToolEmitter {

    private static final int PREVIEW_LIMIT = 280;
    static final int MAX_TOOL_CALL_ID_LENGTH = TeamDelegationToolEmitter.MAX_TOOL_CALL_ID_LENGTH;

    private MemberHandbackToolEmitter() {}

    public static void emitImplicitHandback(
            AgentRunExecutor agentRunExecutor,
            SessionPersistenceService sessionPersistenceService,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            UUID sessionId,
            String parentTaskId,
            TeamMemberDefinition member,
            String content,
            String summary,
            String backendKind) {
        if (backendKind == null || !"a2a".equals(backendKind.strip())) {
            return;
        }
        emitRemoteHandback(
                agentRunExecutor,
                sessionPersistenceService,
                emitter,
                clientConnected,
                sessionId,
                parentTaskId,
                member,
                content,
                summary,
                handbackToolCallId(parentTaskId, member.id()),
                "a2a");
    }

    /**
     * Persist + optionally SSE member-scoped {@code send_message} cards for remote handback (push ingest
     * or implicit A2A completion).
     */
    public static void emitRemoteHandback(
            AgentRunExecutor agentRunExecutor,
            SessionPersistenceService sessionPersistenceService,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            UUID sessionId,
            String parentTaskId,
            TeamMemberDefinition member,
            String content,
            String summary,
            String toolCallId,
            String source) {
        if (member == null || sessionId == null || parentTaskId == null || parentTaskId.isBlank()) {
            return;
        }
        if (content == null || content.isBlank()) {
            return;
        }
        if (toolCallId == null || toolCallId.isBlank()) {
            return;
        }
        String subRunId = TeamAgentSessionBinding.subRunId(parentTaskId, member.id());
        RunPersistenceContext memberContext = RunPersistenceContext.forMember(
                sessionId, subRunId, parentTaskId, member.id(), member.name());
        String toolName = WorkmateToolIds.sendMessage(sessionId);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("to", "team-lead");
        args.put("recipient", "team-lead");
        args.put("content", content);
        args.put("message", content);
        if (summary != null && !summary.isBlank()) {
            args.put("summary", summary);
        }
        if (source != null && !source.isBlank()) {
            args.put("handbackSource", source);
        }

        sessionPersistenceService.recordToolStart(memberContext, toolName, args, toolCallId);
        Map<String, Object> startPayload = new LinkedHashMap<>();
        startPayload.put("toolName", toolName);
        startPayload.put("toolCallId", toolCallId);
        startPayload.put("args", args);
        agentRunExecutor.emit(emitter, clientConnected, memberContext, "tool.start", startPayload);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "message");
        data.put("from", member.id());
        data.put("to", "team-lead");
        data.put("recipient", "team-lead");
        if (summary != null && !summary.isBlank()) {
            data.put("summary", summary);
        }
        data.put("resultMessage", "Delivered");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", data);

        sessionPersistenceService.recordToolEnd(memberContext, toolName, result, false);
        Map<String, Object> endPayload = new LinkedHashMap<>();
        endPayload.put("toolName", toolName);
        endPayload.put("toolCallId", toolCallId);
        endPayload.put("status", "success");
        endPayload.put("result", result);
        agentRunExecutor.emit(emitter, clientConnected, memberContext, "tool.end", endPayload);
    }

    static String handbackToolCallId(String parentRunId, String memberId) {
        String id = "mr-" + compactParentRunId(parentRunId) + "-" + memberId;
        return truncateToolCallId(id);
    }

    public static String ingestHandbackToolCallId(String parentRunId, String memberId, int sequence) {
        String id = "mi-" + compactParentRunId(parentRunId) + "-" + memberId + "-" + sequence;
        return truncateToolCallId(id);
    }

    private static String truncateToolCallId(String id) {
        if (id.length() <= MAX_TOOL_CALL_ID_LENGTH) {
            return id;
        }
        return id.substring(0, MAX_TOOL_CALL_ID_LENGTH);
    }

    private static String compactParentRunId(String parentRunId) {
        if (parentRunId == null || parentRunId.isBlank()) {
            return "00000000";
        }
        String compact = parentRunId.replace("-", "");
        return compact.length() <= 8 ? compact : compact.substring(0, 8);
    }

    static String preview(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        if (trimmed.length() <= PREVIEW_LIMIT) {
            return trimmed;
        }
        return trimmed.substring(0, PREVIEW_LIMIT) + "…";
    }
}
