package com.huawei.ascend.examples.workmate.team.agent;

import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.team.runtime.MemberWorkerPool;
import com.openjiuwen.agent_teams.agent.TeamMember;
import com.openjiuwen.agent_teams.agent.TeamMemberRuntime;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.harness.DeepAgent;
import java.util.Map;

/**
 * Bridges openjiuwen {@code TeamBackend.runMember} (the leader's {@code send_message} tool) to
 * WorkMate's asynchronous, mailbox-driven member runtime.
 *
 * <p>When {@link TeamAgentMemberExecutionContext} is present (the {@code openjiuwen-team} path),
 * {@code send_message} is <b>fire-and-continue</b>: the message is routed into the member's
 * mailbox and a generic acknowledgement is returned immediately. The member then runs on its own
 * {@link com.huawei.ascend.examples.workmate.team.runtime.MemberWorker} (virtual thread), streams
 * its tool / reasoning / message deltas as team-surface run_events, and its produced output is
 * routed back to the leader's inbox. The leader is re-awakened by the bridge when that reply
 * arrives. Member lifecycle events are emitted by the worker, not here.</p>
 *
 * <p>When no execution context is present (other topologies), the member runs inline and the
 * lifecycle is emitted synchronously via {@link MemberExecutionListener}.</p>
 */
public class WorkmateInstrumentedMemberRuntime extends TeamMemberRuntime {

    private final MemberExecutionListener listener;
    private final TeamAgentMemberExecutionContext executionContext;

    public WorkmateInstrumentedMemberRuntime(
            TeamMember member,
            DeepAgent agent,
            Session session,
            MemberExecutionListener listener,
            TeamAgentMemberExecutionContext executionContext) {
        super(member, agent, session);
        this.listener = listener != null ? listener : MemberExecutionListener.NOOP;
        this.executionContext = executionContext;
    }

    @Override
    public Object invoke(Object content) {
        String memberName = getMember().getMemberName();
        if (executionContext != null) {
            TeamMemberDefinition memberDef = executionContext.resolveMember(memberName);
            MemberWorkerPool pool = executionContext.pool();
            if (memberDef != null && pool != null) {
                // Fire-and-continue: route the message into the member's mailbox and return an
                // acknowledgement immediately. The member runs on its own worker; its lifecycle and
                // produced output are surfaced (and routed back to the leader) by the worker layer.
                pool.route(pool.leaderId(), memberDef.id(), contentToMessage(content));
                return acknowledgement(memberDef);
            }
        }

        // Synchronous fallback (non-openjiuwen topology): run inline and emit lifecycle here.
        listener.onMemberExecution(MemberExecutionListener.MemberExecutionEvent.STARTED, memberName, null);
        try {
            Object result = super.invoke(content);
            listener.onMemberExecution(
                    MemberExecutionListener.MemberExecutionEvent.COMPLETED,
                    memberName,
                    null);
            return result;
        } catch (RuntimeException ex) {
            listener.onMemberExecution(
                    MemberExecutionListener.MemberExecutionEvent.FAILED,
                    memberName,
                    ex.getMessage());
            throw ex;
        }
    }

    private static String acknowledgement(TeamMemberDefinition memberDef) {
        String who = memberDef.name() != null && !memberDef.name().isBlank() ? memberDef.name() : memberDef.id();
        return "已把任务派发给成员「" + who + "」，该成员正在并行处理。"
                + "其产出会作为一条新消息回到你的收件箱，届时你会被重新唤醒。"
                + "在收到该成员的回传之前，请勿假设其结果已经返回；可以继续派发其他成员或等待回传。";
    }

    private static String contentToMessage(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof Map<?, ?> map) {
            Object message = map.get("message");
            if (message == null) {
                message = map.get("query");
            }
            if (message == null) {
                message = map.get("content");
            }
            if (message != null) {
                return message.toString();
            }
        }
        return content.toString();
    }
}
