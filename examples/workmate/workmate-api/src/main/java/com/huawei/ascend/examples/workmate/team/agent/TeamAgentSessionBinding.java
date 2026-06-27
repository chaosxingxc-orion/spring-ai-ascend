package com.huawei.ascend.examples.workmate.team.agent;

import java.util.UUID;

public final class TeamAgentSessionBinding {

    private TeamAgentSessionBinding() {
    }

    public static String teamSessionId(UUID sessionId) {
        return sessionId.toString();
    }

    public static String subRunId(String parentRunId, String memberId) {
        return parentRunId + ":" + memberId;
    }
}
