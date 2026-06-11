package com.huawei.ascend.runtime.engine.spi;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Capacity-bounded producer/consumer channel. The adapter thread publishes events while
 * (possibly on another thread) the runtime drains them in FIFO order. A poison
 * sentinel enqueued by {@link #close()} terminates the draining stream, so a
 * blocking framework run can stream its callbacks out while the run is in flight.
 */
public final class QueueTrajectoryChannel implements TrajectoryChannel {

    private static final Logger LOG = LoggerFactory.getLogger(QueueTrajectoryChannel.class);

    /**
     * Caps the in-flight event count so a fast producer (e.g. a FULL-level run emitting large
     * reasoning/tool payloads) sheds load rather than exhausting the heap. Trajectory is best-effort
     * telemetry: dropping events under pressure is correct; blocking the run or OOM is not.
     */
    private static final int CAPACITY = 10_000;

    private static final TrajectoryEvent POISON = new TrajectoryEvent(
            -1L, null, 0L, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

    // Internally unbounded so close()'s poison pill is always accepted (drain always terminates);
    // the real-event count is capped in publish() via the size check below.
    private final BlockingQueue<TrajectoryEvent> queue = new LinkedBlockingQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean warnedDrop = new AtomicBoolean(false);

    @Override
    public void publish(TrajectoryEvent event) {
        if (event == null || closed.get()) {
            return;
        }
        if (queue.size() >= CAPACITY) {
            if (warnedDrop.compareAndSet(false, true)) {
                LOG.warn("trajectory queue at capacity={}; dropping further events for this invocation", CAPACITY);
            }
            return;
        }
        queue.add(event);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            queue.add(POISON); // unbounded queue always accepts the sentinel → drain always terminates
        }
    }

    @Override
    public Stream<TrajectoryEvent> drain() {
        Iterator<TrajectoryEvent> it = new Iterator<>() {
            private TrajectoryEvent next;
            private boolean done;

            @Override
            public boolean hasNext() {
                if (done) {
                    return false;
                }
                if (next != null) {
                    return true;
                }
                try {
                    TrajectoryEvent taken = queue.take();
                    if (taken == POISON) {
                        done = true;
                        return false;
                    }
                    next = taken;
                    return true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    done = true;
                    return false;
                }
            }

            @Override
            public TrajectoryEvent next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                TrajectoryEvent current = next;
                next = null;
                return current;
            }
        };
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(it, Spliterator.ORDERED | Spliterator.NONNULL), false);
    }
}
