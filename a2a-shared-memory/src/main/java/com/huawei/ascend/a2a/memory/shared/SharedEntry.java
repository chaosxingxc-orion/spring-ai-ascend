package com.huawei.ascend.a2a.memory.shared;

/**
 * One versioned entry on the A2A shared blackboard. Writes append (never
 * overwrite), so a key accumulates a provenance-carrying history; {@code version}
 * is 1-based and increases per append for the same key.
 *
 * @param key           the blackboard key
 * @param value         the value written
 * @param writerAgentId who wrote it (the key's owner is the writer of version 1)
 * @param version       1-based append version for this key
 * @param tsEpochMs     write time
 */
public record SharedEntry(String key, String value, String writerAgentId, int version, long tsEpochMs) {
}
