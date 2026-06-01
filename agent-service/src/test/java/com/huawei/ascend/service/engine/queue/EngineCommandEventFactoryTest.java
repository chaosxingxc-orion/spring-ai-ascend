package com.huawei.ascend.service.engine.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.service.engine.api.EnqueueEngineExecutionRequest;
import com.huawei.ascend.service.engine.event.EngineCommandEvent;
import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.model.EngineInput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EngineCommandEventFactoryTest {

    @Test
    void execute_buildsCommandEventWithScopeAndInput() {
        EngineExecutionScope scope = new EngineExecutionScope("t", "u", "s", "task-9", "echo-agent");
        EngineInput input = new EngineInput("text", List.of(), Map.of());
        EnqueueEngineExecutionRequest req = new EnqueueEngineExecutionRequest(scope, input);

        EngineCommandEvent event = new EngineCommandEventFactory().execute(req);

        assertThat(event.getCommandType()).isEqualTo("EXECUTE");
        assertThat(event.getScope().taskId()).isEqualTo("task-9");
        assertThat(event.getInput()).isSameAs(input);
        assertThat(event.getCreatedAt()).isNotNull();
    }
}
