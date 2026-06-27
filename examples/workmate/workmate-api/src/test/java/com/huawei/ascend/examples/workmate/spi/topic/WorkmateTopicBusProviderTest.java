package com.huawei.ascend.examples.workmate.spi.topic;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.config.WorkmateTopicBusProperties;
import com.huawei.ascend.examples.workmate.spi.topic.ascend.AscendRuntimeTopicBusProvider;
import com.huawei.ascend.examples.workmate.spi.topic.local.LocalInMemoryTopicBusProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkmateTopicBusProviderTest {

    @Test
    void selectsConfiguredProvider() {
        WorkmateTopicBusProperties properties = new WorkmateTopicBusProperties();
        properties.setProvider(LocalInMemoryTopicBusProvider.PROVIDER_ID);
        WorkmateTopicBusProvider provider = new WorkmateTopicBusProvider(
                List.of(new LocalInMemoryTopicBusProvider(), new AscendRuntimeTopicBusProvider()),
                properties);
        assertThat(provider.providerId()).isEqualTo(LocalInMemoryTopicBusProvider.PROVIDER_ID);
        try (TopicBusSpi bus = provider.open(new TopicBusScope("parent", "team-a"))) {
            assertThat(bus).isNotNull();
        }
    }

    @Test
    void ascendRuntimeProviderOpensWorkmateIncubatedBus() {
        WorkmateTopicBusProperties properties = new WorkmateTopicBusProperties();
        properties.setProvider(AscendRuntimeTopicBusProvider.PROVIDER_ID);
        WorkmateTopicBusProvider provider = new WorkmateTopicBusProvider(
                List.of(new LocalInMemoryTopicBusProvider(), new AscendRuntimeTopicBusProvider()),
                properties);
        assertThat(provider.providerId()).isEqualTo(AscendRuntimeTopicBusProvider.PROVIDER_ID);
        try (TopicBusSpi bus = provider.open(new TopicBusScope("parent", "team-a"))) {
            bus.publish(TopicBusSpi.INGRESS_TOPIC, "user", "用户", "hi");
            assertThat(bus.entries().size()).isEqualTo(1);
        }
    }
}
