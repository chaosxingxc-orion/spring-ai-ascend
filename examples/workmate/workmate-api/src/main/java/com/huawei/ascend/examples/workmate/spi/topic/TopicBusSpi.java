package com.huawei.ascend.examples.workmate.spi.topic;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Topic message bus SPI — incubated in workmate for ADR-013 W26 / REQ-P2-05.
 *
 * <p>Candidate upstream target: {@code agent-runtime} native {@code publish/subscribe(topic)}.
 * Implementation stays in workmate until upstream accepts the SPI; do not patch agent-runtime.
 */
public interface TopicBusSpi extends AutoCloseable {

    String INGRESS_TOPIC = "ingress";
    String ALL_TOPICS = "*";

    void subscribe(String subscriberId, Set<String> topics, Consumer<TopicBusMessage> handler);

    void publish(String topic, String authorId, String authorName, String body);

    default void publishWithSource(
            String publishSource, String topic, String authorId, String authorName, String body) {
        publish(topic, authorId, authorName, body);
    }

    List<TopicBusMessage> entries();

    String snapshotForTopics(Set<String> topics);

    String summaryMarkdown();

    Map<String, Object> publishedPayload(
            String parentRunId, String topic, String authorId, String authorName, String preview);

    Map<String, Object> subscribedPayload(String parentRunId, String subscriberMemberId, List<String> topics);

    static boolean topicMatches(Set<String> subscribedTopics, String publishedTopic) {
        if (subscribedTopics == null || subscribedTopics.isEmpty()) {
            return false;
        }
        if (subscribedTopics.contains(ALL_TOPICS)) {
            return true;
        }
        return subscribedTopics.contains(publishedTopic);
    }

    @Override
    void close();
}
