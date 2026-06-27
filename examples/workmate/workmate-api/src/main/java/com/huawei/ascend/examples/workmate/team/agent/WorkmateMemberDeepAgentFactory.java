package com.huawei.ascend.examples.workmate.team.agent;

import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamModelConfig;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.TeamRuntimeContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;

/**
 * Builds member {@link DeepAgent} instances from {@link TeamAgentSpec} entries.
 *
 * <p>Bridges openjiuwen {@code TeamBackend.ensureMemberRuntime}, which currently creates
 * bare configs without model settings. WorkMate owns this until upstream aligns member
 * runtime creation with {@link com.openjiuwen.agent_teams.agent.TeamAgent#buildDeepAgent}.</p>
 */
final class WorkmateMemberDeepAgentFactory {

    private WorkmateMemberDeepAgentFactory() {
    }

    static DeepAgent create(
            TeamAgentSpec spec,
            String teamName,
            String memberName,
            AgentCard memberCard,
            String memberDesc,
            String memberPrompt) {
        if (spec != null && spec.getAgents() != null && !spec.getAgents().isEmpty()) {
            TeamRuntimeContext context = new TeamRuntimeContext();
            context.setRole(TeamRole.TEAMMATE);
            context.setMemberName(memberName);
            DeepAgentSpec agentSpec = resolveAgentSpec(spec, context);
            if (agentSpec != null && agentSpec.getConfig() != null) {
                DeepAgentConfig config = agentSpec.getConfig();
                if (config.getCard() == null && memberCard != null) {
                    config.setCard(memberCard);
                }
                String basePrompt = config.getSystemPrompt() != null ? config.getSystemPrompt() : "";
                config.setSystemPrompt(basePrompt + teammateTeamPrompt(teamName, memberName));
                applyTeamModelConfig(config, agentSpec.getModel());
                return HarnessFactory.createDeepAgent(config);
            }
        }
        return createFallback(teamName, memberName, memberCard, memberDesc, memberPrompt);
    }

    private static DeepAgent createFallback(
            String teamName,
            String memberName,
            AgentCard memberCard,
            String memberDesc,
            String memberPrompt) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(memberCard);
        String desc = memberDesc != null ? memberDesc : "";
        String prompt = memberPrompt != null ? memberPrompt : "";
        config.setSystemPrompt("You are team member '" + memberName + "' in team '" + teamName + "'.\n"
                + desc + "\n" + prompt);
        return HarnessFactory.createDeepAgent(config);
    }

    private static String teammateTeamPrompt(String teamName, String memberName) {
        return "\n\nYou are working in agent team '" + teamName + "' as teammate '" + memberName + "'.";
    }

    private static DeepAgentSpec resolveAgentSpec(TeamAgentSpec spec, TeamRuntimeContext context) {
        if (spec == null || spec.getAgents() == null || spec.getAgents().isEmpty()) {
            return null;
        }
        String memberName = context != null ? context.getMemberName() : null;
        if (memberName != null && spec.getAgents().containsKey(memberName)) {
            return spec.getAgents().get(memberName);
        }
        String roleKey = context != null && context.getRole() != null
                ? context.getRole().name().toLowerCase()
                : "leader";
        DeepAgentSpec byRole = spec.getAgents().get(roleKey);
        if (byRole != null) {
            return byRole;
        }
        DeepAgentSpec teammate = spec.getAgents().get("teammate");
        return teammate != null ? teammate : spec.getAgents().get("leader");
    }

    private static void applyTeamModelConfig(DeepAgentConfig config, TeamModelConfig modelConfig) {
        if (config == null || modelConfig == null) {
            return;
        }
        config.setModelClientConfig(modelConfig.getModelClientConfig());
        config.setModelRequestConfig(modelConfig.getModelRequestConfig());
    }
}
