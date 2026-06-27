package com.huawei.ascend.examples.workmate.team.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class TeamMailboxTest {

    @Test
    void pointToPointDeliversToSingleRecipient() {
        TeamMailbox mailbox = new TeamMailbox("run-1");
        mailbox.registerRecipient("lead");
        mailbox.registerRecipient("designer");
        mailbox.registerRecipient("writer");

        List<String> delivered = mailbox.deliver(
                MailboxMessage.create("run-1", "lead", "designer", MailboxMessageKind.MESSAGE, "hi", null, null));

        assertThat(delivered).containsExactly("designer");
        assertThat(mailbox.unreadCount("designer")).isEqualTo(1);
        assertThat(mailbox.unreadCount("writer")).isZero();
        assertThat(mailbox.drainUnread("designer")).hasSize(1);
        assertThat(mailbox.unreadCount("designer")).isZero();
    }

    @Test
    void broadcastFansOutToEveryoneExceptSender() {
        TeamMailbox mailbox = new TeamMailbox("run-1");
        mailbox.registerRecipient("lead");
        mailbox.registerRecipient("designer");
        mailbox.registerRecipient("writer");

        List<String> delivered = mailbox.deliver(
                MailboxMessage.create(
                        "run-1", "lead", MailboxMessage.BROADCAST, MailboxMessageKind.BROADCAST, "kickoff", null, null));

        assertThat(delivered).containsExactlyInAnyOrder("designer", "writer");
        assertThat(mailbox.unreadCount("lead")).isZero();
    }

    @Test
    void listenerFiresOnDelivery() {
        TeamMailbox mailbox = new TeamMailbox("run-1");
        mailbox.registerRecipient("designer");
        CopyOnWriteArrayList<String> woken = new CopyOnWriteArrayList<>();
        mailbox.addListener((message, recipients) -> woken.addAll(recipients));

        mailbox.deliver(MailboxMessage.create("run-1", "lead", "designer", MailboxMessageKind.MESSAGE, "go", null, null));

        assertThat(woken).containsExactly("designer");
    }

    @Test
    void routerResolvesAddressingTokens() {
        MemberMessageRouter router = new MemberMessageRouter("lead");

        assertThat(router.resolveRecipient(null)).isEqualTo("lead");
        assertThat(router.resolveRecipient("@all")).isEqualTo(MailboxMessage.BROADCAST);
        assertThat(router.resolveRecipient("@main")).isEqualTo("lead");
        assertThat(router.resolveRecipient("team-lead")).isEqualTo("lead");
        assertThat(router.resolveRecipient("team_lead")).isEqualTo("lead");
        assertThat(router.resolveRecipient("team_leader")).isEqualTo("lead");
        assertThat(router.resolveRecipient("@designer")).isEqualTo("designer");
        assertThat(router.resolveRecipient("writer")).isEqualTo("writer");
    }

    @Test
    void routerParsesUserMessagePrefix() {
        MemberMessageRouter router = new MemberMessageRouter("lead");

        MemberMessageRouter.Routed direct = router.parseUserMessage("@designer refine the hero section");
        assertThat(direct.recipient()).isEqualTo("designer");
        assertThat(direct.body()).isEqualTo("refine the hero section");

        MemberMessageRouter.Routed toLead = router.parseUserMessage("just keep going");
        assertThat(toLead.recipient()).isEqualTo("lead");
        assertThat(toLead.body()).isEqualTo("just keep going");

        MemberMessageRouter.Routed broadcast = router.parseUserMessage("@all status check");
        assertThat(broadcast.isBroadcast()).isTrue();
        assertThat(broadcast.body()).isEqualTo("status check");
    }
}
