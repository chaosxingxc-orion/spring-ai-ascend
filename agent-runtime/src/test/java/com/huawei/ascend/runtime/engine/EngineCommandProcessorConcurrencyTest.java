package com.huawei.ascend.runtime.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.api.DefaultEngineExecutionApi;
import com.huawei.ascend.runtime.engine.api.EnqueueEngineExecutionRequest;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.engine.support.RecordingTaskControlClient;
import com.huawei.ascend.runtime.queue.QueueManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class EngineCommandProcessorConcurrencyTest {

    @Test
    void processorOffloadsCommandsSoAgentExecutionsCanOverlap() throws Exception {
        BlockingAgentHandler handler = new BlockingAgentHandler();
        DefaultAgentRuntimeHandlerRegistry registry = new DefaultAgentRuntimeHandlerRegistry();
        registry.register("blocking-agent", handler);
        InternalEngineCommandGateway gateway = new InternalEngineCommandGateway(new QueueManager());
        EngineDispatcher dispatcher = new EngineDispatcher(registry, new RecordingTaskControlClient());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        EngineWorker processor = new EngineWorker(gateway, dispatcher, executor);
        DefaultEngineExecutionApi api = new DefaultEngineExecutionApi(new EngineCommandEventFactory(), gateway);
        processor.start();

        api.enqueueExecution(new EnqueueEngineExecutionRequest(scope("task-1"), input()));
        api.enqueueExecution(new EnqueueEngineExecutionRequest(scope("task-2"), input()));

        assertThat(handler.awaitBothStarted()).isTrue();

        handler.release();
        assertThat(handler.awaitBothFinished()).isTrue();
        executor.shutdownNow();
    }

    private static EngineExecutionScope scope(String taskId) {
        return new EngineExecutionScope("tenant", "user", "session", taskId, "blocking-agent");
    }

    private static EngineInput input() {
        return new EngineInput("text", List.of(), Map.of());
    }

    private static final class BlockingAgentHandler implements AgentRuntimeHandler {
        private final CountDownLatch bothStarted = new CountDownLatch(2);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch bothFinished = new CountDownLatch(2);
        private final AtomicInteger active = new AtomicInteger();

        @Override
        public String agentId() {
            return "blocking-agent";
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            active.incrementAndGet();
            bothStarted.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("agent execution was not released");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("agent execution interrupted", ex);
            } finally {
                active.decrementAndGet();
                bothFinished.countDown();
            }
            return Stream.of(Map.of("result_type", "answer", "output", "done"));
        }

        @Override
        public StreamAdapter resultAdapter() {
            return rawResults -> rawResults.map(rawResult -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) rawResult;
                return AgentExecutionResult.completed(String.valueOf(result.get("output")));
            });
        }

        boolean awaitBothStarted() throws InterruptedException {
            boolean started = bothStarted.await(5, TimeUnit.SECONDS);
            return started && active.get() == 2;
        }

        void release() {
            release.countDown();
        }

        boolean awaitBothFinished() throws InterruptedException {
            return bothFinished.await(5, TimeUnit.SECONDS);
        }

    }
}
