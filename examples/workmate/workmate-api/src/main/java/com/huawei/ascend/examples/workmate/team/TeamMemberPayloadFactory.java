package com.huawei.ascend.examples.workmate.team;

import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TeamMemberPayloadFactory {

    private final MemberRunRouter memberRunRouter;

    public TeamMemberPayloadFactory(MemberRunRouter memberRunRouter) {
        this.memberRunRouter = memberRunRouter;
    }

    public Map<String, Object> started(String parentRunId, String subRunId, TeamMemberDefinition member) {
        Map<String, Object> payload = TeamRunPayloads.memberPayload(parentRunId, subRunId, member);
        enrichRemoteMember(payload, member);
        return payload;
    }

    public void enrichRemoteMember(Map<String, Object> payload, TeamMemberDefinition member) {
        if (memberRunRouter.usesRemoteMember(member.expertId())) {
            payload.put("remote", true);
            memberRunRouter.remoteBaseUrl(member.expertId()).ifPresent(url -> payload.put("memberRuntimeUrl", url.toString()));
        }
    }
}
