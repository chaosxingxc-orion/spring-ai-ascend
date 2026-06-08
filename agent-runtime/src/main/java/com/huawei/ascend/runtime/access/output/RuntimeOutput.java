package com.huawei.ascend.runtime.access.output;

import com.huawei.ascend.runtime.common.AgentResponseEvent;
import java.util.Objects;

public record RuntimeOutput(AgentResponseEvent event) {

    public RuntimeOutput {
        event = Objects.requireNonNull(event, "event");
    }

    public static RuntimeOutput from(AgentResponseEvent event) {
        return new RuntimeOutput(event);
    }

    public boolean terminal() {
        return event.terminal();
    }
}
