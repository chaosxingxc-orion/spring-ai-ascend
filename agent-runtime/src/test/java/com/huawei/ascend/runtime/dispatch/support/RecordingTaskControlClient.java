package com.huawei.ascend.service.engine.support;

import com.huawei.ascend.service.engine.event.EngineCancelledEvent;
import com.huawei.ascend.service.engine.event.EngineCompletedEvent;
import com.huawei.ascend.service.engine.event.EngineFailedEvent;
import com.huawei.ascend.service.engine.event.EngineInterruptedEvent;
import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.port.TaskControlClient;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link TaskControlClient} for tests: records every lifecycle
 * transition so assertions can verify routing without the real
 * task-centric-control module.
 */
public class RecordingTaskControlClient implements TaskControlClient {

    public final List<String> transitions = Collections.synchronizedList(new ArrayList<>());
    public final List<EngineCompletedEvent> succeeded = Collections.synchronizedList(new ArrayList<>());
    public final List<EngineFailedEvent> failed = Collections.synchronizedList(new ArrayList<>());
    public final List<EngineInterruptedEvent> waiting = Collections.synchronizedList(new ArrayList<>());
    public final List<EngineCancelledEvent> cancelled = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void markRunning(EngineExecutionScope scope) {
        transitions.add("RUNNING:" + scope.taskId());
    }

    @Override
    public void markWaiting(EngineExecutionScope scope, EngineInterruptedEvent event) {
        transitions.add("WAITING:" + scope.taskId());
        waiting.add(event);
    }

    @Override
    public void markSucceeded(EngineExecutionScope scope, EngineCompletedEvent event) {
        transitions.add("SUCCEEDED:" + scope.taskId());
        succeeded.add(event);
    }

    @Override
    public void markFailed(EngineExecutionScope scope, EngineFailedEvent event) {
        transitions.add("FAILED:" + scope.taskId());
        failed.add(event);
    }

    @Override
    public void markCancelled(EngineExecutionScope scope, EngineCancelledEvent event) {
        transitions.add("CANCELLED:" + scope.taskId());
        cancelled.add(event);
    }
}
