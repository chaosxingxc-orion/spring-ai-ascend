package com.huawei.ascend.service.engine.queue;

import com.huawei.ascend.service.engine.dispatch.EngineDispatcher;
import com.huawei.ascend.service.engine.event.EngineCommandEvent;

/**
 * Subscribes to the engine command queue and hands each command to the
 * {@link EngineDispatcher}. See engine model design §7.3.
 */
public class EngineCommandSubscriber {

    private final EngineQueueGateway queueGateway;
    private final EngineDispatcher dispatcher;

    public EngineCommandSubscriber(EngineQueueGateway queueGateway, EngineDispatcher dispatcher) {
        this.queueGateway = queueGateway;
        this.dispatcher = dispatcher;
    }

    public void start() {
        queueGateway.subscribe(this::onCommand);
    }

    private void onCommand(EngineCommandEvent command) {
        dispatcher.dispatch(command);
    }
}
