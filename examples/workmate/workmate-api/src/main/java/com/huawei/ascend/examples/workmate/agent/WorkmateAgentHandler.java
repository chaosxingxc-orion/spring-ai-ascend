package com.huawei.ascend.examples.workmate.agent;

import com.huawei.ascend.examples.workmate.approval.ApprovalGate;
import com.huawei.ascend.examples.workmate.config.WorkmateLlmProperties;
import com.huawei.ascend.examples.workmate.memory.MemoryService;
import com.huawei.ascend.examples.workmate.model.ModelCatalogService;
import com.huawei.ascend.examples.workmate.model.ModelCatalogService.ResolvedModel;
import com.huawei.ascend.examples.workmate.model.ModelEffort;
import com.huawei.ascend.examples.workmate.mcp.McpGateway;
import com.huawei.ascend.examples.workmate.mcp.McpOpenJiuwenToolFactory;
import com.huawei.ascend.examples.workmate.mcp.McpOpenJiuwenToolFactory.McpToolSet;
import com.huawei.ascend.examples.workmate.office.ExpertDefinition;
import com.huawei.ascend.examples.workmate.office.ExpertService;
import com.huawei.ascend.examples.workmate.office.SessionSkillPromptService;
import com.huawei.ascend.examples.workmate.profile.UserProfileService;
import com.huawei.ascend.examples.workmate.office.OfficeArtifactContract;
import com.huawei.ascend.examples.workmate.session.PermissionMode;
import com.huawei.ascend.examples.workmate.spi.topic.TopicBusMemberPublisher;
import com.huawei.ascend.examples.workmate.team.TeamBlackboardContract;
import com.huawei.ascend.examples.workmate.team.TeamUserInteractionContract;
import com.huawei.ascend.examples.workmate.team.TeamTopicBusContract;
import com.huawei.ascend.examples.workmate.team.agent.TeamToolRegistrationContext;
import com.huawei.ascend.examples.workmate.question.QuestionGate;
import com.huawei.ascend.examples.workmate.tools.AskUserQuestionToolFactory;
import com.huawei.ascend.examples.workmate.tools.AskUserQuestionToolFactory.AskUserQuestionToolSet;
import com.huawei.ascend.examples.workmate.tools.MemberSendMessageToolFactory;
import com.huawei.ascend.examples.workmate.tools.MemberSendMessageToolFactory.MemberSendMessageToolSet;
import com.huawei.ascend.examples.workmate.team.runtime.TeamRuntimeManager;
import com.huawei.ascend.examples.workmate.tools.TeamTopicBusToolFactory;
import com.huawei.ascend.examples.workmate.tools.TeamTopicBusToolFactory.TeamTopicBusToolSet;
import com.huawei.ascend.examples.workmate.tools.ToolExecutionContext;
import com.huawei.ascend.examples.workmate.tools.WorkmateToolIds;
import com.huawei.ascend.examples.workmate.tools.WorkspaceToolFactory;
import com.huawei.ascend.examples.workmate.tools.WorkspaceToolFactory.WorkspaceToolSet;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.UUID;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenAgentRuntimeHandler;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WorkmateAgentHandler extends OpenJiuwenAgentRuntimeHandler {

    public static final String AGENT_ID = "workmate-agent";
    public static final String WORKSPACE_ROOT_VAR = "workmate.workspaceRoot";
    public static final String APPROVAL_GATE_VAR = "workmate.approvalGate";
    public static final String QUESTION_GATE_VAR = "workmate.questionGate";
    public static final String TASK_ID_VAR = "workmate.taskId";
    public static final String EXPERT_ID_VAR = "workmate.expertId";
    public static final String PERMISSION_MODE_VAR = "workmate.permissionMode";
    public static final String OFFICE_TASK_ROOT_VAR = "workmate.officeTaskRoot";
    public static final String TEAM_BLACKBOARD_PATH_VAR = "workmate.teamBlackboardPath";
    public static final String TEAM_TOPIC_BUS_PUBLISHER_VAR = "workmate.teamTopicBusPublisher";
    public static final String TEAM_MEMBER_ID_VAR = "workmate.teamMemberId";
    public static final String MENTIONS_PROMPT_VAR = "workmate.mentionsPrompt";
    public static final String MODEL_ID_VAR = "workmate.modelId";
    public static final String EFFORT_VAR = "workmate.effort";
    public static final String ENABLED_CONNECTOR_IDS_VAR = "workmate.enabledConnectorIds";
    public static final String ENABLED_SKILL_IDS_VAR = "workmate.enabledSkillIds";
    public static final String HANDOFF_PROMPT_VAR = "workmate.handoffPrompt";

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

    private final WorkmateLlmProperties llm;
    private final WorkspaceToolFactory toolFactory;
    private final McpOpenJiuwenToolFactory mcpToolFactory;
    private final McpGateway mcpGateway;
    private final ExpertService expertService;
    private final TeamTopicBusToolFactory teamTopicBusToolFactory;
    private final MemberSendMessageToolFactory memberSendMessageToolFactory;
    private final TeamRuntimeManager teamRuntimeManager;
    private final AskUserQuestionToolFactory askUserQuestionToolFactory;
    private final MemoryService memoryService;
    private final UserProfileService userProfileService;
    private final ModelCatalogService modelCatalogService;
    private final SessionSkillPromptService sessionSkillPromptService;

    public WorkmateAgentHandler(
            WorkmateLlmProperties llm,
            WorkspaceToolFactory toolFactory,
            McpOpenJiuwenToolFactory mcpToolFactory,
            McpGateway mcpGateway,
            ExpertService expertService,
            TeamTopicBusToolFactory teamTopicBusToolFactory,
            MemberSendMessageToolFactory memberSendMessageToolFactory,
            TeamRuntimeManager teamRuntimeManager,
            AskUserQuestionToolFactory askUserQuestionToolFactory,
            MemoryService memoryService,
            UserProfileService userProfileService,
            ModelCatalogService modelCatalogService,
            SessionSkillPromptService sessionSkillPromptService) {
        super(AGENT_ID);
        this.llm = llm;
        this.toolFactory = toolFactory;
        this.mcpToolFactory = mcpToolFactory;
        this.mcpGateway = mcpGateway;
        this.expertService = expertService;
        this.teamTopicBusToolFactory = teamTopicBusToolFactory;
        this.memberSendMessageToolFactory = memberSendMessageToolFactory;
        this.teamRuntimeManager = teamRuntimeManager;
        this.askUserQuestionToolFactory = askUserQuestionToolFactory;
        this.memoryService = memoryService;
        this.userProfileService = userProfileService;
        this.modelCatalogService = modelCatalogService;
        this.sessionSkillPromptService = sessionSkillPromptService;
    }

    @Override
    protected BaseAgent createOpenJiuwenAgent(AgentExecutionContext context) {
        String sessionId = context.getScope().sessionId();
        Object workspaceRootValue = context.getVariables().get(WORKSPACE_ROOT_VAR);
        if (!(workspaceRootValue instanceof String workspaceRoot) || workspaceRoot.isBlank()) {
            throw new IllegalStateException(WORKSPACE_ROOT_VAR + " is required");
        }
        Path workspace = Path.of(workspaceRoot);
        UUID sessionUuid = UUID.fromString(sessionId);
        boolean teamSubRun = isTeamSubRun(context);
        String teamAgentTag = TeamToolRegistrationContext.sharedAgentTag(sessionUuid);
        String toolAgentTag = teamSubRun ? teamAgentTag : AGENT_ID + "-" + sessionId;
        String invocationAgentId = AGENT_ID + "-" + sessionId;

        AgentCard card = AgentCard.builder()
                .id(invocationAgentId)
                .name(invocationAgentId)
                .description("WorkMate session agent with workspace tools.")
                .build();
        ReActAgent agent = new ReActAgent(card);

        int maxIterations = llm.maxIterations();
        java.util.Optional<ExpertDefinition> expertOpt = readExpertDefinition(context);
        if (expertOpt.isPresent()) {
            ExpertDefinition expert = expertOpt.get();
            Integer expertMax = expert.effectiveMaxTurns();
            if (expertMax != null) {
                maxIterations = expertMax;
            }
            // TODO(A1): merge expert.preloadSkills() with session-enabled skills when wiring registry defaults
        }

        ReActAgentConfig config = ReActAgentConfig.builder()
                .maxIterations(maxIterations)
                .build();
        ResolvedModel resolvedModel = resolveModel(context);
        config.configureModelClient(
                resolvedModel.provider(),
                resolvedModel.apiKey(),
                resolvedModel.apiBase(),
                resolvedModel.modelName(),
                resolvedModel.sslVerify());
        // configureModelClient leaves the openjiuwen default 60s request timeout; raise it so long
        // single-shot generations don't fail with a Reactor 60000ms TimeoutException.
        config.setModelClientConfig(withRequestTimeout(config.getModelClientConfig()));
        ModelRequestConfig modelConfig = config.getModelConfigObj();
        resolveEffort(context).applyTo(modelConfig);
        agent.configure(config);
        String readId = WorkmateToolIds.read(sessionId);
        String writeId = WorkmateToolIds.write(sessionId);
        String bashId = WorkmateToolIds.bash(sessionId);
        String askId = WorkmateToolIds.askUserQuestion(sessionId);
        PermissionMode permissionMode = readPermissionMode(context);
        if (!isTeamSubRun(context)) {
            String memoryText = memoryService.loadForInjection();
            if (!memoryText.isBlank()) {
                agent.addPromptBuilderSection("workmate_memory", memoryPrompt(memoryText), 9);
            }
            String profileText = userProfileService.loadForInjection();
            if (!profileText.isBlank()) {
                agent.addPromptBuilderSection("workmate_user_profile", userProfilePrompt(profileText), 8);
            }
            Object mentionsPrompt = context.getVariables().get(MENTIONS_PROMPT_VAR);
            if (mentionsPrompt instanceof String mentionText && !mentionText.isBlank()) {
                agent.addPromptBuilderSection("workmate_mentions", mentionText, 7);
            }
            Object handoffPrompt = context.getVariables().get(HANDOFF_PROMPT_VAR);
            if (handoffPrompt instanceof String handoffText && !handoffText.isBlank()) {
                agent.addPromptBuilderSection("workmate_handoff", handoffText, 6);
            }
        }
        expertOpt.ifPresent(expert -> agent.addPromptBuilderSection(
                "workmate_expert", expert.systemPrompt(), 10));
        List<String> enabledSkillIds = readEnabledSkillIds(context);
        if (!enabledSkillIds.isEmpty()) {
            String skillsPrompt = sessionSkillPromptService.buildPromptSection(enabledSkillIds);
            if (!skillsPrompt.isBlank()) {
                agent.addPromptBuilderSection("workmate_session_skills", skillsPrompt, 11);
            }
        }
        Object officeTaskRoot = context.getVariables().get(OFFICE_TASK_ROOT_VAR);
        if (officeTaskRoot instanceof String taskRoot && !taskRoot.isBlank()) {
            agent.addPromptBuilderSection("workmate_office_baseline", OfficeArtifactContract.BASELINE_PROMPT, 5);
            agent.addPromptBuilderSection(
                    "workmate_office_layout", OfficeArtifactContract.officeLayoutPrompt(taskRoot), 8);
        }
        Object blackboardPath = context.getVariables().get(TEAM_BLACKBOARD_PATH_VAR);
        if (blackboardPath instanceof String path && !path.isBlank()) {
            agent.addPromptBuilderSection(
                    "workmate_team_blackboard", TeamBlackboardContract.blackboardPrompt(path), 12);
        }
        TopicBusMemberPublisher topicBusPublisher = readTopicBusPublisher(context);
        if (topicBusPublisher != null) {
            String busPublishId = WorkmateToolIds.teamBusPublish(sessionId);
            agent.addPromptBuilderSection(
                    "workmate_team_bus",
                    TeamTopicBusContract.memberPrompt(busPublishId, topicBusPublisher.memberId()),
                    11);
        }
        boolean teamSubRunForPrompt = isTeamSubRun(context);
        agent.addPromptBuilderSection(
                "workmate_rules",
                teamSubRunForPrompt
                        ? TeamUserInteractionContract.memberWorkspaceRulesPrompt(
                                readId, writeId, bashId, permissionMode)
                        : workspaceRulesPrompt(readId, writeId, bashId, askId, permissionMode),
                20);
        if (teamSubRunForPrompt) {
            agent.addPromptBuilderSection(
                    "workmate_team_member_hitl",
                    TeamUserInteractionContract.memberHitlRules(askId),
                    21);
            agent.addPromptBuilderSection(
                    "workmate_team_member_send_message",
                    TeamUserInteractionContract.memberSendMessageRules(),
                    22);
        }
        if (mcpGateway.hasServers() && !readEnabledConnectorIds(context).isEmpty()) {
            agent.addPromptBuilderSection("workmate_mcp", mcpPrompt(writeId, permissionMode), 25);
        }

        ApprovalGate approvalGate = readApprovalGate(context);
        QuestionGate questionGate = readQuestionGate(context);
        String taskId = readTaskId(context);
        ToolExecutionContext toolContext = new ToolExecutionContext(
                UUID.fromString(sessionId), taskId, approvalGate, questionGate);

        WorkspaceToolSet toolSet = teamSubRun && workspaceToolsRegistered(sessionUuid)
                ? referenceWorkspaceTools(sessionUuid, toolAgentTag, permissionMode)
                : toolFactory.create(workspace, toolAgentTag, toolContext, sessionUuid, permissionMode);
        McpToolSet mcpToolSet = teamSubRun && mcpToolsRegistered()
                ? referenceMcpTools(toolAgentTag)
                : mcpToolFactory.create(toolAgentTag, toolContext, readEnabledConnectorIds(context));
        AskUserQuestionToolSet askToolSet = questionGate != null && !teamSubRun
                ? askUserQuestionToolFactory.register(sessionUuid, toolAgentTag, toolContext)
                : null;
        TeamTopicBusToolSet teamBusToolSet =
                teamTopicBusToolFactory.register(topicBusPublisher, toolAgentTag, sessionUuid)
                        .orElse(null);
        MemberSendMessageToolSet memberSendToolSet = null;
        if (teamSubRun) {
            String memberId = readTeamMemberId(context);
            if (memberId != null) {
                String sendToolId = WorkmateToolIds.sendMessage(sessionUuid, memberId);
                Tool sendTool = getRegisteredTool(sendToolId);
                if (sendTool != null) {
                    memberSendToolSet = new MemberSendMessageToolSet(sendTool, toolAgentTag, sendToolId);
                } else {
                    memberSendToolSet = memberSendMessageToolFactory
                            .register(sessionUuid, toolAgentTag, memberId, teamRuntimeManager.findBySession(sessionId))
                            .orElse(null);
                }
            }
        }
        Map<String, Object> agentState = new HashMap<>();
        agentState.put("workmate.toolSet", toolSet);
        agentState.put("workmate.mcpToolSet", mcpToolSet);
        if (askToolSet != null) {
            agentState.put("workmate.askToolSet", askToolSet);
        }
        if (teamBusToolSet != null) {
            agentState.put("workmate.teamBusToolSet", teamBusToolSet);
        }
        if (memberSendToolSet != null) {
            agentState.put("workmate.memberSendMessageToolSet", memberSendToolSet);
        }
        context.replaceAgentState(agentState);
        for (Tool tool : toolSet.tools()) {
            agent.getAbilityManager().add(tool.getCard());
        }
        for (Tool tool : mcpToolSet.tools()) {
            agent.getAbilityManager().add(tool.getCard());
        }
        if (teamBusToolSet != null) {
            agent.getAbilityManager().add(teamBusToolSet.tool().getCard());
        }
        if (memberSendToolSet != null) {
            agent.getAbilityManager().add(memberSendToolSet.tool().getCard());
        }
        if (askToolSet != null) {
            agent.getAbilityManager().add(askToolSet.tool().getCard());
        }
        return agent;
    }

    private static String memoryPrompt(String memoryText) {
        return """
                Long-term user memory (stable preferences and facts). Use to personalize responses; do not repeat verbatim unless relevant.

                %s
                """.formatted(memoryText);
    }

    private static String userProfilePrompt(String profileText) {
        return """
                Onboarding user profile (role and interests). Adapt examples and tone; do not repeat verbatim unless relevant.

                %s
                """.formatted(profileText);
    }

    private static boolean isTeamSubRun(AgentExecutionContext context) {
        Object memberId = context.getVariables().get(TEAM_MEMBER_ID_VAR);
        if (memberId instanceof String memberText && !memberText.isBlank()) {
            return true;
        }
        Object blackboard = context.getVariables().get(TEAM_BLACKBOARD_PATH_VAR);
        if (blackboard instanceof String path && !path.isBlank()) {
            return true;
        }
        Object publisher = context.getVariables().get(TEAM_TOPIC_BUS_PUBLISHER_VAR);
        return publisher instanceof TopicBusMemberPublisher;
    }

    private static TopicBusMemberPublisher readTopicBusPublisher(AgentExecutionContext context) {
        Object value = context.getVariables().get(TEAM_TOPIC_BUS_PUBLISHER_VAR);
        return value instanceof TopicBusMemberPublisher publisher ? publisher : null;
    }

    private static String readTeamMemberId(AgentExecutionContext context) {
        Object value = context.getVariables().get(TEAM_MEMBER_ID_VAR);
        return value instanceof String text && !text.isBlank() ? text.strip() : null;
    }

    private java.util.Optional<ExpertDefinition> readExpertDefinition(AgentExecutionContext context) {
        Object value = context.getVariables().get(EXPERT_ID_VAR);
        if (!(value instanceof String expertId) || expertId.isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(expertService.requireExpertDefinition(expertId));
    }

    private static PermissionMode readPermissionMode(AgentExecutionContext context) {
        Object value = context.getVariables().get(PERMISSION_MODE_VAR);
        if (value instanceof PermissionMode mode) {
            return mode;
        }
        if (value instanceof String name && !name.isBlank()) {
            try {
                return PermissionMode.valueOf(name.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return PermissionMode.CRAFT;
            }
        }
        return PermissionMode.CRAFT;
    }

    @SuppressWarnings("unchecked")
    private static List<String> readEnabledConnectorIds(AgentExecutionContext context) {
        Object value = context.getVariables().get(ENABLED_CONNECTOR_IDS_VAR);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof String text && !text.isBlank())
                    .map(item -> ((String) item).strip())
                    .toList();
        }
        return List.of();
    }

    private static List<String> readEnabledSkillIds(AgentExecutionContext context) {
        Object value = context.getVariables().get(ENABLED_SKILL_IDS_VAR);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item instanceof String text && !text.isBlank())
                    .map(item -> ((String) item).strip())
                    .toList();
        }
        return List.of();
    }

    private static ApprovalGate readApprovalGate(AgentExecutionContext context) {
        Object value = context.getVariables().get(APPROVAL_GATE_VAR);
        return value instanceof ApprovalGate gate ? gate : null;
    }

    private static QuestionGate readQuestionGate(AgentExecutionContext context) {
        Object value = context.getVariables().get(QUESTION_GATE_VAR);
        return value instanceof QuestionGate gate ? gate : null;
    }

    private static String readTaskId(AgentExecutionContext context) {
        Object value = context.getVariables().get(TASK_ID_VAR);
        if (value instanceof String taskId && !taskId.isBlank()) {
            return taskId;
        }
        return context.getScope().taskId() != null ? context.getScope().taskId() : context.getAgentStateKey();
    }

    private ModelClientConfig withRequestTimeout(ModelClientConfig base) {
        if (base == null) {
            return null;
        }
        return ModelClientConfig.builder()
                .clientProvider(base.getClientProvider())
                .apiKey(base.getApiKey())
                .apiBase(base.getApiBase())
                .verifySsl(base.isVerifySsl())
                .sslCert(base.getSslCert())
                .headers(base.getHeaders())
                .customHeaders(base.getCustomHeaders())
                .maxRetries(base.getMaxRetries())
                .timeout(llm.requestTimeoutSeconds())
                .build();
    }

    private ResolvedModel resolveModel(AgentExecutionContext context) {
        Object value = context.getVariables().get(MODEL_ID_VAR);
        if (value instanceof String modelId && !modelId.isBlank()) {
            return modelCatalogService.resolve(modelId);
        }
        return modelCatalogService.resolveDefault();
    }

    private static ModelEffort resolveEffort(AgentExecutionContext context) {
        Object value = context.getVariables().get(EFFORT_VAR);
        if (value instanceof String effort && !effort.isBlank()) {
            return ModelEffort.parse(effort);
        }
        return ModelEffort.AUTO;
    }

    private static boolean workspaceToolsRegistered(UUID sessionId) {
        return getRegisteredTool(WorkmateToolIds.read(sessionId)) != null;
    }

    private static WorkspaceToolSet referenceWorkspaceTools(
            UUID sessionId, String agentTag, PermissionMode mode) {
        List<Tool> tools = new ArrayList<>();
        Tool read = getRegisteredTool(WorkmateToolIds.read(sessionId));
        if (read != null) {
            tools.add(read);
        }
        if (mode.allowsWrite()) {
            Tool write = getRegisteredTool(WorkmateToolIds.write(sessionId));
            if (write != null) {
                tools.add(write);
            }
        }
        if (mode.allowsBash()) {
            Tool bash = getRegisteredTool(WorkmateToolIds.bash(sessionId));
            if (bash != null) {
                tools.add(bash);
            }
        }
        return new WorkspaceToolSet(List.copyOf(tools), agentTag, sessionId);
    }

    private boolean mcpToolsRegistered() {
        if (!mcpGateway.hasServers()) {
            return true;
        }
        for (McpGateway.McpToolDescriptor descriptor : mcpGateway.listTools()) {
            if (getRegisteredTool(descriptor.openJiuwenToolId()) == null) {
                return false;
            }
        }
        return true;
    }

    private McpToolSet referenceMcpTools(String agentTag) {
        if (!mcpGateway.hasServers()) {
            return new McpToolSet(List.of(), agentTag);
        }
        List<Tool> tools = new ArrayList<>();
        for (McpGateway.McpToolDescriptor descriptor : mcpGateway.listTools()) {
            Tool tool = getRegisteredTool(descriptor.openJiuwenToolId());
            if (tool != null) {
                tools.add(tool);
            }
        }
        return new McpToolSet(List.copyOf(tools), agentTag);
    }

    private static Tool getRegisteredTool(String toolId) {
        Object raw = Runner.resourceMgr().getTool(toolId);
        return raw instanceof Tool tool ? tool : null;
    }

    @Override
    public void stop() {
        // per-invocation tool cleanup happens in AgentRunService
    }
}
