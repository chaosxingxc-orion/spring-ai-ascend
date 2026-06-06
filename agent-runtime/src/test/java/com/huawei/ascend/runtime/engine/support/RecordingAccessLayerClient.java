package com.huawei.ascend.runtime.engine.support;

import com.huawei.ascend.runtime.engine.event.EngineCompletedEvent;
import com.huawei.ascend.runtime.engine.event.EngineFailedEvent;
import com.huawei.ascend.runtime.engine.event.EngineInterruptedEvent;
import com.huawei.ascend.runtime.engine.event.EngineOutputEvent;
import com.huawei.ascend.runtime.engine.model.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.port.AccessLayerClient;
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
    public final List<EngineOutputEvent> outputs = Collections.synchronizedList(new ArrayList<>());
    public final List<EngineCompletedEvent> completed = Collections.synchronizedList(new ArrayList<>());
    public final List<EngineFailedEvent> failed = Collections.synchronizedList(new ArrayList<>());
    public final List<EngineInterruptedEvent> userInputRequests = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void appendOutput(EngineExecutionScope scope, EngineOutputEvent event) {
        signals.add("APPEND:" + scope.taskId());
        outputs.add(event);
    }

    @Override
    public void completeOutput(EngineExecutionScope scope, EngineCompletedEvent event) {
        signals.add("COMPLETE:" + scope.taskId());
        completed.add(event);
    }

    @Override
    public void failOutput(EngineExecutionScope scope, EngineFailedEvent event) {
        signals.add("FAIL:" + scope.taskId());
        failed.add(event);
    }

    @Override
    public void requestUserInput(EngineExecutionScope scope, EngineInterruptedEvent event) {
        signals.add("REQUEST_INPUT:" + scope.taskId());
        userInputRequests.add(event);
    }
}
