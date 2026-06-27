package com.huawei.ascend.examples.workmate.team.agent;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.events.TeamTopic;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Taps openjiuwen team messager topics and forwards them as WorkMate {@code team.*} SSE.
 *
 * <p>Subscribes on the leader {@link TeamBackend} shared messager — not a separate bus instance.</p>
 */
@Component
public class TeamAgentEventForwarder {

    private final AgentRunExecutor agentRunExecutor;

    public TeamAgentEventForwarder(AgentRunExecutor agentRunExecutor) {
        this.agentRunExecutor = agentRunExecutor;
    }

    public MessagerTap attachMessagerTap(
            Messager messager,
            TeamAgentSpec spec,
            String teamSessionId,
            ExpertDefinition team,
            String parentRunId,
            List<TeamMemberDefinition> members,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext) {
        return attachMessagerTap(
                messager,
                spec,
                teamSessionId,
                team,
                parentRunId,
                members,
                emitter,
                clientConnected,
                parentContext,
                true);
    }

    /**
     * @param forwardMemberLifecycle when {@code false}, member lifecycle events are suppressed
     *        because the async {@link com.huawei.ascend.examples.workmate.team.runtime.MemberWorker}
     *        layer is the sole source of truth (avoids premature started/paused from openjiuwen).
     */
    public MessagerTap attachMessagerTap(
            Messager messager,
            TeamAgentSpec spec,
            String teamSessionId,
            ExpertDefinition team,
            String parentRunId,
            List<TeamMemberDefinition> members,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            boolean forwardMemberLifecycle) {
        if (messager == null || spec == null || spec.getTeamName() == null || spec.getTeamName().isBlank()) {
            return MessagerTap.EMPTY;
        }

        List<String> topics = List.of(
                TeamTopic.TEAM.build("shared", spec.getTeamName()),
                TeamTopic.TEAM.build(teamSessionId, spec.getTeamName()));

        MessagerHandler handler = message -> {
            if (!(message instanceof EventMessage eventMessage)) {
                return;
            }
            forwardEvent(
                    eventMessage,
                    team,
                    parentRunId,
                    members,
                    emitter,
                    clientConnected,
                    parentContext,
                    forwardMemberLifecycle);
        };

        for (String topic : topics) {
            messager.subscribe(topic, handler);
        }
        return new MessagerTap(messager, topics);
    }

    private void forwardEvent(
            EventMessage message,
            ExpertDefinition team,
            String parentRunId,
            List<TeamMemberDefinition> members,
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            boolean forwardMemberLifecycle) {
        String eventType = message.getEventType();
        var payload = message.getPayload();
        List<TeamEventSseAdapter.AdaptedTeamEvent> adapted =
                TeamEventSseAdapter.adaptTeamEvents(eventType, payload, team, parentRunId, members);
        for (TeamEventSseAdapter.AdaptedTeamEvent event : adapted) {
            if (!forwardMemberLifecycle && TeamEventSseAdapter.isMemberLifecycleEvent(event.sseEventName())) {
                continue;
            }
            if ("team.build.completed".equals(event.sseEventName())) {
                Map<String, Object> buildPayload = event.payload();
                agentRunExecutor.onTeamBuildCompleted(
                        emitter,
                        clientConnected,
                        parentContext,
                        parentRunId,
                        stringValue(buildPayload, "teamName", "team_name"),
                        stringValue(buildPayload, "displayName", "display_name"),
                        members.size());
            }
            agentRunExecutor.emit(
                    emitter, clientConnected, parentContext, event.sseEventName(), event.payload());
        }
    }

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

    public record MessagerTap(Messager messager, List<String> topics) {

        static final MessagerTap EMPTY = new MessagerTap(null, List.of());

        public void close() {
            if (messager == null) {
                return;
            }
            for (String topic : topics) {
                messager.unsubscribe(topic);
            }
        }
    }
}
