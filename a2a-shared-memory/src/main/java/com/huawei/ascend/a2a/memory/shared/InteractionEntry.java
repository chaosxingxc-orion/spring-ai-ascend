package com.huawei.ascend.a2a.memory.shared;

/**
 * One edge of the team-collaboration record — "who did what to whom" — that no
 * single agent's own conclusions capture. The Coordinator authors DISPATCH /
 * HANDOVER / VALIDATE / OUTCOME; READ is auto-recorded when an agent reads another
 * agent's conclusion (the dependency edge). Together these form the interaction
 * graph of a collaboration.
 *
 * @param type            kind of interaction
 * @param actorAgentId    who acted (the reader, the coordinator, the validator…)
 * @param targetAgentId   who it concerns (the handover target / the owner being read); may be ""
 * @param key             the blackboard key involved (for READ/VALIDATE); may be ""
 * @param detail          human-readable note
 * @param tsEpochMs       when
 */
public record InteractionEntry(
        InteractionType type, String actorAgentId, String targetAgentId, String key, String detail, long tsEpochMs) {

    public enum InteractionType {
        DISPATCH,   // coordinator routed a task to an agent (by capability)
        HANDOVER,   // task handed from one agent to another
        READ,       // an agent read another agent's conclusion (dependency edge)
        VALIDATE,   // a result was validated / rejected
        OUTCOME     // final collaboration outcome
    }

    public InteractionEntry {
        actorAgentId = actorAgentId == null ? "" : actorAgentId;
        targetAgentId = targetAgentId == null ? "" : targetAgentId;
        key = key == null ? "" : key;
        detail = detail == null ? "" : detail;
    }
}
