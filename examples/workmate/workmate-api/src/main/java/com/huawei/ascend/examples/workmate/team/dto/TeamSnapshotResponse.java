package com.huawei.ascend.examples.workmate.team.dto;

import java.util.List;
import java.util.Map;

public record TeamSnapshotResponse(
        String teamId,
        String teamName,
        String description,
        String pattern,
        String collaboration,
        String teamPromptSummary,
        TeamLeadSnapshot lead,
        List<MemberSnapshot> members,
        String source) {

    public record TeamLeadSnapshot(String name, String title, String avatar) {}

    public record MemberSnapshot(
            String memberId,
            String name,
            String expertId,
            String role,
            int order,
            String avatar,
            String promptSummary,
            String backendType,
            List<String> subscriptions,
            String status) {}
}
