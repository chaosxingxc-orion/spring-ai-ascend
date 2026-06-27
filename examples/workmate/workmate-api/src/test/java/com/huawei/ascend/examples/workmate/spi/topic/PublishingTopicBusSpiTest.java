package com.huawei.ascend.examples.workmate.spi.topic;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.spi.topic.local.LocalInMemoryTopicBus;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PublishingTopicBusSpiTest {

    @Test
    void notifiesListenerOnPublishAndDelegatesEntries() {
        AtomicReference<PublishingTopicBusSpi.PublishEvent> captured = new AtomicReference<>();
        PublishingTopicBusSpi bus =
                new PublishingTopicBusSpi(new LocalInMemoryTopicBus(), captured::set);
        try {
            bus.publish("ingress", "user", "用户", "brief");
            assertThat(captured.get().body()).isEqualTo("brief");
            assertThat(captured.get().publishSource()).isNull();
            assertThat(bus.entries()).hasSize(1);
            assertThat(bus.delegate()).isNotNull();
        } finally {
            bus.close();
        }
    }

    @Test
    void publishWithSourcePassesSourceToListener() {
        AtomicReference<PublishingTopicBusSpi.PublishEvent> captured = new AtomicReference<>();
        PublishingTopicBusSpi bus =
                new PublishingTopicBusSpi(new LocalInMemoryTopicBus(), captured::set);
        try {
            bus.publishWithSource("orchestrator", "ingress", "user", "用户", "brief");
            assertThat(captured.get().publishSource()).isEqualTo("orchestrator");
        } finally {
            bus.close();
        }
    }
}
