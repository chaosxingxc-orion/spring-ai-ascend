package com.huawei.ascend.runtime.engine;

import com.huawei.ascend.runtime.queue.InternalEventQueue;
import com.huawei.ascend.runtime.queue.QueueManager;
import reactor.core.publisher.Flux;

/**
 * Engine-owned command gateway backed by the shared internal queue mechanism.
 */
public class InternalEngineCommandGateway implements EngineCommandGateway {

    private static final String QUEUE_ID = "engine:commands";

    private final InternalEventQueue<EngineCommandEvent> queue;

    public InternalEngineCommandGateway(QueueManager queueManager) {
        this.queue = queueManager.getOrCreate(QUEUE_ID, EngineCommandEvent.class);
    }

    @Override
    public boolean publish(EngineCommandEvent event) {
        queue.offer(event);
        return true;
    }

    @Override
    public Flux<EngineCommandEvent> commands() {
        return queue.stream();
    }

    public void close() {
        queue.close();
    }
}
