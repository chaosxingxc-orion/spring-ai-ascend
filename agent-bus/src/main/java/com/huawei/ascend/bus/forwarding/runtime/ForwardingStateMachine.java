package com.huawei.ascend.bus.forwarding.runtime;

import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;

/**
 * Pure state-transition engine for the C3 forwarding outbox / inbox substrates.
 *
 * <p>Holds no state and performs no IO: given a current status and an event, it
 * returns the legal next status, or throws
 * {@link IllegalStateTransitionException} for any transition not in the tables
 * below. Ports and the dispatcher call this engine to validate a transition
 * before persisting, so the legal-transition set is defined in exactly one place
 * and exercised directly by the harness.
 *
 * <p>{@code null} current status denotes a record that does not yet exist; only
 * the creation events (outbox {@link OutboxEvent#ENQUEUE}, inbox
 * {@link InboxEvent#ARRIVE_NEW} / {@link InboxEvent#ARRIVE_DUPLICATE}) accept a
 * {@code null} current.
 *
 * <p>Authority: {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §4}
 * (outbox / inbox migration tables — the single source of truth); mirror the
 * L2 tables exactly when editing.
 */
public final class ForwardingStateMachine {

    /** Events that drive an outbox record through its lifecycle. */
    public enum OutboxEvent {
        ENQUEUE, BEGIN_DISPATCH, ACK, RETRY, EXHAUST_RETRIES, EXPIRE
    }

    /** Events that drive an inbox record through its lifecycle. */
    public enum InboxEvent {
        ARRIVE_NEW, ARRIVE_DUPLICATE, CONSUME, REJECT
    }

    /**
     * Compute the next outbox status, or throw if the transition is illegal.
     *
     * @param current current status, or {@code null} for a not-yet-created record
     * @param event   the triggering event
     * @return the legal next status
     * @throws IllegalStateTransitionException if no such transition exists
     */
    public ForwardingStatus.Outbox transitOutbox(ForwardingStatus.Outbox current, OutboxEvent event) {
        if (current == null) {
            if (event == OutboxEvent.ENQUEUE) {
                return ForwardingStatus.Outbox.PENDING;
            }
            throw illegal("outbox", current, event);
        }
        return switch (current) {
            case PENDING -> switch (event) {
                case BEGIN_DISPATCH -> ForwardingStatus.Outbox.DISPATCHING;
                case EXPIRE -> ForwardingStatus.Outbox.EXPIRED;
                default -> throw illegal("outbox", current, event);
            };
            case DISPATCHING -> switch (event) {
                case ACK -> ForwardingStatus.Outbox.ACKED;
                case RETRY -> ForwardingStatus.Outbox.RETRY_SCHEDULED;
                case EXHAUST_RETRIES -> ForwardingStatus.Outbox.DLQ;
                case EXPIRE -> ForwardingStatus.Outbox.EXPIRED;
                default -> throw illegal("outbox", current, event);
            };
            case RETRY_SCHEDULED -> switch (event) {
                case BEGIN_DISPATCH -> ForwardingStatus.Outbox.DISPATCHING;
                case EXHAUST_RETRIES -> ForwardingStatus.Outbox.DLQ;
                case EXPIRE -> ForwardingStatus.Outbox.EXPIRED;
                default -> throw illegal("outbox", current, event);
            };
            case ACKED, DLQ, EXPIRED -> throw new IllegalStateTransitionException(
                    "outbox: " + current + " is terminal; no outbound transition");
        };
    }

    /**
     * Compute the next inbox status, or throw if the transition is illegal.
     *
     * @param current current status, or {@code null} for a not-yet-created record
     * @param event   the triggering event
     * @return the legal next status
     * @throws IllegalStateTransitionException if no such transition exists
     */
    public ForwardingStatus.Inbox transitInbox(ForwardingStatus.Inbox current, InboxEvent event) {
        if (current == null) {
            return switch (event) {
                case ARRIVE_NEW -> ForwardingStatus.Inbox.RECEIVED;
                case ARRIVE_DUPLICATE -> ForwardingStatus.Inbox.DUPLICATE_SUPPRESSED;
                default -> throw illegal("inbox", current, event);
            };
        }
        return switch (current) {
            case RECEIVED -> switch (event) {
                case CONSUME -> ForwardingStatus.Inbox.CONSUMED;
                case REJECT -> ForwardingStatus.Inbox.REJECTED;
                default -> throw illegal("inbox", current, event);
            };
            case DUPLICATE_SUPPRESSED, CONSUMED, REJECTED -> throw new IllegalStateTransitionException(
                    "inbox: " + current + " is terminal; no outbound transition");
        };
    }

    private static IllegalStateTransitionException illegal(String side, Object current, Object event) {
        return new IllegalStateTransitionException(
                side + ": illegal transition " + current + " + " + event);
    }

    /** Raised when a state/event pair has no legal transition. */
    public static final class IllegalStateTransitionException extends IllegalStateException {
        public IllegalStateTransitionException(String message) {
            super(message);
        }
    }
}
