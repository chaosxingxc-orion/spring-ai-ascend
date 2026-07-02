package com.huawei.ascend.runtime.engine.alpha;

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.openjiuwen.core.kernel.model.AgentEvent;
import com.openjiuwen.core.kernel.model.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Bridges a reactive {@link Flux} of framework {@link AgentEvent}s into the engine's
 * synchronous {@link AgentExecutionResult} stream. Terminal events map to a single
 * terminal result; thinking deltas surface as {@code OUTPUT} chunks; intermediate
 * process events (task lifecycle, tool, plan/node, strategy-specific) are dropped —
 * they carry no user-facing payload.
 *
 * <p>Two terminal-safety guarantees:
 * <ul>
 *   <li><b>First terminal wins.</b> Once a terminal event is mapped, the iterator stops;
 *       a second terminal in the same stream is swallowed. A double terminal is a defect
 *       to be diagnosed upstream, not a recovery signal to emit twice.</li>
 *   <li><b>No silent end.</b> If the upstream completes without any terminal event, a
 *       degraded {@code COMPLETED} result is appended — the runtime contract requires
 *       every execution to end in a terminal.</li>
 * </ul>
 */
public final class FluxToResultStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(FluxToResultStream.class);

    /** Default error code when a TASK_FAILED event carries no {@code errorCode} metadata. */
    static final String DEFAULT_FAILURE_CODE = "TASK_FAILED";

    public Stream<AgentExecutionResult> apply(Flux<AgentEvent> flux) {
        Iterator<AgentEvent> upstream = flux.toIterable().iterator();
        Iterator<AgentExecutionResult> adapted = new ResultIterator(upstream);
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(adapted, 0), false);
    }

    private static final class ResultIterator implements Iterator<AgentExecutionResult> {
        private final Iterator<AgentEvent> upstream;
        private AgentExecutionResult next;
        private boolean done;
        private boolean terminalSeen;

        ResultIterator(Iterator<AgentEvent> upstream) {
            this.upstream = upstream;
        }

        @Override
        public boolean hasNext() {
            if (done) {
                return false;
            }
            if (next != null) {
                return true;
            }
            if (terminalSeen) {
                done = true;
                return false;
            }
            while (upstream.hasNext()) {
                AgentExecutionResult mapped = map(upstream.next());
                if (mapped != null) {
                    next = mapped;
                    return true;
                }
            }
            LOGGER.warn("no terminal event in stream, degraded to COMPLETED");
            next = AgentExecutionResult.completed("");
            terminalSeen = true;
            return true;
        }

        @Override
        public AgentExecutionResult next() {
            if (next == null && !hasNext()) {
                throw new NoSuchElementException();
            }
            AgentExecutionResult result = next;
            next = null;
            return result;
        }

        /**
         * Maps one event to a result. Returns {@code null} for intermediate process
         * events that carry no user-facing payload and must not surface in the result
         * stream. Sets {@code terminalSeen} as a side effect when the event is terminal.
         */
        private AgentExecutionResult map(AgentEvent event) {
            EventType type = event.type();
            String data = event.data() != null ? event.data() : "";
            switch (type) {
                case TASK_COMPLETED:
                    terminalSeen = true;
                    return AgentExecutionResult.completed(data);
                case TASK_FAILED:
                    terminalSeen = true;
                    return AgentExecutionResult.failed(failureCode(event.metadata()), data);
                case TASK_PAUSED:
                    terminalSeen = true;
                    return AgentExecutionResult.interrupted(data);
                case TASK_CANCELLED:
                    terminalSeen = true;
                    return AgentExecutionResult.failed(EventType.TASK_CANCELLED.name(), data);
                case THINKING_DELTA:
                case THINKING_BLOCK_START:
                case THINKING_BLOCK_END:
                    return AgentExecutionResult.output(data);
                default:
                    return null;
            }
        }

        private static String failureCode(Map<String, String> metadata) {
            return metadata != null ? metadata.getOrDefault("errorCode", DEFAULT_FAILURE_CODE) : DEFAULT_FAILURE_CODE;
        }
    }
}
