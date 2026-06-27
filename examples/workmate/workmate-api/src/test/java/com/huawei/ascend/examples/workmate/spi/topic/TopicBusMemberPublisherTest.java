package com.huawei.ascend.examples.workmate.spi.topic;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.spi.topic.local.LocalInMemoryTopicBus;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TopicBusMemberPublisherTest {

    @Test
    void publishesWithMidRunSource() {
        AtomicReference<PublishingTopicBusSpi.PublishEvent> captured = new AtomicReference<>();
        PublishingTopicBusSpi bus =
                new PublishingTopicBusSpi(new LocalInMemoryTopicBus(), captured::set);
        try {
            TopicBusMemberPublisher publisher = new TopicBusMemberPublisher(bus, "member-a", "A");
            publisher.publishMemberTopic("incremental");
            assertThat(captured.get().publishSource()).isEqualTo("mid-run");
            assertThat(bus.entries().get(0).body()).isEqualTo("incremental");
        } finally {
            bus.close();
        }
    }
}
