package com.huawei.ascend.examples.workmate.team.agent;

import com.huawei.ascend.examples.workmate.agent.AgentRunExecutor.ExecuteRequest;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.team.backend.MemberDescriptor;
import com.huawei.ascend.examples.workmate.team.backend.MemberRunContext;
import com.huawei.ascend.examples.workmate.team.runtime.MemberRunContextFactory;
import com.huawei.ascend.examples.workmate.team.runtime.MemberWorkerListener;
import com.huawei.ascend.examples.workmate.team.runtime.MemberWorkerPool;
import com.huawei.ascend.examples.workmate.tools.MemberSendMessageToolFactory;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.agent.TeamMember;
import com.openjiuwen.agent_teams.agent.TeamMemberRuntime;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.harness.DeepAgent;
import java.lang.reflect.Field;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Rebinds predefined member runtimes with model-aware {@link DeepAgent} configs from
 * {@link TeamAgentSpec#getAgents()}.
 *
 * <p>openjiuwen {@code TeamBackend.registerPredefinedMembers()} eagerly creates member
 * runtimes without model settings. WorkMate replaces those runtimes before {@code send_message}
 * executes teammates.</p>
 */
@Component
public class WorkmateTeamMemberRuntimeBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger(WorkmateTeamMemberRuntimeBootstrap.class);

    private final MemberSendMessageToolFactory memberSendMessageToolFactory;

    public WorkmateTeamMemberRuntimeBootstrap(MemberSendMessageToolFactory memberSendMessageToolFactory) {
        this.memberSendMessageToolFactory = memberSendMessageToolFactory;
    }

    public void rebindMemberRuntimes(
            TeamAgent leader,
            TeamAgentSpec spec,
            MemberExecutionListener listener,
            TeamAgentMemberExecutionContext executionContext) {
        if (leader == null || leader.getTeamBackend() == null || spec == null) {
            return;
        }
        TeamBackend backend = leader.getTeamBackend();
        Map<String, TeamMemberRuntime> runtimes = memberRuntimes(backend);
        String teamName = spec.getTeamName();
        int rebound = 0;
        for (TeamMemberSpec memberSpec : spec.getPredefinedMembers()) {
            if (memberSpec == null || memberSpec.getRoleType() == TeamRole.HUMAN_AGENT) {
                continue;
            }
            String memberName = memberSpec.getMemberName();
            if (memberName == null || memberName.isBlank()) {
                continue;
            }
            TeamMember member = backend.getMember(memberName);
            if (member == null) {
                continue;
            }
            Session session = backend.ensureMemberSession(memberName, member.getAgentCard());
            DeepAgent agent = WorkmateMemberDeepAgentFactory.create(
                    spec,
                    teamName,
                    memberName,
                    member.getAgentCard(),
                    member.getDesc(),
                    member.getPrompt());
            registerWorker(executionContext, memberName);
            runtimes.put(
                    memberName,
                    new WorkmateInstrumentedMemberRuntime(
                            member, agent, session, listener, executionContext));
            rebound++;
        }
        if (rebound > 0) {
            LOG.debug("Rebound {} team member runtime(s) for team {}", rebound, teamName);
        }
    }

    /**
     * Register the member as a concurrent worker in the team's {@link MemberWorkerPool}, supplying a
     * {@link MemberRunContextFactory} that assembles the in-process {@link ExecuteRequest} so member
     * output streams through WorkMate's normal event pipeline (team-surface run_events).
     */
    private void registerWorker(TeamAgentMemberExecutionContext executionContext, String memberName) {
        if (executionContext == null || executionContext.pool() == null) {
            return;
        }
        TeamMemberDefinition memberDef = executionContext.resolveMember(memberName);
        if (memberDef == null) {
            return;
        }
        MemberWorkerPool pool = executionContext.pool();
        if (pool.worker(memberDef.id()) != null) {
            return;
        }
        MemberDescriptor descriptor =
                MemberDescriptor.of(memberDef.id(), memberDef.name(), memberDef.expertId(), null);
        MemberRunContextFactory factory = (member, combinedBody, inbound) -> {
            String subRunId = TeamAgentSessionBinding.subRunId(executionContext.parentTaskId(), memberDef.id());
            RunPersistenceContext subContext = executionContext
                    .sessionPersistenceService()
                    .beginSubRun(
                            executionContext.session().getId(),
                            subRunId,
                            executionContext.parentTaskId(),
                            memberDef.id(),
                            memberDef.name());
            ExecuteRequest request = new ExecuteRequest(
                    executionContext.session(),
                    combinedBody,
                    subRunId,
                    memberDef.expertId(),
                    subContext,
                    executionContext.emitter(),
                    executionContext.clientConnected(),
                    true,
                    false,
                    false);
            return new MemberRunContext(member, request);
        };
        MemberWorkerListener workerListener = executionContext.memberWorkerListener() != null
                ? executionContext.memberWorkerListener()
                : MemberWorkerListener.NOOP;
        pool.addMember(descriptor, factory, workerListener);
        String agentTag = TeamToolRegistrationContext.sharedAgentTag(executionContext.session().getId());
        memberSendMessageToolFactory.register(
                executionContext.session().getId(),
                agentTag,
                memberDef.id(),
                pool);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, TeamMemberRuntime> memberRuntimes(TeamBackend backend) {
        try {
            Field field = TeamBackend.class.getDeclaredField("memberRuntimes");
            field.setAccessible(true);
            return (Map<String, TeamMemberRuntime>) field.get(backend);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot access TeamBackend member runtimes", ex);
        }
    }
}
