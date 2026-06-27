package com.huawei.ascend.examples.workmate.team.agent;

import com.huawei.ascend.examples.workmate.chat.SessionPersistenceService;
import com.huawei.ascend.examples.workmate.chat.RunPersistenceContext;
import com.huawei.ascend.examples.workmate.office.TeamMemberDefinition;
import com.huawei.ascend.examples.workmate.session.WorkmateSession;
import com.huawei.ascend.examples.workmate.team.runtime.MemberWorkerListener;
import com.huawei.ascend.examples.workmate.team.runtime.MemberWorkerPool;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Bridges openjiuwen member invoke → WorkMate concurrent {@link MemberWorkerPool}.
 *
 * <p>Member sub-runs are dispatched through the pool's mailbox + worker + backend registry, so they
 * execute concurrently and stream tool / reasoning / message deltas as team-surface run_events.</p>
 */
public record TeamAgentMemberExecutionContext(
        WorkmateSession session,
        SseEmitter emitter,
        AtomicBoolean clientConnected,
        RunPersistenceContext parentContext,
        String parentTaskId,
        List<TeamMemberDefinition> members,
        MemberWorkerPool pool,
        SessionPersistenceService sessionPersistenceService,
        MemberWorkerListener memberWorkerListener) {

    public TeamMemberDefinition resolveMember(String memberKey) {
        if (memberKey == null || memberKey.isBlank()) {
            return null;
        }
        return members.stream()
                .filter(member -> member.id().equals(memberKey) || member.name().equals(memberKey))
                .findFirst()
                .orElse(null);
    }
}
