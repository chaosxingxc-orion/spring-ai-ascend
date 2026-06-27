package com.huawei.ascend.examples.workmate.team.agent;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.tools.AgentTeamsToolRegistry;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.AbilityManager;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Registers openjiuwen team tools ({@code build_team}, {@code send_message}, …) into
 * {@link Runner#resourceMgr()} for the leader's {@link com.openjiuwen.agent_teams.tools.TeamBackend}.
 *
 * <p>TeamAgent only adds tool cards to AbilityManager; without this step tool execution fails with
 * {@code Tool instance not found in resource_mgr: team.*}.</p>
 *
 * <p><b>Per-run isolation (ADR-005 pattern).</b> The openjiuwen team tools ship with fixed ids
 * ({@code team.build_team}, {@code team.send_message}, …) and {@code Runner.resourceMgr()} is a
 * process-wide singleton that resolves tools by id (tags are ignored at resolution time). Registering
 * those fixed ids globally means two concurrent team runs collide: the later run fails to register
 * ({@code resource already exist}) and, worse, when the earlier run finishes its cleanup removes the
 * shared instances out from under the still-running later run ({@code Tool instance not found in
 * resource_mgr: team.send_message}), silently degrading the team into a solo leader with no
 * sub-agents. To let any number of team runs execute concurrently we give each run its own id
 * namespace (mirroring {@link com.huawei.ascend.examples.workmate.tools.WorkmateToolIds}) and point
 * the leader's resolved cards at the run-scoped ids.</p>
 */
@Component
public class OpenJiuwenTeamToolRegistrar {

    private static final String SCOPE_SEP = "__";

    public RuntimeToolRegistration register(TeamAgent leader, String runScope) {
        if (leader == null || leader.getTeamBackend() == null || leader.getSpec() == null) {
            return RuntimeToolRegistration.EMPTY;
        }
        List<Tool> tools = AgentTeamsToolRegistry.createTeamTools(
                leader.getTeamBackend(),
                TeamRole.LEADER,
                leader.getSpec().getTeammateMode());
        if (tools.isEmpty()) {
            return RuntimeToolRegistration.EMPTY;
        }
        String scope = runScope == null || runScope.isBlank() ? "default" : runScope;
        AbilityManager abilityManager = leaderAbilityManager(leader);
        for (Tool tool : tools) {
            ToolCard card = tool.getCard();
            if (card == null || card.getId() == null) {
                continue;
            }
            // Namespace the resource id by run so concurrent team runs never share an id.
            String scopedId = card.getId() + SCOPE_SEP + scope;
            String toolName = card.getName();
            card.setId(scopedId);
            // The leader's AbilityManager holds a separate card (keyed by tool name) whose id the ReAct
            // loop uses to resolve the instance from resourceMgr. Repoint it at the run-scoped id so
            // name → scopedId → this run's own tool instance (bound to this run's TeamBackend).
            if (abilityManager != null && toolName != null
                    && abilityManager.get(toolName) instanceof ToolCard leaderCard) {
                leaderCard.setId(scopedId);
            }
        }
        Runner.resourceMgr().addTools(tools, null);
        return new RuntimeToolRegistration(tools);
    }

    private static AbilityManager leaderAbilityManager(TeamAgent leader) {
        return leader.getDeepAgent() != null ? leader.getDeepAgent().getAbilityManager() : null;
    }

    public void unregister(RuntimeToolRegistration registration) {
        if (registration == null || registration.tools().isEmpty()) {
            return;
        }
        for (Tool tool : registration.tools()) {
            if (tool == null || tool.getCard() == null || tool.getCard().getId() == null) {
                continue;
            }
            Runner.resourceMgr().removeTool(tool.getCard().getId(), null, TagMatchStrategy.ALL, true);
        }
    }

    public record RuntimeToolRegistration(List<Tool> tools) {

        public static final RuntimeToolRegistration EMPTY = new RuntimeToolRegistration(List.of());

        public RuntimeToolRegistration {
            tools = tools != null ? List.copyOf(tools) : List.of();
        }
    }
}
