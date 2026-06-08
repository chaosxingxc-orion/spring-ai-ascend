package com.huawei.ascend.runtime.engine.support;

import com.huawei.ascend.runtime.engine.EngineEvent;
import com.huawei.ascend.runtime.engine.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.TaskControlClient;
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
    public final List<EngineEvent> outputs = Collections.synchronizedList(new ArrayList<>());
    public final List<EngineEvent> succeeded = Collections.synchronizedList(new ArrayList<>());
    public final List<EngineEvent> failed = Collections.synchronizedList(new ArrayList<>());
    public final List<EngineEvent> waiting = Collections.synchronizedList(new ArrayList<>());
    public final List<EngineEvent> cancelled = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void markRunning(EngineExecutionScope scope) {
        transitions.add("RUNNING:" + scope.taskId());
    }

    @Override
    public void appendOutput(EngineExecutionScope scope, EngineEvent event) {
        transitions.add("APPEND:" + scope.taskId());
        outputs.add(event);
    }

    @Override
    public void markWaiting(EngineExecutionScope scope, EngineEvent event) {
        transitions.add("WAITING:" + scope.taskId());
        waiting.add(event);
    }

    @Override
    public void markSucceeded(EngineExecutionScope scope, EngineEvent event) {
        transitions.add("SUCCEEDED:" + scope.taskId());
        succeeded.add(event);
    }

    @Override
    public void markFailed(EngineExecutionScope scope, EngineEvent event) {
        transitions.add("FAILED:" + scope.taskId());
        failed.add(event);
    }

    @Override
    public void markCancelled(EngineExecutionScope scope, EngineEvent event) {
        transitions.add("CANCELLED:" + scope.taskId());
        cancelled.add(event);
    }
}
