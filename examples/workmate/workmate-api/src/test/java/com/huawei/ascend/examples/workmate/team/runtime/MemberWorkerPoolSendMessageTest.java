package com.huawei.ascend.examples.workmate.team.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.team.backend.MemberBackendRegistry;
import com.huawei.ascend.examples.workmate.team.mailbox.MailboxMessage;
import com.huawei.ascend.examples.workmate.team.mailbox.MemberSendMessageOutcome;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class MemberWorkerPoolSendMessageTest {

    @Test
    void routeSendMessageDeliversToLeaderAndMarksExplicitHandback() {
        MemberWorkerPool pool = new MemberWorkerPool(
                "run-sm", "__lead__", new MemberBackendRegistry(List.of()), Executors.newVirtualThreadPerTaskExecutor());

        MemberSendMessageOutcome outcome = pool.routeSendMessage(
                "topic-researcher", "team-lead", "## 研究摘要\n\n正文", "初步调研摘要");

        assertThat(outcome.fromId()).isEqualTo("topic-researcher");
        assertThat(outcome.resolvedRecipient()).isEqualTo("__lead__");
        assertThat(outcome.summary()).isEqualTo("初步调研摘要");
        assertThat(pool.hasExplicitHandback("topic-researcher")).isTrue();
        assertThat(pool.explicitHandbackSummary("topic-researcher")).isEqualTo("初步调研摘要");

        List<MailboxMessage> leaderMail = pool.mailbox().drainUnread("__lead__");
        assertThat(leaderMail).hasSize(1);
        assertThat(leaderMail.get(0).body()).contains("研究摘要");
        assertThat(leaderMail.get(0).summary()).isEqualTo("初步调研摘要");
    }

    @Test
    void routeSendMessageToTeammateDoesNotMarkLeaderHandback() {
        MemberWorkerPool pool = new MemberWorkerPool(
                "run-peer", "__lead__", new MemberBackendRegistry(List.of()), Executors.newVirtualThreadPerTaskExecutor());

        MemberSendMessageOutcome outcome =
                pool.routeSendMessage("topic-researcher", "research-planner", "peer note", "note");

        assertThat(outcome.resolvedRecipient()).isEqualTo("research-planner");
        assertThat(pool.hasExplicitHandback("topic-researcher")).isFalse();
        assertThat(pool.mailbox().drainUnread("__lead__")).isEmpty();
        assertThat(pool.mailbox().drainUnread("research-planner")).hasSize(1);
    }

    @Test
    void invokeSendMessageToolDelegatesToRouteSendMessage() {
        MemberWorkerPool pool = new MemberWorkerPool(
                "run-tool", "__lead__", new MemberBackendRegistry(List.of()), Executors.newVirtualThreadPerTaskExecutor());

        Map<String, Object> result = pool.invokeSendMessageTool(
                "topic-researcher",
                Map.of("to", "team-lead", "content", "deliverable", "summary", "card title"));

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(pool.hasExplicitHandback("topic-researcher")).isTrue();
    }

    @Test
    void ingestRemoteHandbackIncrementsSequence() {
        MemberWorkerPool pool = new MemberWorkerPool(
                "run-ingest", "__lead__", new MemberBackendRegistry(List.of()), Executors.newVirtualThreadPerTaskExecutor());

        assertThat(pool.ingestRemoteHandback("remote-a", "first", "s1").sequence()).isEqualTo(1);
        assertThat(pool.ingestRemoteHandback("remote-a", "second", "s2").sequence()).isEqualTo(2);
        assertThat(pool.mailbox().drainUnread("__lead__")).hasSize(2);
    }
}
