package com.huawei.ascend.examples.workmate.team.runtime;

/**
 * Lifecycle state of a concurrent {@link MemberWorker}.
 *
 * <ul>
 *   <li>{@code READY} – registered, idle, never run yet</li>
 *   <li>{@code BUSY} – currently processing a batch of inbound messages</li>
 *   <li>{@code PAUSED} – finished a turn, idle, will auto-reawaken on a new message</li>
 *   <li>{@code STOPPED} – terminated, will not run again</li>
 * </ul>
 */
public enum MemberRuntimeState {
    READY,
    BUSY,
    PAUSED,
    STOPPED
}
