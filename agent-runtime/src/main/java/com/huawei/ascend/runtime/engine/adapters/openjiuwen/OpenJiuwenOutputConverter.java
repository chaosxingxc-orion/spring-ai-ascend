package com.huawei.ascend.runtime.engine.adapters.openjiuwen;

import com.huawei.ascend.runtime.common.RunEvent;
import com.huawei.ascend.runtime.common.RunEventType;
import com.huawei.ascend.runtime.common.RunPhase;
import com.huawei.ascend.runtime.engine.spi.OutputConverter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

/**
 * Converts openJiuwen's native run output into the neutral {@code RunEvent} stream. Lives
 * entirely inside the openjiuwen adapter so the runtime core stays framework-neutral.
 *
 * <p>Mirrors the proven openJiuwen result contract ({@code result_type ∈ {answer, interrupt,
 * error}} with an {@code output} payload): an ACCEPTED event is emitted first, each native item
 * maps to a CHUNK / COMPLETED / WAITING_INPUT / FAILED {@code RunEvent}. Typeless items (raw
 * streaming token fragments) become CHUNK events.
 */
public final class OpenJiuwenOutputConverter implements OutputConverter {

    @Override
    public Flow.Publisher<RunEvent> convert(Object frameworkStream) {
        List<RunEvent> events = new ArrayList<>();
        events.add(RunEvent.accepted());
        int sequence = 1;
        if (frameworkStream instanceof Iterable<?> items) {
            for (Object item : items) {
                events.add(toRunEvent(sequence++, item));
            }
        } else if (frameworkStream != null) {
            events.add(toRunEvent(sequence, frameworkStream));
        }
        return syncPublisher(events);
    }

    private static RunEvent toRunEvent(int sequence, Object item) {
        String resultType = null;
        String output = "";
        if (item instanceof Map<?, ?> map) {
            resultType = asString(map.get("result_type"));
            output = asString(map.get("output"));
        } else if (item != null) {
            output = String.valueOf(item);
        }
        return switch (resultType == null ? "" : resultType) {
            case "answer" -> RunEvent.completed(sequence, output);
            case "interrupt" -> new RunEvent(sequence, RunEventType.CHUNK, RunPhase.WAITING_INPUT, output, null);
            case "error" -> RunEvent.failed(sequence, output);
            default -> RunEvent.chunk(sequence, output);
        };
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** Minimal synchronous JDK Flow publisher: delivers all items on request, then completes. */
    private static Flow.Publisher<RunEvent> syncPublisher(List<RunEvent> items) {
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
}
