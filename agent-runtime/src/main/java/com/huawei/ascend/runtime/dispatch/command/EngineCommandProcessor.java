package com.huawei.ascend.service.engine.command;

import com.huawei.ascend.service.engine.dispatch.EngineDispatcher;
import com.huawei.ascend.service.engine.event.EngineCommandEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

/**
 * Consumes engine commands and offloads each agent execution to the engine
 * execution pool.
 */
public class EngineCommandProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(EngineCommandProcessor.class);

    private final EngineCommandGateway commandGateway;
    private final EngineDispatcher dispatcher;
    private final Executor executor;
    private Disposable subscription;

    public EngineCommandProcessor(EngineCommandGateway commandGateway, EngineDispatcher dispatcher, Executor executor) {
        this.commandGateway = Objects.requireNonNull(commandGateway, "commandGateway");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public void start() {
        LOGGER.info("engine command processor starting");
        subscription = commandGateway.commands().subscribe(this::onCommand);
    }

    private void onCommand(EngineCommandEvent command) {
        long receivedNanos = System.nanoTime();
        long queueWaitMs = elapsedSince(command.getCreatedAt());
        LOGGER.info("engine command received commandType={} tenantId={} sessionId={} taskId={} agentId={}",
                command.getCommandType(),
                command.getScope().tenantId(),
                command.getScope().sessionId(),
                command.getScope().taskId(),
                command.getScope().agentId());
        LOGGER.info("trace stage=engine-command-received commandType={} tenantId={} sessionId={} taskId={} agentId={} queueWaitMs={}",
                command.getCommandType(),
                command.getScope().tenantId(),
                command.getScope().sessionId(),
                command.getScope().taskId(),
                command.getScope().agentId(),
                queueWaitMs);
        executor.execute(() -> {
            long startedNanos = System.nanoTime();
            LOGGER.info("engine command executing commandType={} tenantId={} sessionId={} taskId={} agentId={}",
                    command.getCommandType(),
                    command.getScope().tenantId(),
                    command.getScope().sessionId(),
                    command.getScope().taskId(),
                    command.getScope().agentId());
            LOGGER.info("trace stage=engine-command-execute-start commandType={} tenantId={} sessionId={} taskId={} agentId={} executorWaitMs={}",
                    command.getCommandType(),
                    command.getScope().tenantId(),
                    command.getScope().sessionId(),
                    command.getScope().taskId(),
                    command.getScope().agentId(),
                    elapsedMs(receivedNanos));
            try {
                dispatcher.dispatch(command);
            } finally {
                LOGGER.info("trace stage=engine-command-execute-finish commandType={} tenantId={} sessionId={} taskId={} agentId={} durationMs={}",
                        command.getCommandType(),
                        command.getScope().tenantId(),
                        command.getScope().sessionId(),
                        command.getScope().taskId(),
                        command.getScope().agentId(),
                        elapsedMs(startedNanos));
            }
        });
    }

    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }

    private static long elapsedSince(Instant instant) {
        return instant == null ? -1L : Duration.between(instant, Instant.now()).toMillis();
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
