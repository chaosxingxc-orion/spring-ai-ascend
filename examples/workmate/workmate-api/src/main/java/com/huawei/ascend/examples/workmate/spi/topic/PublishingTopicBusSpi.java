package com.huawei.ascend.examples.workmate.spi.topic;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Decorator that notifies on each {@link #publish} (for SSE / run_events projection).
 */
public final class PublishingTopicBusSpi implements TopicBusSpi {

    private final TopicBusSpi delegate;
    private final Consumer<PublishEvent> onPublish;

    public record PublishEvent(
            String topic, String authorId, String authorName, String body, String publishSource) {}

    public PublishingTopicBusSpi(TopicBusSpi delegate, Consumer<PublishEvent> onPublish) {
        this.delegate = delegate;
        this.onPublish = onPublish;
    }

    public TopicBusSpi delegate() {
        return delegate;
    }

    @Override
    public void subscribe(String subscriberId, Set<String> topics, Consumer<TopicBusMessage> handler) {
        delegate.subscribe(subscriberId, topics, handler);
    }

    @Override
    public void publish(String topic, String authorId, String authorName, String body) {
        publishWithSource(null, topic, authorId, authorName, body);
    }

    @Override
    public void publishWithSource(
            String publishSource, String topic, String authorId, String authorName, String body) {
        delegate.publish(topic, authorId, authorName, body);
        onPublish.accept(new PublishEvent(topic, authorId, authorName, body, publishSource));
    }

    @Override
    public List<TopicBusMessage> entries() {
        return delegate.entries();
    }

    @Override
    public String snapshotForTopics(Set<String> topics) {
        return delegate.snapshotForTopics(topics);
    }

    @Override
    public String summaryMarkdown() {
        return delegate.summaryMarkdown();
    }

    @Override
    public Map<String, Object> publishedPayload(
            String parentRunId, String topic, String authorId, String authorName, String preview) {
        return delegate.publishedPayload(parentRunId, topic, authorId, authorName, preview);
    }

    @Override
    public Map<String, Object> subscribedPayload(String parentRunId, String subscriberMemberId, List<String> topics) {
        return delegate.subscribedPayload(parentRunId, subscriberMemberId, topics);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
