package com.huawei.ascend.service.engine.queue;

import com.huawei.ascend.service.engine.event.EngineCommandEvent;
import com.huawei.ascend.service.engine.spi.EngineCommandConsumer;
import com.huawei.ascend.service.engine.spi.EngineQueueGateway;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link EngineQueueGateway} that dispatches synchronously to its
 * registered consumers. Sufficient for Phase 1; a real broker-backed gateway
 * replaces it later. See engine model design §15.3.
 */
public class InMemoryEngineQueueGateway implements EngineQueueGateway {

    private final List<EngineCommandConsumer> consumers = new CopyOnWriteArrayList<>();

    @Override
    public boolean publish(EngineCommandEvent event) {
        for (EngineCommandConsumer consumer : consumers) {
            consumer.accept(event);
        }
        return true;
    }

    @Override
    public void subscribe(EngineCommandConsumer consumer) {
        consumers.add(consumer);
    }
}
