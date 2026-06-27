package com.huawei.ascend.examples.workmate.team.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.config.WorkmateLlmProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import com.huawei.ascend.examples.workmate.model.ModelCatalogService;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.tools.MemberSendMessageToolFactory;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.harness.DeepAgentConfig;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkmateTeamMemberRuntimeBootstrapTest {

    @Test
    void rebindMemberRuntimesAppliesModelConfigFromSpec() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        ExpertRegistry registry = new ExpertRegistry(new WorkmateOfficeProperties(officeRoot.toString()), null);
        WorkmateLlmProperties llm = new WorkmateLlmProperties(
                "OpenAI", "test-key", "https://example.invalid/v1", "gpt-test", true, 10, "gpt-test", java.util.List.of());
        TeamAgentSpecBuilder specBuilder = new TeamAgentSpecBuilder(registry, new ModelCatalogService(llm), llm);

        WorkmateSession session = new WorkmateSession(
                UUID.randomUUID(), "test", "/tmp/workmate-member-runtime", SessionStatus.CREATED, "gpt-researcher-team");
        TeamAgentSpec spec = specBuilder.build(session, registry.requireExpert("gpt-researcher-team"), "parent-run");
        TeamAgent leader = spec.build();

        new WorkmateTeamMemberRuntimeBootstrap(Mockito.mock(MemberSendMessageToolFactory.class)).rebindMemberRuntimes(
                leader, spec, MemberExecutionListener.NOOP, null);

        DeepAgentConfig memberConfig = (DeepAgentConfig) leader.getTeamBackend()
                .getMemberRuntime("topic-researcher")
                .getAgent()
                .getConfig();
        assertThat(memberConfig.getModelClientConfig()).isNotNull();
        assertThat(memberConfig.getModelRequestConfig()).isNotNull();
    }
}
