package com.huawei.ascend.runtime.engine.model;

/**
 * How an agent invokes another agent. See engine model design §5.5.
 *
 * <ul>
 *   <li>{@code INLINE} — the target agent runs within the caller's execution.</li>
 *   <li>{@code CHILD_TASK} — the target agent runs as a separate child task.</li>
 * </ul>
 */
public enum AgentCallMode {
    INLINE,
    CHILD_TASK
}
