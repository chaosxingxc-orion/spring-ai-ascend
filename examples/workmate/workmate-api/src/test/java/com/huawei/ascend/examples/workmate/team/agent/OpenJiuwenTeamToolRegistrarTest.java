package com.huawei.ascend.examples.workmate.team.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.config.WorkmateLlmProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import com.huawei.ascend.examples.workmate.model.ModelCatalogService;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenJiuwenTeamToolRegistrarTest {

    private final OpenJiuwenTeamToolRegistrar registrar = new OpenJiuwenTeamToolRegistrar();
    private final List<OpenJiuwenTeamToolRegistrar.RuntimeToolRegistration> registrations = new ArrayList<>();

    @AfterEach
    void tearDown() {
        registrations.forEach(registrar::unregister);
        registrations.clear();
    }

    private TeamAgent buildLeader() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        ExpertRegistry registry = new ExpertRegistry(new WorkmateOfficeProperties(officeRoot.toString()), null);
        WorkmateLlmProperties llm = new WorkmateLlmProperties(
                "OpenAI", "test-key", "https://example.invalid/v1", "gpt-test", true, 10, "gpt-test", java.util.List.of());
        TeamAgentSpecBuilder specBuilder = new TeamAgentSpecBuilder(registry, new ModelCatalogService(llm), llm);
        WorkmateSession session = new WorkmateSession(
                UUID.randomUUID(), "test", "/tmp/openjiuwen-team-tools", SessionStatus.CREATED, "gpt-researcher-team");
        TeamAgentSpec spec = specBuilder.build(session, registry.requireExpert("gpt-researcher-team"), "parent-run");
        return spec.build();
    }

    private OpenJiuwenTeamToolRegistrar.RuntimeToolRegistration register(TeamAgent leader, String scope) {
        OpenJiuwenTeamToolRegistrar.RuntimeToolRegistration registration = registrar.register(leader, scope);
        registrations.add(registration);
        return registration;
    }

    @Test
    void registersRunScopedTeamToolsInResourceMgr() {
        TeamAgent leader = buildLeader();
        register(leader, "run-A");

        // Team tools are registered under run-scoped ids, and the leader's resolved cards point at them.
        assertThat(Runner.resourceMgr().getTool("team.build_team__run-A")).isInstanceOf(Tool.class);
        assertThat(Runner.resourceMgr().getTool("team.send_message__run-A")).isInstanceOf(Tool.class);
        assertThat(leader.getDeepAgent().getAbilityManager().get("send_message"))
                .isInstanceOf(ToolCard.class);
        assertThat(((ToolCard) leader.getDeepAgent().getAbilityManager().get("send_message")).getId())
                .isEqualTo("team.send_message__run-A");
    }

    @Test
    void concurrentRunsDoNotCollideOnTeamToolIds() {
        TeamAgent leaderA = buildLeader();
        TeamAgent leaderB = buildLeader();

        register(leaderA, "run-A");
        // Before the fix this second registration failed with "resource already exist" on team.* ids,
        // degrading run B to a solo leader. With per-run id namespacing both runs coexist.
        register(leaderB, "run-B");

        assertThat(Runner.resourceMgr().getTool("team.send_message__run-A")).isInstanceOf(Tool.class);
        assertThat(Runner.resourceMgr().getTool("team.send_message__run-B")).isInstanceOf(Tool.class);
        assertThat(((ToolCard) leaderA.getDeepAgent().getAbilityManager().get("send_message")).getId())
                .isEqualTo("team.send_message__run-A");
        assertThat(((ToolCard) leaderB.getDeepAgent().getAbilityManager().get("send_message")).getId())
                .isEqualTo("team.send_message__run-B");
    }
}
