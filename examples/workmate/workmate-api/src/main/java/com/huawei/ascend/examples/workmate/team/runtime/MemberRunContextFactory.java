package com.huawei.ascend.examples.workmate.team.runtime;

import com.huawei.ascend.examples.workmate.team.backend.MemberDescriptor;
import com.huawei.ascend.examples.workmate.team.backend.MemberRunContext;
import com.huawei.ascend.examples.workmate.team.mailbox.MailboxMessage;
import java.util.List;

/**
 * Builds a {@link MemberRunContext} for one member turn from the batch of inbound messages a
 * worker drained from its mailbox.
 *
 * <p>The live team bridge supplies a concrete factory that assembles an
 * {@code AgentRunExecutor.ExecuteRequest} (session, sub-run id, persistence context, SSE emitter)
 * so member output streams through WorkMate's normal event pipeline. Tests supply a fake.</p>
 */
@FunctionalInterface
public interface MemberRunContextFactory {

    /**
     * @param member       the member about to run
     * @param combinedBody the combined inbound text for this turn
     * @param inbound      the raw inbound messages drained for this turn (FIFO)
     */
    MemberRunContext create(MemberDescriptor member, String combinedBody, List<MailboxMessage> inbound);
}
