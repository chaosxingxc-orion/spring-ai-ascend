package com.huawei.ascend.examples.workmate.team.agent;

import com.huawei.ascend.examples.workmate.mcp.McpGateway;
import com.huawei.ascend.examples.workmate.mcp.McpOpenJiuwenToolFactory;
import com.huawei.ascend.examples.workmate.mcp.McpOpenJiuwenToolFactory.McpToolSet;
import com.huawei.ascend.examples.workmate.office.OfficeArtifactContract;
import com.huawei.ascend.examples.workmate.office.OfficeArtifactLayoutService;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.team.TeamBlackboardContract;
import com.huawei.ascend.examples.workmate.team.TeamUserInteractionContract;
import com.huawei.ascend.examples.workmate.tools.MemberSendMessageToolFactory;
import com.huawei.ascend.examples.workmate.tools.AskUserQuestionToolFactory;
import com.huawei.ascend.examples.workmate.tools.AskUserQuestionToolFactory.AskUserQuestionToolSet;
import com.huawei.ascend.examples.workmate.tools.ToolExecutionContext;
import com.huawei.ascend.examples.workmate.tools.WorkmateToolIds;
import com.huawei.ascend.examples.workmate.tools.WorkspaceToolFactory;
import com.huawei.ascend.examples.workmate.tools.WorkspaceToolFactory.WorkspaceToolSet;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.harness.DeepAgentConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkmateTeamToolRegistrar {

    private final WorkspaceToolFactory workspaceToolFactory;
    private final McpOpenJiuwenToolFactory mcpToolFactory;
    private final McpGateway mcpGateway;
    private final AskUserQuestionToolFactory askUserQuestionToolFactory;
    private final OfficeArtifactLayoutService officeArtifactLayoutService;
    private final MemberSendMessageToolFactory memberSendMessageToolFactory;

    public WorkmateTeamToolRegistrar(
            WorkspaceToolFactory workspaceToolFactory,
            McpOpenJiuwenToolFactory mcpToolFactory,
            McpGateway mcpGateway,
            AskUserQuestionToolFactory askUserQuestionToolFactory,
            OfficeArtifactLayoutService officeArtifactLayoutService,
            MemberSendMessageToolFactory memberSendMessageToolFactory) {
        this.workspaceToolFactory = workspaceToolFactory;
        this.mcpToolFactory = mcpToolFactory;
        this.mcpGateway = mcpGateway;
        this.askUserQuestionToolFactory = askUserQuestionToolFactory;
        this.officeArtifactLayoutService = officeArtifactLayoutService;
        this.memberSendMessageToolFactory = memberSendMessageToolFactory;
    }

    public TeamToolRegistration registerForTeam(TeamAgentSpec spec, TeamToolRegistrationContext context) {
        WorkmateSession session = context.session();
        UUID sessionId = session.getId();
        Path workspaceRoot = Path.of(session.getWorkspaceRoot());
        PermissionMode permissionMode = session.getPermissionMode();
        String agentTag = TeamToolRegistrationContext.sharedAgentTag(sessionId);

        ToolExecutionContext toolContext = new ToolExecutionContext(
                sessionId,
                context.parentRunId(),
                context.approvalGate(),
                context.questionGate());

        WorkspaceToolSet workspaceTools =
                workspaceToolFactory.create(workspaceRoot, agentTag, toolContext, sessionId, permissionMode);
        McpToolSet mcpTools = mcpToolFactory.create(agentTag, toolContext, session.getEnabledConnectorIds());
        AskUserQuestionToolSet askTools = context.questionGate() != null
                ? askUserQuestionToolFactory.register(sessionId, agentTag, toolContext)
                : null;

        List<ToolCard> leaderToolCards = collectToolCards(workspaceTools, mcpTools, askTools);
        List<ToolCard> memberToolCards = collectToolCards(workspaceTools, mcpTools, null);
        String blackboardPath = TeamBlackboardContract.relativePath(context.parentRunId());
        String officeTaskRoot = officeArtifactLayoutService.taskRootRelative(context.team(), sessionId);

        for (Map.Entry<String, DeepAgentSpec> entry : spec.getAgents().entrySet()) {
            DeepAgentSpec agentSpec = entry.getValue();
            if (agentSpec == null || agentSpec.getConfig() == null) {
                continue;
            }
            boolean leaderAgent = TeamUserInteractionContract.isLeaderAgent(entry.getKey());
            DeepAgentConfig config = agentSpec.getConfig();
            config.setSystemPrompt(augmentSystemPrompt(
                    config.getSystemPrompt(),
                    sessionId,
                    permissionMode,
                    blackboardPath,
                    officeTaskRoot,
                    leaderAgent,
                    session.getEnabledConnectorIds()));
            mergeToolCards(config, leaderAgent ? leaderToolCards : memberToolCards);
        }
        return new TeamToolRegistration(workspaceTools, mcpTools, askTools);
    }

    public void unregister(TeamToolRegistration registration, ExpertDefinition team, UUID sessionId) {
        if (registration == null) {
            return;
        }
        workspaceToolFactory.unregister(registration.workspace());
        mcpToolFactory.unregister(registration.mcp());
        if (registration.ask() != null) {
            askUserQuestionToolFactory.unregister(registration.ask());
        }
        if (team != null && sessionId != null) {
            String agentTag = TeamToolRegistrationContext.sharedAgentTag(sessionId);
            for (var member : team.members()) {
                memberSendMessageToolFactory.unregisterMember(sessionId, agentTag, member.id());
            }
        }
    }

    private static List<ToolCard> collectToolCards(
            WorkspaceToolSet workspaceTools, McpToolSet mcpTools, AskUserQuestionToolSet askTools) {
        List<ToolCard> cards = new ArrayList<>();
        appendCards(cards, workspaceTools.tools());
        appendCards(cards, mcpTools.tools());
        if (askTools != null) {
            cards.add(askTools.tool().getCard());
        }
        return List.copyOf(cards);
    }

    private static void appendCards(List<ToolCard> cards, List<Tool> tools) {
        for (Tool tool : tools) {
            if (tool != null && tool.getCard() != null) {
                cards.add(tool.getCard());
            }
        }
    }

    private static void mergeToolCards(DeepAgentConfig config, List<ToolCard> sharedToolCards) {
        List<ToolCard> merged = new ArrayList<>();
        if (config.getTools() != null) {
            merged.addAll(config.getTools());
        }
        for (ToolCard card : sharedToolCards) {
            boolean exists = merged.stream().anyMatch(existing -> existing.getId().equals(card.getId()));
            if (!exists) {
                merged.add(card);
            }
        }
        config.setTools(merged);
    }

    private String augmentSystemPrompt(
            String basePrompt,
            UUID sessionId,
            PermissionMode permissionMode,
            String blackboardPath,
            String officeTaskRoot,
            boolean leaderAgent,
            List<String> enabledConnectorIds) {
        String readId = WorkmateToolIds.read(sessionId);
        String writeId = WorkmateToolIds.write(sessionId);
        String bashId = WorkmateToolIds.bash(sessionId);
        String askId = WorkmateToolIds.askUserQuestion(sessionId);
        StringBuilder prompt = new StringBuilder(basePrompt != null ? basePrompt : "");
        if (leaderAgent) {
            prompt.append("\n\n").append(workspaceRulesPrompt(readId, writeId, bashId, askId, permissionMode));
            prompt.append("\n\n").append(TeamUserInteractionContract.leaderSendMessageRules());
            prompt.append("\n\n").append(TeamUserInteractionContract.leaderHitlRules(askId));
        } else {
            prompt.append("\n\n")
                    .append(TeamUserInteractionContract.memberWorkspaceRulesPrompt(
                            readId, writeId, bashId, permissionMode));
            prompt.append("\n\n").append(TeamUserInteractionContract.memberHitlRules(askId));
            prompt.append("\n\n").append(TeamUserInteractionContract.memberSendMessageRules());
        }
        if (mcpGateway.hasServers() && enabledConnectorIds != null && !enabledConnectorIds.isEmpty()) {
            prompt.append("\n\n").append(mcpPrompt(writeId, permissionMode));
        }
        if (officeTaskRoot != null && !officeTaskRoot.isBlank()) {
            prompt.append("\n\n").append(OfficeArtifactContract.BASELINE_PROMPT);
            prompt.append("\n\n").append(OfficeArtifactContract.officeLayoutPrompt(officeTaskRoot));
        }
        prompt.append("\n\n").append(TeamBlackboardContract.blackboardPrompt(blackboardPath));
        return prompt.toString();
    }

    private static String workspaceRulesPrompt(
            String readId, String writeId, String bashId, String askId, PermissionMode mode) {
        String askHint = "When requirements are ambiguous, call %s with a clear question and optional choices."
                .formatted(askId);
        if (mode == PermissionMode.ASK) {
            return """
                    You are WorkMate in Ask mode: answer questions using %s and MCP read tools only.
                    Do NOT call write or bash tools. Be concise.
                    %s
                    """.formatted(readId, askHint);
        }
        if (mode == PermissionMode.PLAN) {
            return """
                    You are WorkMate in Plan mode: analyze the request, read context with %s and MCP tools,
                    then propose a clear step-by-step plan in your reply. Do NOT call write or bash tools yet.
                    %s
                    """.formatted(readId, askHint);
        }
        return """
                You are WorkMate, a workspace assistant for one task session.
                Use %s, %s, and %s to work ONLY inside the session workspace.
                Prefer relative paths (e.g. hello.md). When asked to create a file, call %s.
                When MCP tools (mcp__*) are available, use them to read project docs outside the workspace.
                Be concise; confirm what you created or changed.
                %s
                """.formatted(readId, writeId, bashId, writeId, askHint);
    }

    private static String mcpPrompt(String writeId, PermissionMode mode) {
        if (!mode.allowsWrite()) {
            return """
                    MCP proxy tools (prefix mcp__) are connected. Use them for external read-only data.
                    Do not attempt to persist results to the workspace in this mode.
                    """;
        }
        return """
                MCP proxy tools (prefix mcp__) are connected. Only allowlisted tools are registered per server.
                Use them for external data (e.g. fund search, docs), then write results into the session workspace with %s.
                """.formatted(writeId);
    }
}
