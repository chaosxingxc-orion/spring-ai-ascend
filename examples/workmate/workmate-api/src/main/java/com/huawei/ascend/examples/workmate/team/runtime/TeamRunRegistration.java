package com.huawei.ascend.examples.workmate.team.runtime;

import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import java.util.List;
import java.util.UUID;

/** Metadata for an in-flight team run (roster + ids for handback ingest / @member routing). */
public record TeamRunRegistration(UUID sessionId, String parentRunId, List<TeamMemberDefinition> members) {

    public TeamMemberDefinition findMember(String memberId) {
        if (memberId == null || memberId.isBlank() || members == null) {
            return null;
        }
        for (TeamMemberDefinition member : members) {
            if (memberId.equals(member.id())) {
                return member;
            }
        }
        return null;
    }
}
