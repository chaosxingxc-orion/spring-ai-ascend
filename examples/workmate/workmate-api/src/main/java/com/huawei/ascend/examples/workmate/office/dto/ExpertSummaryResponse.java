package com.huawei.ascend.examples.workmate.office.dto;

import com.huawei.ascend.examples.workmate.office.CoordinationSpec;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.TeamLeadDefinition;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import java.util.List;
import java.util.Map;

public record ExpertSummaryResponse(
        String id,
        String name,
        String description,
        String expertType,
        String defaultInitPrompt,
        String category,
        List<String> tags,
        List<String> skillCompatibility,
        List<String> preloadSkills,
        List<TeamMemberSummary> members,
        String collaboration,
        TeamLeadSummary lead,
        String officeCapability,
        CoordinationSummary coordination,
        Map<String, String> uiLabels,
        Map<String, String> displayName,
        Map<String, String> profession,
        Integer maxTurns,
        List<String> quickPrompts,
        boolean beta,
        String teamRuntime) {

    public record TeamMemberSummary(
            String id,
            String name,
            String expertId,
            String role,
            int order,
            String avatar,
            String participantRole,
            Map<String, String> profession,
            String nickname) {
        public static TeamMemberSummary from(TeamMemberDefinition member) {
            return new TeamMemberSummary(
                    member.id(),
                    member.name(),
                    member.expertId(),
                    member.role(),
                    member.order(),
                    member.avatar(),
                    member.participantRole(),
                    member.profession(),
                    member.nickname());
        }
    }

    public record TeamLeadSummary(String name, Map<String, String> title, String avatar) {
        public static TeamLeadSummary from(TeamLeadDefinition lead) {
            if (lead == null) {
                return null;
            }
            return new TeamLeadSummary(lead.name(), lead.title(), lead.avatar());
        }
    }

    public record CoordinationSummary(String pattern, TerminationSummary termination, String acceptanceCriteria) {
        public static CoordinationSummary from(CoordinationSpec spec) {
            if (spec == null) {
                return null;
            }
            CoordinationSpec.Termination t = spec.termination();
            return new CoordinationSummary(
                    spec.pattern(),
                    t == null ? null : new TerminationSummary(
                            t.maxIterations(), t.timeBudgetMs(), t.convergence(), t.decider()),
                    spec.acceptanceCriteria());
        }
    }

    public record TerminationSummary(Integer maxIterations, Long timeBudgetMs, String convergence, String decider) {}

    public static ExpertSummaryResponse from(ExpertDefinition expert) {
        boolean beta = expert.tags().contains("beta");
        return new ExpertSummaryResponse(
                expert.id(),
                expert.name(),
                expert.description(),
                expert.expertType(),
                expert.defaultInitPrompt(),
                expert.category(),
                expert.tags(),
                expert.skillCompatibility(),
                expert.preloadSkills(),
                expert.members().stream().map(TeamMemberSummary::from).toList(),
                expert.collaboration(),
                TeamLeadSummary.from(expert.lead()),
                expert.officeCapability(),
                CoordinationSummary.from(expert.coordination()),
                expert.uiLabels(),
                expert.displayName(),
                expert.profession(),
                expert.maxTurns(),
                expert.quickPrompts(),
                beta,
                expert.resolvedTeamRuntime());
    }
}
