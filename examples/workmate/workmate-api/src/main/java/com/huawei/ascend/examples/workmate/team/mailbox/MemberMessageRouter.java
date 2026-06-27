package com.huawei.ascend.examples.workmate.team.mailbox;

/**
 * Resolves a routing token into a concrete mailbox recipient and parses {@code @member}
 * style addressing so users / members can bypass the leader and talk directly to a member.
 *
 * <p>Routing rules (aligned with openjiuwen gateway {@code message_handler} and the reference workbench):</p>
 * <ul>
 *   <li>{@code @all} / {@code *} / {@code all} → broadcast to every member</li>
 *   <li>{@code @main} / {@code main} / {@code lead} / {@code leader} / {@code team-lead} / {@code team_lead} → the team leader</li>
 *   <li>{@code @name} / {@code name} → the named member</li>
 *   <li>empty / blank → the team leader (default entry point)</li>
 * </ul>
 */
public final class MemberMessageRouter {

    private final String leaderId;

    public MemberMessageRouter(String leaderId) {
        if (leaderId == null || leaderId.isBlank()) {
            throw new IllegalArgumentException("leaderId is required");
        }
        this.leaderId = leaderId;
    }

    public String leaderId() {
        return leaderId;
    }

    /**
     * Resolve a routing token (with or without a leading {@code @}) into a mailbox recipient.
     *
     * @return {@link MailboxMessage#BROADCAST} for fan-out, the leader id for main, otherwise
     *         the trimmed member token.
     */
    public String resolveRecipient(String token) {
        if (token == null || token.isBlank()) {
            return leaderId;
        }
        String normalized = token.trim();
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1).trim();
        }
        if (normalized.isEmpty()) {
            return leaderId;
        }
        String lower = normalized.toLowerCase();
        return switch (lower) {
            case "all", "*", "everyone", "team" -> MailboxMessage.BROADCAST;
            case "main", "lead", "leader", "team-lead", "team_lead", "team_leader" -> leaderId;
            default -> normalized;
        };
    }

    /**
     * Parse a free-form user message that may begin with an {@code @target} prefix.
     *
     * <p>Examples: {@code "@designer refine the hero"} → (designer, "refine the hero");
     * {@code "just go"} → (leader, "just go").</p>
     */
    public Routed parseUserMessage(String rawMessage) {
        if (rawMessage == null) {
            return new Routed(leaderId, "");
        }
        String trimmed = rawMessage.strip();
        if (trimmed.startsWith("@")) {
            int sep = indexOfFirstWhitespace(trimmed);
            if (sep > 0) {
                String token = trimmed.substring(0, sep);
                String body = trimmed.substring(sep + 1).strip();
                return new Routed(resolveRecipient(token), body);
            }
            // Only a bare @target with no body.
            return new Routed(resolveRecipient(trimmed), "");
        }
        return new Routed(leaderId, trimmed);
    }

    private static int indexOfFirstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    /** Resolved routing target and the remaining message body. */
    public record Routed(String recipient, String body) {
        public boolean isBroadcast() {
            return MailboxMessage.BROADCAST.equals(recipient);
        }
    }
}
