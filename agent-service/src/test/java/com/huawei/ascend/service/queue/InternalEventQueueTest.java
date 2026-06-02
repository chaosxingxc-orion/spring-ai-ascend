package com.huawei.ascend.service.queue;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalEventQueueTest {

    @Test
    void streamReceivesOfferedValuesAndCloseCompletesConsumer() {
        InternalEventQueue<String> queue = new InMemoryInternalEventQueue<>("test.queue");

        StepVerifier.create(queue.stream())
                .then(() -> queue.offer("first"))
                .expectNext("first")
                .then(queue::close)
                .verifyComplete();
    }

    @Test
    void streamReceivesValuesOfferedBeforeConsumerSubscribes() {
        InternalEventQueue<String> queue = new InMemoryInternalEventQueue<>("test.queue");

        queue.offer("early");

        StepVerifier.create(queue.stream().take(1))
                .expectNext("early")
                .verifyComplete();
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
                .verifyComplete();
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
                .verifyComplete();

        executor.shutdownNow();
    }

    @Test
    void sizeReflectsPendingValuesUntilConsumerReceivesThem() {
        InternalEventQueue<String> queue = new InMemoryInternalEventQueue<>("test.queue");

        queue.offer("first");

        assertThat(queue.size()).isEqualTo(1);
        StepVerifier.create(queue.stream().take(1))
                .expectNext("first")
                .verifyComplete();
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
                .verify();
    }
}
