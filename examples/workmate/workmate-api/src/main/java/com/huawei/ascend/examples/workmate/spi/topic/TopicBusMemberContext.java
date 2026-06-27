package com.huawei.ascend.examples.workmate.spi.topic;

import java.util.Set;

/** Bus + member identity for a single message-bus member sub-run. */
public record TopicBusMemberContext(TopicBusSpi bus, String memberId, String memberName, Set<String> subscribedTopics) {

    public TopicBusMemberPublisher publisher() {
        return new TopicBusMemberPublisher(bus, memberId, memberName);
    }
}
