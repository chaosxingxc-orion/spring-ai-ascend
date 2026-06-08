package com.huawei.ascend.runtime.engine.support;

import com.huawei.ascend.runtime.engine.EngineEvent;
import com.huawei.ascend.runtime.engine.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.AccessLayerClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory {@link AccessLayerClient} for tests: records every caller-facing
 * signal so assertions can verify output streaming without the real access
 * layer.
 */
public class RecordingAccessLayerClient implements AccessLayerClient {

    public final List<String> signals = Collections.synchronizedList(new ArrayList<>());
    public final List<EngineEvent> outputs = Collections.synchronizedList(new ArrayList<>());
    public final List<EngineEvent> completed = Collections.synchronizedList(new ArrayList<>());
    public final List<EngineEvent> failed = Collections.synchronizedList(new ArrayList<>());
    public final List<EngineEvent> userInputRequests = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void appendOutput(EngineExecutionScope scope, EngineEvent event) {
        signals.add("APPEND:" + scope.taskId());
        outputs.add(event);
    }

    @Override
    public void completeOutput(EngineExecutionScope scope, EngineEvent event) {
        signals.add("COMPLETE:" + scope.taskId());
        completed.add(event);
    }

    @Override
    public void failOutput(EngineExecutionScope scope, EngineEvent event) {
        signals.add("FAIL:" + scope.taskId());
        failed.add(event);
    }

    @Override
    public void requestUserInput(EngineExecutionScope scope, EngineEvent event) {
        signals.add("REQUEST_INPUT:" + scope.taskId());
        userInputRequests.add(event);
    }
}
