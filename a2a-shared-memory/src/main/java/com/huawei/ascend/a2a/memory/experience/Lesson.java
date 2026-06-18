package com.huawei.ascend.a2a.memory.experience;

/**
 * One distilled, PII-stripped piece of cross-run collaboration experience.
 *
 * @param text          the lesson (already redacted before it reaches the store)
 * @param sourceAgentId which agent's conclusion it came from (may be null)
 * @param tsEpochMs     when it was last recorded / reinforced (recency)
 * @param reinforcement how many times it has been re-confirmed / proven useful (usefulness)
 */
public record Lesson(String text, String sourceAgentId, long tsEpochMs, int reinforcement) {

    /** New lesson, not yet reinforced. */
    public Lesson(String text, String sourceAgentId, long tsEpochMs) {
        this(text, sourceAgentId, tsEpochMs, 0);
    }

    /** A re-confirmed copy: bumps reinforcement and refreshes recency. */
    public Lesson refreshed(long tsEpochMs) {
        return new Lesson(text, sourceAgentId, tsEpochMs, reinforcement + 1);
    }
}
