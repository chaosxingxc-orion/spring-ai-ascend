package com.huawei.ascend.examples.workmate.spi.topic;

/**
 * Member-scoped publish handle injected into agent execution context during message-bus runs.
 */
public record TopicBusMemberPublisher(TopicBusSpi bus, String memberId, String memberName) {

    public void publish(String topic, String body) {
        bus.publishWithSource("mid-run", topic, memberId, memberName, body);
    }

    public void publishMemberTopic(String body) {
        bus.publishWithSource("mid-run", memberId, memberId, memberName, body);
    }
}
