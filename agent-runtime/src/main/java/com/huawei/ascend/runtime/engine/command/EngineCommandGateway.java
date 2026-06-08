package com.huawei.ascend.runtime.engine.command;

import com.huawei.ascend.runtime.engine.event.EngineCommandEvent;
import reactor.core.publisher.Flux;

/**
 * Engine-owned command inbox. The task-facing API publishes commands here; the
 * engine runtime consumes the stream and owns execution.
 */
public interface EngineCommandGateway {

    boolean publish(EngineCommandEvent event);

    Flux<EngineCommandEvent> commands();
}
