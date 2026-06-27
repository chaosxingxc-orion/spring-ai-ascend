package com.huawei.ascend.examples.workmate.team;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class TeamEventEmitter {

    private final AgentRunExecutor agentRunExecutor;
    private final TeamBlackboardService teamBlackboardService;

    public TeamEventEmitter(AgentRunExecutor agentRunExecutor, TeamBlackboardService teamBlackboardService) {
        this.agentRunExecutor = agentRunExecutor;
        this.teamBlackboardService = teamBlackboardService;
    }

    public void emit(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            String eventName,
            Map<String, Object> payload) {
        agentRunExecutor.emit(emitter, clientConnected, parentContext, eventName, payload);
    }

    public void emitMemory(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            String parentRunId,
            TeamBlackboardService.MemoryUpdate update) {
        emit(
                emitter,
                clientConnected,
                parentContext,
                "team.memory",
                teamBlackboardService.memoryPayload(parentRunId, update));
    }
}
