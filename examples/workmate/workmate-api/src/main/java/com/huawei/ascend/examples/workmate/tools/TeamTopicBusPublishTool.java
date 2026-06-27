package com.huawei.ascend.examples.workmate.tools;

import com.huawei.ascend.examples.workmate.spi.topic.TopicBusMemberPublisher;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Publishes to the scoped team topic bus during message-bus member runs. */
public final class TeamTopicBusPublishTool extends LocalFunction {

    TeamTopicBusPublishTool(TopicBusMemberPublisher publisher, String toolId) {
        super(
                buildCard(
                        toolId,
                        "Publish a message to the team message bus so subscribed peers receive it asynchronously.",
                        Map.of(
                                "topic",
                                optionalStringProp("Topic lane (defaults to your member topic)"),
                                "body",
                                stringProp("Message body to publish"))),
                inputs -> publish(publisher, inputs));
    }

    private static Map<String, Object> publish(TopicBusMemberPublisher publisher, Map<String, Object> inputs) {
        String body = asString(inputs.get("body"));
        if (body.isBlank()) {
            return WorkspacePathGuard.failure("body must not be blank");
        }
        String topic = asString(inputs.get("topic"));
        if (topic.isBlank()) {
            publisher.publishMemberTopic(body);
            topic = publisher.memberId();
        } else {
            publisher.publish(topic, body);
        }
        return WorkspacePathGuard.success(Map.of("topic", topic, "bytes", body.length()));
    }

    private static ToolCard buildCard(String id, String description, Map<String, Object> properties) {
        Map<String, Object> inputParams = new HashMap<>();
        inputParams.put("type", "object");
        inputParams.put("properties", properties);
        inputParams.put("required", List.of("body"));
        return ToolCard.builder()
                .id(id)
                .name(id)
                .description(description)
                .inputParams(inputParams)
                .build();
    }

    private static Map<String, Object> stringProp(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> optionalStringProp(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static String asString(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }
}
