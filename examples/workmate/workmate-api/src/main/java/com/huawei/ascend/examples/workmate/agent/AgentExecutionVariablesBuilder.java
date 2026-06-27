package com.huawei.ascend.examples.workmate.agent;

import com.huawei.ascend.examples.workmate.approval.ApprovalGate;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.mention.MentionContextService;
import com.huawei.ascend.examples.workmate.mention.dto.MentionItem;
import com.huawei.ascend.examples.workmate.office.ExpertRegistry;
import com.huawei.ascend.examples.workmate.office.OfficeArtifactLayoutService;
import com.huawei.ascend.examples.workmate.question.QuestionGate;
import com.huawei.ascend.examples.workmate.session.ExpertHandoffService;
import com.huawei.ascend.examples.workmate.session.SessionService;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.spi.topic.TopicBusMemberPublisher;
import com.huawei.ascend.examples.workmate.team.TeamBlackboardContract;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AgentExecutionVariablesBuilder {

    private final ExpertRegistry expertRegistry;
    private final OfficeArtifactLayoutService officeArtifactLayoutService;
    private final MentionContextService mentionContextService;
    private final ExpertHandoffService expertHandoffService;
    private final SessionService sessionService;

    public AgentExecutionVariablesBuilder(
            ExpertRegistry expertRegistry,
            OfficeArtifactLayoutService officeArtifactLayoutService,
            MentionContextService mentionContextService,
            ExpertHandoffService expertHandoffService,
            SessionService sessionService) {
        this.expertRegistry = expertRegistry;
        this.officeArtifactLayoutService = officeArtifactLayoutService;
        this.mentionContextService = mentionContextService;
        this.expertHandoffService = expertHandoffService;
        this.sessionService = sessionService;
    }

    public Map<String, Object> build(
            AgentRunExecutor.ExecuteRequest request,
            ApprovalGate approvalGate,
            QuestionGate questionGate,
            boolean memberSubRun) {
        WorkmateSession session = request.session();
        UUID sessionId = session.getId();
        String taskId = request.taskId();
        RunPersistenceContext persistenceContext = request.persistenceContext();

        Map<String, Object> variables = new HashMap<>(Map.of(
                WorkmateAgentHandler.WORKSPACE_ROOT_VAR, session.getWorkspaceRoot(),
                WorkmateAgentHandler.TASK_ID_VAR, taskId,
                WorkmateAgentHandler.PERMISSION_MODE_VAR, session.getPermissionMode()));
        String sessionModelId = session.getModelId();
        if (sessionModelId != null && !sessionModelId.isBlank()) {
            variables.put(WorkmateAgentHandler.MODEL_ID_VAR, sessionModelId);
        }
        String sessionEffort = session.getEffort();
        if (sessionEffort != null && !sessionEffort.isBlank()) {
            variables.put(WorkmateAgentHandler.EFFORT_VAR, sessionEffort);
        }
        List<String> enabledConnectorIds = session.getEnabledConnectorIds();
        if (!enabledConnectorIds.isEmpty()) {
            variables.put(WorkmateAgentHandler.ENABLED_CONNECTOR_IDS_VAR, enabledConnectorIds);
        }
        List<String> enabledSkillIds = session.getEnabledSkillIds();
        if (!enabledSkillIds.isEmpty()) {
            variables.put(WorkmateAgentHandler.ENABLED_SKILL_IDS_VAR, enabledSkillIds);
        }
        if (approvalGate != null) {
            variables.put(WorkmateAgentHandler.APPROVAL_GATE_VAR, approvalGate);
        }
        if (questionGate != null) {
            variables.put(WorkmateAgentHandler.QUESTION_GATE_VAR, questionGate);
        }
        String expertId = request.expertId() != null && !request.expertId().isBlank()
                ? request.expertId()
                : session.getExpertId();
        if (expertId != null && !expertId.isBlank()) {
            variables.put(WorkmateAgentHandler.EXPERT_ID_VAR, expertId);
        }
        expertRegistry.findExpert(expertId).ifPresent(expert -> {
            String taskRoot = officeArtifactLayoutService.taskRootRelative(expert, sessionId);
            if (taskRoot != null) {
                variables.put(WorkmateAgentHandler.OFFICE_TASK_ROOT_VAR, taskRoot);
            }
        });

        boolean teamExpertInvocation = expertId != null
                && !expertId.isBlank()
                && expertRegistry.findExpert(expertId).map(expert -> expert.isTeam()).orElse(false);
        String blackboardParentRunId = resolveBlackboardParentRunId(persistenceContext, taskId, teamExpertInvocation);
        if (blackboardParentRunId != null) {
            variables.put(
                    WorkmateAgentHandler.TEAM_BLACKBOARD_PATH_VAR,
                    TeamBlackboardContract.relativePath(blackboardParentRunId));
        }
        if (memberSubRun && persistenceContext.memberId() != null) {
            variables.put(WorkmateAgentHandler.TEAM_MEMBER_ID_VAR, persistenceContext.memberId());
        }
        if (request.topicBusMemberContext() != null) {
            TopicBusMemberPublisher publisher = request.topicBusMemberContext().publisher();
            variables.put(WorkmateAgentHandler.TEAM_TOPIC_BUS_PUBLISHER_VAR, publisher);
        }
        List<MentionItem> mentions = request.mentions() == null ? List.of() : request.mentions();
        String mentionsExpertId = request.expertId() != null && !request.expertId().isBlank()
                ? request.expertId()
                : session.getExpertId();
        String mentionsPrompt = mentionContextService.buildPromptSection(sessionId, mentionsExpertId, mentions);
        if (!mentionsPrompt.isBlank()) {
            variables.put(WorkmateAgentHandler.MENTIONS_PROMPT_VAR, mentionsPrompt);
        }
        if (!memberSubRun) {
            Integer pendingHandoffGeneration = session.getPendingHandoffGeneration();
            if (pendingHandoffGeneration != null
                    && pendingHandoffGeneration == session.getConversationGeneration()) {
                String handoffPrompt = expertHandoffService.buildHandoffPrompt(
                        sessionId,
                        session.getPendingHandoffFromExpertId(),
                        session.getExpertId(),
                        Path.of(session.getWorkspaceRoot()));
                variables.put(WorkmateAgentHandler.HANDOFF_PROMPT_VAR, handoffPrompt);
                sessionService.clearPendingHandoff(sessionId);
            }
        }

        String agentStateKey = AgentConversationKey.resolve(
                sessionId, taskId, persistenceContext, teamExpertInvocation, session.getConversationGeneration());
        variables.put(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, agentStateKey);
        return variables;
    }

    private static String resolveBlackboardParentRunId(
            RunPersistenceContext persistenceContext, String taskId, boolean teamExpertInvocation) {
        if (persistenceContext != null && persistenceContext.parentRunId() != null) {
            return persistenceContext.parentRunId();
        }
        if (teamExpertInvocation && persistenceContext != null) {
            return persistenceContext.runId();
        }
        return null;
    }
}
