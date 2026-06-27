package com.huawei.ascend.examples.workmate.team.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.huawei.ascend.examples.workmate.config.WorkmateLlmProperties;
import com.huawei.ascend.examples.workmate.config.WorkmateOfficeProperties;
import com.huawei.ascend.examples.workmate.filehistory.FileHistoryService;
import com.huawei.ascend.examples.workmate.mcp.McpGateway;
import com.huawei.ascend.examples.workmate.mcp.McpOpenJiuwenToolFactory;
import com.huawei.ascend.examples.workmate.model.ModelCatalogService;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.OfficeArtifactLayoutService;
import com.huawei.ascend.examples.workmate.question.QuestionGate;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.huawei.ascend.examples.workmate.session.SessionStatus;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.tools.AskUserQuestionToolFactory;
import com.huawei.ascend.examples.workmate.tools.MemberSendMessageToolFactory;
import com.huawei.ascend.examples.workmate.tools.WorkspaceShellExecutor;
import com.huawei.ascend.examples.workmate.tools.WorkspaceToolFactory;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkmateTeamToolRegistrarTest {

    @Test
    void leaderGetsAskToolMembersDoNot() {
        Path officeRoot = Path.of("../office").toAbsolutePath().normalize();
        ExpertRegistry registry = new ExpertRegistry(new WorkmateOfficeProperties(officeRoot.toString()), null);
        WorkmateLlmProperties llm = new WorkmateLlmProperties(
                "OpenAI", "test-key", "https://example.invalid/v1", "gpt-test", true, 10, "gpt-test", java.util.List.of());
        ModelCatalogService modelCatalog = new ModelCatalogService(llm);
        TeamAgentSpecBuilder specBuilder = new TeamAgentSpecBuilder(registry, modelCatalog, llm);

        McpGateway mcpGateway = mock(McpGateway.class);
        when(mcpGateway.hasServers()).thenReturn(false);
        WorkspaceToolFactory workspaceToolFactory = new WorkspaceToolFactory(
                mock(WorkspaceShellExecutor.class), mock(FileHistoryService.class));
        McpOpenJiuwenToolFactory mcpToolFactory = new McpOpenJiuwenToolFactory(mcpGateway, mock());
        AskUserQuestionToolFactory askFactory = mock(AskUserQuestionToolFactory.class);
        when(askFactory.register(any(), any(), any()))
                .thenAnswer(invocation -> {
                    UUID sessionId = invocation.getArgument(0);
                    String agentTag = invocation.getArgument(1);
                    Tool askTool = mock(Tool.class);
                    ToolCard askCard = ToolCard.builder()
                            .id("workmate_ask_user_question__" + sessionId)
                            .name("ask")
                            .description("ask")
                            .build();
                    when(askTool.getCard()).thenReturn(askCard);
                    return new AskUserQuestionToolFactory.AskUserQuestionToolSet(askTool, agentTag, sessionId);
                });
        OfficeArtifactLayoutService layoutService = mock(OfficeArtifactLayoutService.class);
        when(layoutService.taskRootRelative(any(ExpertDefinition.class), any(UUID.class))).thenReturn("office/tasks/demo");

        WorkmateTeamToolRegistrar registrar = new WorkmateTeamToolRegistrar(
                workspaceToolFactory,
                mcpToolFactory,
                mcpGateway,
                askFactory,
                layoutService,
                mock(MemberSendMessageToolFactory.class));

        ExpertDefinition team = registry.requireExpert("gpt-researcher-team");
        UUID sessionId = UUID.randomUUID();
        WorkmateSession session = new WorkmateSession(
                sessionId, "test", "/tmp/workmate-tool-registrar", SessionStatus.CREATED, team.id());
        session.setPermissionMode(PermissionMode.CRAFT);

        TeamAgentSpec spec = specBuilder.build(session, team, "parent-run-tools");
        TeamToolRegistrationContext context = new TeamToolRegistrationContext(
                session, team, "parent-run-tools", null, mock(QuestionGate.class));

        TeamToolRegistration registration = registrar.registerForTeam(spec, context);
        try {
            DeepAgentSpec leader = spec.getAgents().get("leader");
            assertThat(leader.getConfig().getTools()).isNotEmpty();
            assertThat(leader.getConfig().getSystemPrompt()).contains("Team shared blackboard");
            assertThat(leader.getConfig().getSystemPrompt()).contains("office/tasks/demo");
            assertThat(leader.getConfig().getSystemPrompt()).contains(askToolId(sessionId));

            DeepAgentSpec member = spec.getAgents().get("topic-researcher");
            assertThat(member.getConfig().getTools()).hasSize(leader.getConfig().getTools().size() - 1);
            assertThat(member.getConfig().getSystemPrompt()).contains("NEVER call");
            assertThat(member.getConfig().getSystemPrompt()).doesNotContain("When requirements are ambiguous, call");
        } finally {
            registrar.unregister(registration, team, sessionId);
        }
    }

    private static String askToolId(UUID sessionId) {
        return "workmate_ask_user_question__" + sessionId;
    }
}
