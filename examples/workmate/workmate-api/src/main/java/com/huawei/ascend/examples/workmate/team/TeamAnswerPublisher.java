package com.huawei.ascend.examples.workmate.team;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class TeamAnswerPublisher {

    private final SessionPersistenceService sessionPersistenceService;
    private final AgentRunExecutor agentRunExecutor;

    public TeamAnswerPublisher(SessionPersistenceService sessionPersistenceService, AgentRunExecutor agentRunExecutor) {
        this.sessionPersistenceService = sessionPersistenceService;
        this.agentRunExecutor = agentRunExecutor;
    }

    public void publish(
            SseEmitter emitter,
            AtomicBoolean clientConnected,
            RunPersistenceContext parentContext,
            String text) {
        sessionPersistenceService.appendAssistantDelta(parentContext, text);
        agentRunExecutor.emit(emitter, clientConnected, parentContext, "message.delta", Map.of("text", text));
    }
}
