package com.huawei.ascend.examples.workmate.team.agent;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.openjiuwen.core.session.stream.OutputSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class TeamStreamChunkConsumer {

    private final AgentRunExecutor agentRunExecutor;

    public TeamStreamChunkConsumer(AgentRunExecutor agentRunExecutor) {
        this.agentRunExecutor = agentRunExecutor;
    }

    public boolean consume(
            Object chunk,
            ExpertDefinition team,
            String parentRunId,
            List<TeamMemberDefinition> members,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext) {
        return consume(
                chunk, team, parentRunId, members, emitter, clientConnected, parentContext, false);
    }

    public boolean consume(
            Object chunk,
            ExpertDefinition team,
            String parentRunId,
            List<TeamMemberDefinition> members,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            boolean suppressMemberLifecycle) {
        if (!(chunk instanceof OutputSchema output)) {
            return false;
        }
        Object payload = output.getPayload();
        if (!(payload instanceof Map<?, ?> rawPayload)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> eventPayload = (Map<String, Object>) rawPayload;
        String eventType = stringValue(eventPayload, "event_type");
        if ("team.runtime_ready".equals(eventType)) {
            return false;
        }
        return TeamEventSseAdapter.adaptTeamEvent(eventType, eventPayload, team, parentRunId, members)
                .map(adapted -> {
                    if (suppressMemberLifecycle
                            && TeamEventSseAdapter.isMemberLifecycleEvent(adapted.sseEventName())) {
                        return "team.member.failed".equals(adapted.sseEventName());
                    }
                    if ("team.build.completed".equals(adapted.sseEventName())) {
                        Map<String, Object> buildPayload = adapted.payload();
                        agentRunExecutor.onTeamBuildCompleted(
                                emitter,
                                clientConnected,
                                parentContext,
                                parentRunId,
                                stringValue(buildPayload, "teamName", "team_name"),
                                stringValue(buildPayload, "displayName", "display_name"),
                                intValue(buildPayload, "memberCount", members.size()));
                    }
                    agentRunExecutor.emit(
                            emitter, clientConnected, parentContext, adapted.sseEventName(), adapted.payload());
                    return "team.member.failed".equals(adapted.sseEventName());
                })
                .orElseGet(() -> consumeMessageDelta(
                        eventPayload, emitter, clientConnected, parentContext));
    }

    private boolean consumeMessageDelta(
            Map<String, Object> eventPayload,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext) {
        String delta = stringValue(eventPayload, "delta", "content", "text");
        if (delta.isBlank()) {
            return false;
        }
        String memberId = stringValue(eventPayload, "source_member", "member_name", "member_id");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("delta", delta);
        if (!memberId.isBlank()) {
            payload.put("memberId", memberId);
        } else {
            agentRunExecutor.emitLeaderMessageDelta(emitter, clientConnected, parentContext, payload, delta);
            return false;
        }
        agentRunExecutor.emit(emitter, clientConnected, parentContext, "message.delta", payload);
        return false;
    }

    @SafeVarargs
    private static String stringValue(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) {
                String text = value.toString();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    private static int intValue(Map<String, Object> payload, String key, int fallback) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return fallback;
    }
}
