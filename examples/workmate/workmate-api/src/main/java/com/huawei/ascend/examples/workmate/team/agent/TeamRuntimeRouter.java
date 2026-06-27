package com.huawei.ascend.examples.workmate.team.agent;

import com.huawei.ascend.examples.workmate.config.WorkmateTeamRuntimeProperties;
import com.huawei.ascend.examples.workmate.office.CoordinationSpec;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import org.springframework.stereotype.Component;

@Component
public class TeamRuntimeRouter {

    private final ExpertRegistry expertRegistry;
    private final WorkmateTeamRuntimeProperties properties;

    public TeamRuntimeRouter(ExpertRegistry expertRegistry, WorkmateTeamRuntimeProperties properties) {
        this.expertRegistry = expertRegistry;
        this.properties = properties;
    }

    public boolean isOpenJiuwenTeam(String expertId) {
        return resolveKind(expertId) == TeamRuntimeKind.OPENJIUWEN_TEAM;
    }

    public TeamRuntimeKind resolveKind(String expertId) {
        ExpertDefinition expert = expertRegistry.requireExpert(expertId);
        return resolveKind(expert);
    }

    public TeamRuntimeKind resolveKind(ExpertDefinition expert) {
        if (!expert.isTeam() || !isMigratablePattern(expert)) {
            return TeamRuntimeKind.WORKMATE_ORCHESTRATOR;
        }
        String explicit = expert.resolvedTeamRuntime();
        if (explicit != null) {
            return TeamRuntimeKind.fromYaml(explicit);
        }
        if (properties.getAllowlist().contains(expert.id())) {
            return TeamRuntimeKind.OPENJIUWEN_TEAM;
        }
        if (isEnvAllowlisted(expert.id())) {
            return TeamRuntimeKind.OPENJIUWEN_TEAM;
        }
        if (TeamRuntimeKind.OPENJIUWEN_TEAM.yamlValue().equalsIgnoreCase(properties.getDefaultRuntime())) {
            return TeamRuntimeKind.OPENJIUWEN_TEAM;
        }
        return TeamRuntimeKind.WORKMATE_ORCHESTRATOR;
    }

    private static boolean isMigratablePattern(ExpertDefinition expert) {
        String pattern = expert.coordinationPattern();
        return CoordinationSpec.ORCHESTRATOR.equals(pattern)
                || CoordinationSpec.AGENT_TEAM.equals(pattern);
    }

    private static boolean isEnvAllowlisted(String expertId) {
        String env = System.getenv("WORKMATE_TEAM_RUNTIME_ALLOWLIST");
        if (env == null || env.isBlank()) {
            return false;
        }
        for (String item : env.split(",")) {
            if (expertId.equals(item.trim())) {
                return true;
            }
        }
        return false;
    }
}
