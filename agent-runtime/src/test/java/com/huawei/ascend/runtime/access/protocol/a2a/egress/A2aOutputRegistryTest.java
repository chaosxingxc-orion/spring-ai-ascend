package com.huawei.ascend.runtime.access.protocol.a2a.egress;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class A2aOutputRegistryTest {

    @Test
    void lateSubscriberAfterTerminalReplaysTheTerminalSoTheStreamCompletes() {
        A2aOutputRegistry registry = new A2aOutputRegistry();
        A2aOutputHandle handle = new A2aOutputHandle("tenant-1", "session-1", "task-1");
        registry.append(handle, output("task-1", true));

        List<A2aOutput> replayed = new ArrayList<>();
        registry.subscribe(handle, replayed::add);

        // A subscriber that arrives after the task already finished must still receive the
        // terminal output, otherwise the SSE caller waits forever.
        assertThat(replayed).extracting(A2aOutput::taskId).containsExactly("task-1");
        assertThat(replayed.get(replayed.size() - 1).terminal()).isTrue();
    }

    @Test
    void newSubscribersReplayOpenStreamOutputs() {
        A2aOutputRegistry registry = new A2aOutputRegistry();
        A2aOutputHandle handle = new A2aOutputHandle("tenant-1", "session-1", "task-1");
        registry.append(handle, output("task-1", false));

        List<A2aOutput> replayed = new ArrayList<>();
        registry.subscribe(handle, replayed::add);

        assertThat(replayed).extracting(A2aOutput::taskId).containsExactly("task-1");
    }

    @Test
    void aFinishedTaskTerminalDoesNotSuppressReplayForAnotherTaskInTheSameSession() {
        A2aOutputRegistry registry = new A2aOutputRegistry();
        registry.append(new A2aOutputHandle("tenant-1", "session-1", "task-1"), output("task-1", true));

        A2aOutputHandle current = new A2aOutputHandle("tenant-1", "session-1", "task-2");
        registry.append(current, output("task-2", false));

        List<A2aOutput> replayed = new ArrayList<>();
        registry.subscribe(current, replayed::add);

        // Task-scoped handle: task-2's subscriber sees only task-2's output, unaffected by task-1's terminal.
        assertThat(replayed).extracting(A2aOutput::taskId).containsExactly("task-2");
    }

    private static A2aOutput output(String taskId, boolean terminal) {
        return new A2aOutput("TaskStatus", taskId, null, null, terminal, Map.of());
    }
}
