package com.huawei.ascend.runtime.engine.adapters.openjiuwen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.runtime.common.RunEvent;
import com.huawei.ascend.runtime.common.RunEventType;
import com.huawei.ascend.runtime.common.RunPhase;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * W3 white-box: the openjiuwen {@link OpenJiuwenOutputConverter} maps openJiuwen's native
 * result items (result_type/output maps + raw token fragments) onto the neutral
 * {@code RunEvent} stream. No live LLM — stubbed native items prove the mapping.
 */
class OpenJiuwenOutputConverterTest {

    private final OpenJiuwenOutputConverter converter = new OpenJiuwenOutputConverter();

    @Test
    void answerResultBecomesAcceptedThenCompleted() throws Exception {
        List<RunEvent> events = collect(converter.convert(
                List.of(Map.of("result_type", "answer", "output", "pong"))));

        assertEquals(2, events.size());
        assertEquals(RunEventType.ACCEPTED, events.get(0).kind());
        assertEquals(RunEventType.COMPLETED, events.get(1).kind());
        assertEquals(RunPhase.SUCCEEDED, events.get(1).phase());
        assertEquals("pong", events.get(1).content());
    }

    @Test
    void errorResultBecomesFailed() throws Exception {
        List<RunEvent> events = collect(converter.convert(
                List.of(Map.of("result_type", "error", "output", "boom"))));

        assertEquals(RunEventType.FAILED, events.get(1).kind());
        assertEquals(RunPhase.FAILED, events.get(1).phase());
        assertEquals("boom", events.get(1).error());
    }

    @Test
    void interruptResultBecomesWaitingInput() throws Exception {
        List<RunEvent> events = collect(converter.convert(
                List.of(Map.of("result_type", "interrupt", "output", "need-input"))));

        assertEquals(RunPhase.WAITING_INPUT, events.get(1).phase());
        assertEquals("need-input", events.get(1).content());
    }

    @Test
    void streamingFragmentsBecomeChunksThenFinalAnswer() throws Exception {
        List<RunEvent> events = collect(converter.convert(List.of(
                "po",
                "ng",
                Map.of("result_type", "answer", "output", "pong"))));

        assertEquals(4, events.size());
        assertEquals(RunEventType.ACCEPTED, events.get(0).kind());
        assertEquals(RunEventType.CHUNK, events.get(1).kind());
        assertEquals("po", events.get(1).content());
        assertEquals(RunEventType.CHUNK, events.get(2).kind());
        assertEquals(RunEventType.COMPLETED, events.get(3).kind());
        assertTrue(events.get(3).terminal());
    }

    private static List<RunEvent> collect(Flow.Publisher<RunEvent> publisher) throws InterruptedException {
        List<RunEvent> out = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<RunEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(RunEvent item) {
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
