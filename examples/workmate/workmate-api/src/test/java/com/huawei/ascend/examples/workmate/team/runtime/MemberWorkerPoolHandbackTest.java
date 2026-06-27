package com.huawei.ascend.examples.workmate.team.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.team.backend.MemberBackendRegistry;
import com.huawei.ascend.examples.workmate.team.backend.MemberDescriptor;
import com.huawei.ascend.examples.workmate.team.backend.MemberRunResult;
import com.huawei.ascend.examples.workmate.team.mailbox.MailboxMessage;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class MemberWorkerPoolHandbackTest {

    @Test
    void skipsImplicitAssistantReplyWhenExplicitSendMessageUsed() throws Exception {
        MemberWorkerPool pool = new MemberWorkerPool(
                "run-3", "__lead__", new MemberBackendRegistry(List.of()), Executors.newVirtualThreadPerTaskExecutor());
        MemberDescriptor member = MemberDescriptor.of("topic-researcher", "谭溯源", "expert", null);

        pool.markExplicitHandback("topic-researcher", "摘要标题");
        pool.route("topic-researcher", "team-lead", "structured body", "摘要标题");

        invokeRouteReplyToLeader(pool, member, MemberRunResult.ok("assistant fallback text should not be routed"), false);

        List<MailboxMessage> leaderMail = pool.mailbox().drainUnread("__lead__");
        assertThat(leaderMail).hasSize(1);
        assertThat(leaderMail.get(0).body()).isEqualTo("structured body");
    }

    @Test
    void deliversA2aBackendOutputWithoutLocalWrapper() throws Exception {
        MemberWorkerPool pool = new MemberWorkerPool(
                "run-a2a", "__lead__", new MemberBackendRegistry(List.of()), Executors.newVirtualThreadPerTaskExecutor());
        MemberDescriptor member = MemberDescriptor.of("fund-analyst", "分析师", "fund-analyst", "a2a");

        invokeRouteReplyToLeader(
                pool,
                member,
                MemberRunResult.ok("## 远程研报\n\n正文", "a2a"),
                false);

        List<MailboxMessage> leaderMail = pool.mailbox().drainUnread("__lead__");
        assertThat(leaderMail).hasSize(1);
        assertThat(leaderMail.get(0).body()).isEqualTo("## 远程研报\n\n正文");
        assertThat(leaderMail.get(0).summary()).contains("remote handback");
    }

    private static void invokeRouteReplyToLeader(
            MemberWorkerPool pool, MemberDescriptor member, MemberRunResult result, boolean failed)
            throws Exception {
        Method method = MemberWorkerPool.class.getDeclaredMethod(
                "routeReplyToLeader", MemberDescriptor.class, MemberRunResult.class, boolean.class);
        method.setAccessible(true);
        method.invoke(pool, member, result, failed);
    }
}
