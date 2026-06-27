package com.huawei.ascend.examples.workmate.spi.topic.local;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.spi.topic.TopicBusMessage;
import com.huawei.ascend.examples.workmate.spi.topic.TopicBusSpi;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LocalInMemoryTopicBusTest {

    @Test
    void dispatchesMatchingTopicsAsyncAndSkipsSelf() throws Exception {
        try (TopicBusSpi bus = new LocalInMemoryTopicBus()) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<TopicBusMessage> received = new AtomicReference<>();
            bus.subscribe("member-a", Set.of(TopicBusSpi.INGRESS_TOPIC), entry -> {
                received.set(entry);
                latch.countDown();
            });
            bus.publish(TopicBusSpi.INGRESS_TOPIC, "user", "用户", "brief");
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(received.get().body()).isEqualTo("brief");
        }
    }

    @Test
    void wildcardSubscriptionMatchesMemberTopics() throws Exception {
        try (TopicBusSpi bus = new LocalInMemoryTopicBus()) {
            CountDownLatch latch = new CountDownLatch(1);
            bus.subscribe("member-b", Set.of(TopicBusSpi.ALL_TOPICS), entry -> latch.countDown());
            bus.publish("member-a", "member-a", "A", "finding");
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void topicMatchesHelper() {
        assertThat(TopicBusSpi.topicMatches(Set.of(TopicBusSpi.INGRESS_TOPIC), TopicBusSpi.INGRESS_TOPIC))
                .isTrue();
        assertThat(TopicBusSpi.topicMatches(Set.of(TopicBusSpi.INGRESS_TOPIC), "member-a")).isFalse();
        assertThat(TopicBusSpi.topicMatches(Set.of(TopicBusSpi.ALL_TOPICS), "any")).isTrue();
    }
}
