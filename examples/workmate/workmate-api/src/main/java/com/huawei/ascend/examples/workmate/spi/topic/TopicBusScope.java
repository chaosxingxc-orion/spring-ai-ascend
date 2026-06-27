package com.huawei.ascend.examples.workmate.spi.topic;

/**
 * Scoped bus instance — typically one per team parent run.
 */
public record TopicBusScope(String scopeId, String teamId) {}
