package com.huawei.ascend.service.engine.queue;

import com.huawei.ascend.service.engine.api.EnqueueEngineCancelRequest;
import com.huawei.ascend.service.engine.api.EnqueueEngineExecutionRequest;
import com.huawei.ascend.service.engine.api.EnqueueEngineResumeRequest;
import com.huawei.ascend.service.engine.event.EngineCommandEvent;
import java.time.Instant;

/**
 * Builds {@link EngineCommandEvent}s from inbound enqueue requests, stamping the
 * command type and creation time. See engine model design §7.2.
 */
public class EngineCommandEventFactory {

    public EngineCommandEvent execute(EnqueueEngineExecutionRequest request) {
        return new EngineCommandEvent("EXECUTE", request.scope(), request.input(), Instant.now());
    }

    public EngineCommandEvent resume(EnqueueEngineResumeRequest request) {
        return new EngineCommandEvent("RESUME", request.scope(), request.input(), Instant.now());
    }

    public EngineCommandEvent cancel(EnqueueEngineCancelRequest request) {
        return new EngineCommandEvent("CANCEL", request.scope(), null, Instant.now());
    }
}
