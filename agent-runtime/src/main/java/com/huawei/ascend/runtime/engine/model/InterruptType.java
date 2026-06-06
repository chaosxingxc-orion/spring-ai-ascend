package com.huawei.ascend.runtime.engine.model;

/**
 * Why an agent execution paused and is waiting. See engine model design §5.6.
 *
 * <ul>
 *   <li>{@code HUMAN_INPUT} — waiting for free-form user input.</li>
 *   <li>{@code APPROVAL} — waiting for a user approval decision.</li>
 *   <li>{@code WAITING_CHILD_AGENT} — waiting for a child agent task to finish.</li>
 * </ul>
 */
public enum InterruptType {
    HUMAN_INPUT,
    APPROVAL,
    WAITING_CHILD_AGENT
}
