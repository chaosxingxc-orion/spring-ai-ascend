package com.huawei.ascend.runtime.access.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.runtime.common.AgentResponseEvent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OutputChannelRegistryTest {

    @Test
    void replaysBufferedOutputsAndCompletesAfterTerminal() {
        OutputChannelRegistry registry = new OutputChannelRegistry();
        RuntimeOutputHandle handle = new RuntimeOutputHandle("tenant-1", "session-1", "task-1");
        OutputChannel channel = registry.getOrCreate(handle);

        channel.write(acceptedOutput("request-1", "task-1"));
        channel.write(finalOutput("request-1", "task-1", "pong"));

        List<RuntimeOutput> replayed = Objects.requireNonNull(registry.getOrCreate(handle)
                .stream()
                .collectList()
                .block(Duration.ofSeconds(2)));

        assertThat(replayed).hasSize(2);
        assertThat(replayed.get(0).event().status().name()).isEqualTo("ACCEPTED");
        assertThat(replayed.get(1).event().output()).isEqualTo("pong");
        assertThat(replayed.get(1).terminal()).isTrue();
    }

    @Test
    void rejectsPostTerminalWritesWithoutChangingHistory() {
        OutputChannel channel = new OutputChannel();

        channel.write(acceptedOutput("request-1", "task-1"));
        channel.write(finalOutput("request-1", "task-1", "pong"));
        List<RuntimeOutput> historyBeforeLateWrite = channel.history();

        assertThatThrownBy(() -> channel.write(acceptedOutput("request-2", "task-1")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(channel.history()).isEqualTo(historyBeforeLateWrite);
        List<RuntimeOutput> replayed = Objects.requireNonNull(channel.stream()
                .collectList()
                .block(Duration.ofSeconds(2)));
        assertThat(replayed).isEqualTo(historyBeforeLateWrite);
    }

    @Test
    void concurrentWritesAreConsistentBetweenHistoryAndReplay() throws Exception {
        OutputChannel channel = new OutputChannel();
        int writeCount = 32;
        CountDownLatch ready = new CountDownLatch(writeCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(writeCount);

        try {
            for (int index = 0; index < writeCount; index++) {
                int outputIndex = index;
                executor.execute(() -> {
                    ready.countDown();
                    await(start);
                    channel.write(acceptedOutput("request-" + outputIndex, "task-1"));
                });
            }

            assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            channel.write(finalOutput("request-terminal", "task-1", "pong"));

            List<RuntimeOutput> history = channel.history();
            List<RuntimeOutput> replayed = Objects.requireNonNull(channel.stream()
                    .collectList()
                    .block(Duration.ofSeconds(2)));

            assertThat(history).hasSize(writeCount + 1);
            assertThat(replayed).isEqualTo(history);
        } finally {
            executor.shutdownNow();
        }
    }

    private static RuntimeOutput acceptedOutput(String requestId, String taskId) {
        return RuntimeOutput.from(AgentResponseEvent.taskAccepted(
                requestId, "tenant-1", "user-1", "agent-1", "session-1", taskId));
    }

    private static RuntimeOutput finalOutput(String requestId, String taskId, String output) {
        return RuntimeOutput.from(AgentResponseEvent.finalText(
                requestId, "tenant-1", "user-1", "agent-1", "session-1", taskId, output, Map.of()));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
