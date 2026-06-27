package com.huawei.ascend.examples.workmate.team.mailbox;

/**
 * Kind of a {@link MailboxMessage} routed through the WorkMate team mailbox.
 *
 * <p>Mirrors the message kinds used by the reference workbench (agent teams) and openjiuwen
 * {@code agent_teams} so the application-layer orchestration stays framework-agnostic.</p>
 */
public enum MailboxMessageKind {
    /** Point-to-point working message to a specific member / the leader. */
    MESSAGE,
    /** Broadcast to all members (token consumption scales with member count). */
    BROADCAST,
    /** Request a member to gracefully shut down. */
    SHUTDOWN_REQUEST,
    /** A member's response to a shutdown request. */
    SHUTDOWN_RESPONSE,
    /** A plan approval response routed through the team mailbox. */
    PLAN_APPROVAL_RESPONSE
}
