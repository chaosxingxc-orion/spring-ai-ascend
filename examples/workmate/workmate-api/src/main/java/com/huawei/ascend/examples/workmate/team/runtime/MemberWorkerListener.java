package com.huawei.ascend.examples.workmate.team.runtime;

import com.huawei.ascend.examples.workmate.team.backend.MemberDescriptor;
import com.huawei.ascend.examples.workmate.team.backend.MemberRunResult;
import com.huawei.ascend.examples.workmate.team.mailbox.MailboxMessage;
import java.util.List;

/**
 * Observes a {@link MemberWorker}'s lifecycle so the orchestration layer can emit
 * {@code team.member.*} events, route results back into the mailbox, and drive termination.
 *
 * <p>The worker itself stays a pure execution primitive: it does not decide how results are
 * routed (that policy — reply-to-sender, reply-to-leader, broadcast — lives in the wiring layer
 * to keep loop control and termination centralized).</p>
 */
public interface MemberWorkerListener {

    default void onStarted(MemberDescriptor member, List<MailboxMessage> inbound) {
    }

    default void onCompleted(MemberDescriptor member, List<MailboxMessage> inbound, MemberRunResult result) {
    }

    default void onFailed(MemberDescriptor member, List<MailboxMessage> inbound, MemberRunResult result) {
    }

    default void onStateChanged(MemberDescriptor member, MemberRuntimeState state) {
    }

    /** No-op listener. */
    MemberWorkerListener NOOP = new MemberWorkerListener() {
    };
}
