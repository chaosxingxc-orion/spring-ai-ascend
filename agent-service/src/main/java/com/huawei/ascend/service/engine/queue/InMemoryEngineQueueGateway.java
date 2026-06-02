package com.huawei.ascend.service.engine.queue;

import com.huawei.ascend.service.engine.event.EngineCommandEvent;
import com.huawei.ascend.service.queue.InternalEventQueue;
import com.huawei.ascend.service.queue.QueueManager;
import reactor.core.Disposable;

/**
 * Engine-owned queue gateway backed by the shared internal queue mechanism.
 */
public class InMemoryEngineQueueGateway implements EngineQueueGateway {

    private static final String QUEUE_ID = "engine:commands";

    private final InternalEventQueue<EngineCommandEvent> queue;
    private Disposable subscription;

    public InMemoryEngineQueueGateway(QueueManager queueManager) {
        this.queue = queueManager.getOrCreate(QUEUE_ID, EngineCommandEvent.class);
    }

    @Override
    public boolean publish(EngineCommandEvent event) {
        queue.offer(event);
        return true;
    }

    @Override
    public void subscribe(EngineCommandConsumer consumer) {
        subscription = queue.stream().subscribe(consumer::accept);
    }

    public void close() {
        if (subscription != null) {
            subscription.dispose();
        }
        queue.close();
    }
}
