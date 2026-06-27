package com.huawei.ascend.examples.workmate.spi.topic;

/**
 * Opens scoped {@link TopicBusSpi} instances (local in-memory today; ascend runtime later).
 */
public interface TopicBusProvider {

    String providerId();

    TopicBusSpi open(TopicBusScope scope);
}
