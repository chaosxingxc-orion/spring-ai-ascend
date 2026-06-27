package com.huawei.ascend.examples.workmate.spi.topic;

/**
 * Neutral topic-bus message (ADR-013 W26 ascend SPI incubation in workmate).
 */
public record TopicBusMessage(String topic, String authorId, String authorName, String body) {}
