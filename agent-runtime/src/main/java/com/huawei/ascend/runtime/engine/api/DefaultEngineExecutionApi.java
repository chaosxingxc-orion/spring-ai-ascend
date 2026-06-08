package com.huawei.ascend.runtime.engine.api;

import com.huawei.ascend.runtime.engine.event.EngineCommandEvent;
import com.huawei.ascend.runtime.engine.command.EngineCommandEventFactory;
import com.huawei.ascend.runtime.engine.command.EngineCommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link EngineExecutionApi}: the inbound entry point for
 * task-centric-control. Each call builds a command event and publishes it onto
 * the engine queue, returning only the enqueue outcome — real execution status
 * is written back later through {@code TaskControlClient} (design §4, §7).
 */
public class DefaultEngineExecutionApi implements EngineExecutionApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultEngineExecutionApi.class);

    private final EngineCommandEventFactory commandEventFactory;
    private final EngineCommandGateway commandGateway;

    public DefaultEngineExecutionApi(EngineCommandEventFactory commandEventFactory, EngineCommandGateway commandGateway) {
        this.commandEventFactory = commandEventFactory;
        this.commandGateway = commandGateway;
    }

    @Override
    public EnqueueEngineStatus enqueueExecution(EnqueueEngineExecutionRequest request) {
        return publish(commandEventFactory.execute(request));
    }

    @Override
    public EnqueueEngineStatus enqueueResume(EnqueueEngineResumeRequest request) {
        return publish(commandEventFactory.resume(request));
    }

    @Override
    public EnqueueEngineStatus enqueueCancel(EnqueueEngineCancelRequest request) {
        return publish(commandEventFactory.cancel(request));
    }

    private EnqueueEngineStatus publish(EngineCommandEvent event) {
        long startedNanos = System.nanoTime();
        boolean accepted = commandGateway.publish(event);
        LOGGER.info("trace stage=engine-dispatch-publish commandType={} tenantId={} sessionId={} taskId={} agentId={} accepted={} durationMs={}",
                event.getCommandType(),
                event.getScope().tenantId(),
                event.getScope().sessionId(),
                event.getScope().taskId(),
                event.getScope().agentId(),
                accepted,
                elapsedMs(startedNanos));
        return accepted ? EnqueueEngineStatus.SUCCESS : EnqueueEngineStatus.FAILED;
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
