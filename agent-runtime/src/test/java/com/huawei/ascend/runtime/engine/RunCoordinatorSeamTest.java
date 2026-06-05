package com.huawei.ascend.runtime.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.runtime.common.InvocationRequest;
import com.huawei.ascend.runtime.common.RunEvent;
import com.huawei.ascend.runtime.common.RunEventType;
import com.huawei.ascend.runtime.common.RunPhase;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentDriver;
import com.huawei.ascend.runtime.engine.spi.AgentDriver;
import com.huawei.ascend.runtime.engine.spi.OutputConverter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * W2 neutral-seam test: a stub {@link AgentDriver} (framework-id "stub") emits an opaque native
 * stream; the {@link RunCoordinator} routes it through the driver's {@link OutputConverter} and
 * produces the neutral {@code Flow.Publisher<RunEvent>}. No framework, no LLM — proves the seam.
 */
class RunCoordinatorSeamTest {

    @Test
    void coordinatorProducesNeutralEventStreamFromDriver() throws Exception {
        AgentDriver driver = new AbstractAgentDriver() {
            @Override
            public String name() {
                return "stub-agent";
            }

            @Override
            public String frameworkId() {
                return "stub";
            }

            @Override
            public Object invoke(InvocationRequest request) {
                // opaque "framework-native" stream — the runtime never inspects it
                return List.of("pong");
            }

            @Override
            public OutputConverter outputConverter() {
                return nativeStream -> syncPublisher(List.of(
                        RunEvent.accepted(),
                        RunEvent.chunk(1, "pong"),
                        RunEvent.completed(2, "pong")));
            }
        };

        RunCoordinator coordinator = new RunCoordinator(driver);
        coordinator.start();
        assertTrue(coordinator.isRunning());
        assertEquals("stub", coordinator.frameworkId());

        List<RunEvent> events = collect(coordinator.stream(
                new InvocationRequest("req-1", "stub-agent", "sess-1", "ping")));

        assertEquals(3, events.size());
        assertEquals(RunEventType.ACCEPTED, events.get(0).kind());
        assertEquals(RunEventType.CHUNK, events.get(1).kind());
        assertEquals(RunEventType.COMPLETED, events.get(2).kind());
        assertTrue(events.get(2).terminal());
        assertEquals("pong", events.get(2).content());
        assertEquals(RunPhase.SUCCEEDED, events.get(2).phase());
    }

    /** Minimal synchronous JDK Flow publisher: delivers all items on request, then completes. */
    private static <T> Flow.Publisher<T> syncPublisher(List<T> items) {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            private int idx = 0;
            private boolean cancelled = false;

            @Override
            public void request(long n) {
                while (n-- > 0 && idx < items.size() && !cancelled) {
                    subscriber.onNext(items.get(idx++));
                }
                if (idx >= items.size() && !cancelled) {
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                cancelled = true;
            }
        });
    }

    private static <T> List<T> collect(Flow.Publisher<T> publisher) throws InterruptedException {
        List<T> out = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<T>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T item) {
                out.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        assertTrue(done.await(5, TimeUnit.SECONDS), "stream did not complete");
        return out;
    }
}
