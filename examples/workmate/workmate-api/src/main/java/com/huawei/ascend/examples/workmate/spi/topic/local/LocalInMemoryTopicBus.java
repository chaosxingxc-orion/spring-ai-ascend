package com.huawei.ascend.examples.workmate.spi.topic.local;

import com.huawei.ascend.examples.workmate.spi.topic.TopicBusMessage;
import com.huawei.ascend.examples.workmate.spi.topic.TopicBusSpi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Per-scope in-memory topic bus with virtual-thread async dispatch (W26 workmate reference impl).
 */
public final class LocalInMemoryTopicBus implements TopicBusSpi {

    private final List<TopicBusMessage> entries = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final ExecutorService dispatchPool = Executors.newVirtualThreadPerTaskExecutor();

    private record Subscription(String subscriberId, Set<String> topics, Consumer<TopicBusMessage> handler) {}

    @Override
    public void subscribe(String subscriberId, Set<String> topics, Consumer<TopicBusMessage> handler) {
        subscriptions.add(new Subscription(subscriberId, topics, handler));
    }

    @Override
    public void publish(String topic, String authorId, String authorName, String body) {
        TopicBusMessage message = new TopicBusMessage(topic, authorId, authorName, body != null ? body.trim() : "");
        entries.add(message);
        for (Subscription subscription : subscriptions) {
            if (subscription.subscriberId().equals(message.authorId())) {
                continue;
            }
            if (!TopicBusSpi.topicMatches(subscription.topics(), message.topic())) {
                continue;
            }
            dispatchPool.submit(() -> subscription.handler().accept(message));
        }
    }

    @Override
    public List<TopicBusMessage> entries() {
        return List.copyOf(entries);
    }

    @Override
    public String snapshotForTopics(Set<String> topics) {
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (TopicBusMessage entry : entries) {
            if (TopicBusSpi.topicMatches(topics, entry.topic())) {
                sb.append("[")
                        .append(entry.topic())
                        .append("] ")
                        .append(entry.authorName() != null ? entry.authorName() : entry.authorId())
                        .append(": ")
                        .append(entry.body())
                        .append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    @Override
    public String summaryMarkdown() {
        if (entries.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("# Team message bus\n\n");
        for (TopicBusMessage entry : entries) {
            sb.append("## [")
                    .append(entry.topic())
                    .append("] ")
                    .append(entry.authorName() != null ? entry.authorName() : entry.authorId())
                    .append("\n\n")
                    .append(entry.body())
                    .append("\n\n---\n\n");
        }
        return sb.toString().trim();
    }

    @Override
    public Map<String, Object> publishedPayload(
            String parentRunId, String topic, String authorId, String authorName, String preview) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parentRunId", parentRunId);
        payload.put("topic", topic);
        payload.put("authorMemberId", authorId);
        payload.put("authorMemberName", authorName);
        payload.put("preview", preview);
        return payload;
    }

    @Override
    public Map<String, Object> subscribedPayload(String parentRunId, String subscriberMemberId, List<String> topics) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parentRunId", parentRunId);
        payload.put("subscriberMemberId", subscriberMemberId);
        payload.put("topics", topics);
        return payload;
    }

    @Override
    public void close() {
        dispatchPool.shutdownNow();
    }
}
