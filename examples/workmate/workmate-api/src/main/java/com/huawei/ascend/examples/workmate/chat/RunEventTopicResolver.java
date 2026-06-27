package com.huawei.ascend.examples.workmate.chat;

/**
 * Topic lanes for W26 interim message-bus projection (ADR-013).
 * Derived from {@code run_events.event_name} at persist/SSE time.
 */
public final class RunEventTopicResolver {

    public static final String TOPIC_FIELD = "topic";

    private RunEventTopicResolver() {}

    public static String resolve(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            return "system";
        }
        if (eventName.startsWith("team.bus.")) {
            return "bus";
        }
        if (eventName.startsWith("team.")) {
            return "team";
        }
        if (eventName.startsWith("tool.")) {
            return "tool";
        }
        if (eventName.startsWith("approval.")) {
            return "approval";
        }
        if (eventName.startsWith("question.")) {
            return "question";
        }
        if (eventName.startsWith("artifact.")) {
            return "artifact";
        }
        if (eventName.startsWith("plan.")) {
            return "plan";
        }
        if (eventName.startsWith("usage.")) {
            return "usage";
        }
        if (eventName.startsWith("run.")) {
            return "run";
        }
        if (eventName.startsWith("message.")) {
            return "message";
        }
        int dot = eventName.indexOf('.');
        if (dot > 0) {
            return eventName.substring(0, dot);
        }
        return "system";
    }
}
