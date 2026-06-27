package com.huawei.ascend.examples.workmate.team.agent;

import com.huawei.ascend.examples.workmate.config.WorkmateLlmProperties;
import com.huawei.ascend.examples.workmate.model.ModelCatalogService;
import com.huawei.ascend.examples.workmate.model.ModelCatalogService.ResolvedModel;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.TeamAgentOverrides;
import com.huawei.ascend.examples.workmate.office.TeamLeadDefinition;
import com.huawei.ascend.examples.workmate.office.TeamMemberBackend;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamLifecycle;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamModelConfig;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.workspace.TeamWorkspaceConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgentConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TeamAgentSpecBuilder {

    private final ExpertRegistry expertRegistry;
    private final ModelCatalogService modelCatalogService;
    private final WorkmateLlmProperties llm;

    public TeamAgentSpecBuilder(
            ExpertRegistry expertRegistry,
            ModelCatalogService modelCatalogService,
            WorkmateLlmProperties llm) {
        this.expertRegistry = expertRegistry;
        this.modelCatalogService = modelCatalogService;
        this.llm = llm;
    }

    public TeamAgentSpec build(WorkmateSession session, ExpertDefinition team, String parentRunId) {
        if (!team.isTeam()) {
            throw new IllegalArgumentException("Expert is not a team: " + team.id());
        }
        List<TeamMemberDefinition> members = team.members();
        if (members.size() < 2) {
            throw new IllegalStateException("Team expert " + team.id() + " requires at least 2 members");
        }

        ResolvedModel model = modelCatalogService.resolve(session.getModelId());
        TeamAgentOverrides overrides = team.teamAgent();

        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName(uniqueTeamName(team.id(), parentRunId));
        spec.setLifecycle(TeamLifecycle.TEMPORARY);
        spec.setSpawnMode(overrides != null ? overrides.resolvedSpawnMode() : "inprocess");
        spec.setTeammateMode(overrides != null ? overrides.resolvedTeammateMode() : "build_mode");
        spec.setTeamMode(overrides != null ? overrides.resolvedTeamMode() : "hybrid");

        LeaderSpec leader = buildLeader(team);
        spec.setLeader(leader);
        spec.getAgents().put("leader", buildDeepAgentSpec(
                team.systemPrompt(),
                "leader",
                "WorkMate team role: " + team.id() + "-leader",
                model,
                team));

        List<TeamMemberSpec> predefinedMembers = new ArrayList<>();
        for (TeamMemberDefinition member : members) {
            assertSupportedBackend(member);
            TeamMemberSpec memberSpec = new TeamMemberSpec();
            memberSpec.setMemberName(member.id());
            memberSpec.setDisplayName(member.name());
            memberSpec.setRoleType(TeamRole.TEAMMATE);
            if (member.role() != null && !member.role().isBlank()) {
                memberSpec.setPersona(member.role());
            }
            predefinedMembers.add(memberSpec);

            ExpertDefinition memberExpert = resolveMemberExpert(member);
            String roleDescription = member.backend() == TeamMemberBackend.EXPERT_REF
                    ? "WorkMate expert_ref: " + member.expertId()
                    : "WorkMate team role: " + member.expertId();
            spec.getAgents().put(
                    member.id(),
                    buildDeepAgentSpec(
                            memberExpert.systemPrompt(),
                            member.id(),
                            roleDescription,
                            model,
                            memberExpert,
                            memberExpert.effectiveMaxTurns()));
        }
        spec.setPredefinedMembers(predefinedMembers);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workmatePattern", team.coordinationPattern());
        metadata.put("workmateTeamId", team.id());
        metadata.put("workmateParentRunId", parentRunId);
        spec.setMetadata(metadata);

        TeamWorkspaceConfig workspace = new TeamWorkspaceConfig();
        workspace.setEnabled(true);
        workspace.setRootPath(session.getWorkspaceRoot());
        spec.setWorkspace(workspace);

        return spec;
    }

    private static void assertSupportedBackend(TeamMemberDefinition member) {
        TeamMemberBackend backend = member.backend();
        if (backend == TeamMemberBackend.LOCAL || backend == TeamMemberBackend.EXPERT_REF) {
            return;
        }
        throw new UnsupportedOperationException(
                "Team member backend not yet supported in W51 Phase 0: " + backend.yamlValue() + " (" + member.id() + ")");
    }

    private ExpertDefinition resolveMemberExpert(TeamMemberDefinition member) {
        return expertRegistry.requireExpert(member.expertId());
    }

    private static LeaderSpec buildLeader(ExpertDefinition team) {
        LeaderSpec leader = new LeaderSpec();
        TeamLeadDefinition lead = team.lead();
        if (lead != null && lead.name() != null && !lead.name().isBlank()) {
            leader.setDisplayName(lead.name());
            String title = lead.resolvedTitle();
            if (title != null && !title.isBlank()) {
                leader.setPersona(title);
            }
        }
        return leader;
    }

    private DeepAgentSpec buildDeepAgentSpec(
            String systemPrompt,
            String roleKey,
            String cardDescription,
            ResolvedModel model,
            ExpertDefinition expert) {
        return buildDeepAgentSpec(systemPrompt, roleKey, cardDescription, model, expert, expert.effectiveMaxTurns());
    }

    private DeepAgentSpec buildDeepAgentSpec(
            String systemPrompt,
            String roleKey,
            String cardDescription,
            ResolvedModel model,
            ExpertDefinition expert,
            Integer maxTurnsOverride) {
        DeepAgentConfig config = new DeepAgentConfig();
        AgentCard card = AgentCard.builder()
                .id(roleKey)
                .name(roleKey)
                .description(cardDescription)
                .build();
        config.setCard(card);
        config.setSystemPrompt(systemPrompt != null ? systemPrompt : "");
        Integer expertMax = maxTurnsOverride != null ? maxTurnsOverride : expert.effectiveMaxTurns();
        config.setMaxIterations(expertMax != null ? expertMax : llm.maxIterations());

        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(model.provider())
                .apiKey(model.apiKey() != null ? model.apiKey() : "")
                .apiBase(model.apiBase() != null ? model.apiBase() : "")
                .verifySsl(model.sslVerify())
                .timeout(llm.requestTimeoutSeconds())
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(model.modelName())
                .build();
        config.setModelClientConfig(clientConfig);
        config.setModelRequestConfig(requestConfig);

        DeepAgentSpec spec = new DeepAgentSpec();
        spec.setConfig(config);
        spec.setModel(new TeamModelConfig(clientConfig, requestConfig));
        return spec;
    }

    private static String uniqueTeamName(String teamId, String parentRunId) {
        String suffix = parentRunId.length() >= 8 ? parentRunId.substring(0, 8) : parentRunId;
        return teamId + "-" + suffix;
    }

}
