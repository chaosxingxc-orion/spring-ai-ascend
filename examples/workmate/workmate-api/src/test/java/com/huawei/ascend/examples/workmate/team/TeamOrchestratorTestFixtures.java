package com.huawei.ascend.examples.workmate.team;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor;
import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;

public final class TeamOrchestratorTestFixtures {

    private TeamOrchestratorTestFixtures() {
    }

    public record TeamRunSupport(
            TeamEventEmitter eventEmitter,
            TeamAnswerPublisher answerPublisher,
            TeamMemberPayloadFactory memberPayloadFactory) {}

    public static TeamRunSupport support(
            AgentRunExecutor agentRunExecutor,
            SessionPersistenceService sessionPersistenceService,
            TeamBlackboardService teamBlackboardService,
            MemberRunRouter memberRunRouter) {
        return new TeamRunSupport(
                new TeamEventEmitter(agentRunExecutor, teamBlackboardService),
                new TeamAnswerPublisher(sessionPersistenceService, agentRunExecutor),
                new TeamMemberPayloadFactory(memberRunRouter));
    }
}
