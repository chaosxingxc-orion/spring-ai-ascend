package com.huawei.ascend.runtime.queue;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.HashSet;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalEventQueueTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalEventQueueTest.class);
    // Bound every verification so a regression that starves the consumer fails fast here
    // instead of blocking the whole Surefire fork indefinitely.
    private static final Duration VERIFY_TIMEOUT = Duration.ofSeconds(30);

    @Test
    void streamReceivesOfferedValuesAndCloseCompletesConsumer() {
        InternalEventQueue<String> queue = new InMemoryInternalEventQueue<>("test.queue");

        StepVerifier.create(queue.stream())
                .then(() -> queue.offer("first"))
                .expectNext("first")
                .then(queue::close)
                .expectComplete().verify(VERIFY_TIMEOUT);
    }

    @Test
    void streamReceivesValuesOfferedBeforeConsumerSubscribes() {
        InternalEventQueue<String> queue = new InMemoryInternalEventQueue<>("test.queue");

        queue.offer("early");

        StepVerifier.create(queue.stream().take(1))
                .expectNext("early")
                .expectComplete().verify(VERIFY_TIMEOUT);
    }

    @Test
    void secondConsumerIsRejected() {
        InternalEventQueue<String> queue = new InMemoryInternalEventQueue<>("test.queue");

        StepVerifier.create(queue.stream().take(1))
                .then(() -> assertThatThrownBy(queue::stream)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("single consumer"))
                .then(() -> queue.offer("first"))
                .expectNext("first")
                .expectComplete().verify(VERIFY_TIMEOUT);
    }

    @Test
    void offerIsSafeForMultipleProducers() throws Exception {
        InternalEventQueue<Integer> queue = new InMemoryInternalEventQueue<>("test.queue");
        int producerCount = 4;
        int eventsPerProducer = 25;
        CountDownLatch ready = new CountDownLatch(producerCount);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(producerCount);

        for (int producer = 0; producer < producerCount; producer++) {
            int producerOffset = producer * eventsPerProducer;
            executor.submit(() -> {
                ready.countDown();
                start.await(2, TimeUnit.SECONDS);
                IntStream.range(0, eventsPerProducer)
                        .forEach(value -> queue.offer(producerOffset + value));
                return null;
            });
        }

        assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
        StepVerifier.create(queue.stream().take(producerCount * eventsPerProducer))
                .then(start::countDown)
                .expectNextCount(producerCount * eventsPerProducer)
                .expectComplete().verify(VERIFY_TIMEOUT);

        executor.shutdownNow();
    }

    @Test
    void stressPreservesAllValuesFromConcurrentProducers() throws Exception {
        InternalEventQueue<Integer> queue = new InMemoryInternalEventQueue<>("test.queue.stress");
        int producerCount = 8;
        int eventsPerProducer = 250;
        int expectedEvents = producerCount * eventsPerProducer;
        CountDownLatch ready = new CountDownLatch(producerCount);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(producerCount);

        for (int producer = 0; producer < producerCount; producer++) {
            int producerOffset = producer * eventsPerProducer;
            executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                IntStream.range(0, eventsPerProducer)
                        .forEach(value -> queue.offer(producerOffset + value));
                return null;
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        StepVerifier.create(queue.stream().take(expectedEvents).collectList())
                .then(start::countDown)
                .assertNext(values -> {
                    assertThat(values).hasSize(expectedEvents);
                    assertThat(new HashSet<>(values)).hasSize(expectedEvents);
                    assertThat(values).contains(0, expectedEvents - 1);
                })
                .expectComplete().verify(VERIFY_TIMEOUT);

        executor.shutdownNow();
    }

    @Test
    void stressLatencyStaysFarBelowAgentExecutionTime(TestInfo testInfo) throws Exception {
        InternalEventQueue<TimedEvent> queue = new InMemoryInternalEventQueue<>("test.queue.latency");
        int producerCount = 8;
        int eventsPerProducer = 500;
        int expectedEvents = producerCount * eventsPerProducer;
        CountDownLatch ready = new CountDownLatch(producerCount);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(producerCount);
        ConcurrentLinkedQueue<Long> consumeLatencyMicros = new ConcurrentLinkedQueue<>();
        long testStartedNanos = System.nanoTime();

        for (int producer = 0; producer < producerCount; producer++) {
            int producerOffset = producer * eventsPerProducer;
            executor.submit(() -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                IntStream.range(0, eventsPerProducer)
                        .forEach(value -> queue.offer(new TimedEvent(producerOffset + value, System.nanoTime())));
                return null;
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        StepVerifier.create(queue.stream()
                        .doOnNext(value -> consumeLatencyMicros.add((System.nanoTime() - value.createdNanos()) / 1_000L))
                        .take(expectedEvents)
                        .collectList())
                .then(start::countDown)
                .assertNext(values -> {
                    long totalDurationMs = elapsedMs(testStartedNanos);
                    assertThat(values).hasSize(expectedEvents);
                    assertThat(values.stream().map(TimedEvent::id).collect(java.util.stream.Collectors.toSet()))
                            .hasSize(expectedEvents);
                    assertThat(consumeLatencyMicros).hasSize(expectedEvents);
                    LongSummaryStatistics latencyMicros = consumeLatencyMicros.stream()
                            .mapToLong(Long::longValue)
                            .summaryStatistics();
                    LOGGER.info("queue latency smoke test={} events={} producers={} totalDurationMs={} avgLatencyMicros={} maxLatencyMicros={}",
                            testInfo.getDisplayName(),
                            expectedEvents,
                            producerCount,
                            totalDurationMs,
                            (long) latencyMicros.getAverage(),
                            latencyMicros.getMax());
                    assertThat(totalDurationMs).isLessThan(5_000L);
                })
                .expectComplete().verify(VERIFY_TIMEOUT);

        executor.shutdownNow();
    }

    @Test
    void sizeReflectsPendingValuesUntilConsumerReceivesThem() {
        InternalEventQueue<String> queue = new InMemoryInternalEventQueue<>("test.queue");

        queue.offer("first");

        assertThat(queue.size()).isEqualTo(1);
        StepVerifier.create(queue.stream().take(1))
                .expectNext("first")
                .expectComplete().verify(VERIFY_TIMEOUT);
        assertThat(queue.size()).isZero();
    }

    @Test
    void offerAfterCloseIsRejected() {
        InternalEventQueue<String> queue = new InMemoryInternalEventQueue<>("test.queue");
        queue.close();

        assertThatThrownBy(() -> queue.offer("late"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void streamWaitsWithoutBusyPollingUntilOfferArrives() {
        InternalEventQueue<String> queue = new InMemoryInternalEventQueue<>("test.queue");

        StepVerifier.withVirtualTime(queue::stream)
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(5))
                .then(() -> queue.offer("late"))
                .expectNext("late")
                .thenCancel()
                .verify(VERIFY_TIMEOUT);
    }

    private record TimedEvent(int id, long createdNanos) {
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
