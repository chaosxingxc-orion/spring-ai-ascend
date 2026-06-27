package com.huawei.ascend.examples.workmate.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.examples.workmate.spi.topic.TopicBusMemberPublisher;
import com.huawei.ascend.examples.workmate.spi.topic.TopicBusSpi;
import com.huawei.ascend.examples.workmate.spi.topic.local.LocalInMemoryTopicBus;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TeamTopicBusPublishToolTest {

    @Test
    void publishesToMemberTopicWhenTopicOmitted() throws Exception {
        try (TopicBusSpi bus = new LocalInMemoryTopicBus()) {
            TopicBusMemberPublisher publisher = new TopicBusMemberPublisher(bus, "member-a", "A");
            TeamTopicBusPublishTool tool = new TeamTopicBusPublishTool(publisher, "test-bus-publish");
            Map<String, Object> result =
                    (Map<String, Object>) tool.invoke(Map.of("body", "incremental finding"));
            assertThat(result.get("success")).isEqualTo(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            assertThat(data.get("topic")).isEqualTo("member-a");
            assertThat(bus.entries()).hasSize(1);
            assertThat(bus.entries().get(0).body()).isEqualTo("incremental finding");
        }
    }

    @Test
    void publishesToExplicitTopic() throws Exception {
        try (TopicBusSpi bus = new LocalInMemoryTopicBus()) {
            TopicBusMemberPublisher publisher = new TopicBusMemberPublisher(bus, "member-a", "A");
            TeamTopicBusPublishTool tool = new TeamTopicBusPublishTool(publisher, "test-bus-publish");
            Map<String, Object> result =
                    (Map<String, Object>) tool.invoke(Map.of("topic", "ingress", "body", "lane note"));
            assertThat(result.get("success")).isEqualTo(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            assertThat(data.get("topic")).isEqualTo("ingress");
            assertThat(bus.entries().get(0).topic()).isEqualTo("ingress");
        }
    }

    @Test
    void rejectsBlankBody() throws Exception {
        try (TopicBusSpi bus = new LocalInMemoryTopicBus()) {
            TopicBusMemberPublisher publisher = new TopicBusMemberPublisher(bus, "member-a", "A");
            TeamTopicBusPublishTool tool = new TeamTopicBusPublishTool(publisher, "test-bus-publish");
            Map<String, Object> result = (Map<String, Object>) tool.invoke(Map.of("body", "  "));
            assertThat(result.get("success")).isEqualTo(false);
        }
    }
}
