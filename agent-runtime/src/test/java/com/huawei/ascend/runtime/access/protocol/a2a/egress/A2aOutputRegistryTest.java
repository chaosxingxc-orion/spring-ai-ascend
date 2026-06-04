package com.huawei.ascend.runtime.access.protocol.a2a.egress;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class A2aOutputRegistryTest {

    @Test
    void newSubscribersDoNotReplayTerminalOutputsFromPreviousStreams() {
        A2aOutputRegistry registry = new A2aOutputRegistry();
        A2aOutputHandle handle = new A2aOutputHandle("tenant-1", "session-1");
        registry.append(handle, output("task-1", true));

        List<A2aOutput> replayed = new ArrayList<>();
        registry.subscribe(handle, replayed::add);

        assertThat(replayed).isEmpty();
    }

    @Test
    void newSubscribersReplayOnlyOpenStreamOutputs() {
        A2aOutputRegistry registry = new A2aOutputRegistry();
        A2aOutputHandle handle = new A2aOutputHandle("tenant-1", "session-1");
        registry.append(handle, output("task-1", false));

        List<A2aOutput> replayed = new ArrayList<>();
        registry.subscribe(handle, replayed::add);

        assertThat(replayed).extracting(A2aOutput::taskId).containsExactly("task-1");
    }

    private static A2aOutput output(String taskId, boolean terminal) {
        return new A2aOutput("TaskStatus", taskId, null, null, terminal, Map.of());
    }
}
