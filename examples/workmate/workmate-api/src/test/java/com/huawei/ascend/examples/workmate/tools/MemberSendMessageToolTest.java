package com.huawei.ascend.examples.workmate.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.team.backend.MemberBackendRegistry;
import com.huawei.ascend.examples.workmate.team.runtime.MemberWorkerPool;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class MemberSendMessageToolTest {

    @Test
    void routesTeamLeadHandbackAndMarksExplicit() throws Exception {
        MemberWorkerPool pool = new MemberWorkerPool(
                "run-handback", "__lead__", new MemberBackendRegistry(List.of()), Executors.newVirtualThreadPerTaskExecutor());
        MemberSendMessageTool tool = new MemberSendMessageTool("send_message__test", "topic-researcher", pool);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.invoke(
                Map.of(
                        "to", "team-lead",
                        "content", "## 研究摘要\n\n正文",
                        "summary", "初步调研摘要"),
                Map.of());

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(pool.hasExplicitHandback("topic-researcher")).isTrue();
        assertThat(pool.explicitHandbackSummary("topic-researcher")).isEqualTo("初步调研摘要");
        List<com.huawei.ascend.examples.workmate.team.mailbox.MailboxMessage> leaderMail =
                pool.mailbox().drainUnread("__lead__");
        assertThat(leaderMail).hasSize(1);
        assertThat(leaderMail.get(0).body()).contains("研究摘要");
        assertThat(leaderMail.get(0).summary()).isEqualTo("初步调研摘要");
    }

    @Test
    void requiresContent() throws Exception {
        MemberWorkerPool pool = new MemberWorkerPool(
                "run-2", "__lead__", new MemberBackendRegistry(List.of()), Executors.newVirtualThreadPerTaskExecutor());
        MemberSendMessageTool tool = new MemberSendMessageTool("send_message__test2", "topic-researcher", pool);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.invoke(Map.of("to", "team-lead", "content", ""), Map.of());

        assertThat(result.get("success")).isEqualTo(false);
    }
}
