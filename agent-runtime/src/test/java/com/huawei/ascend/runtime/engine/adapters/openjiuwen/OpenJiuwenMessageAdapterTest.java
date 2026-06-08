package com.huawei.ascend.runtime.engine.adapters.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.handler.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.model.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.model.EngineInput;
import com.huawei.ascend.runtime.common.Message;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenJiuwenMessageAdapterTest {

    @Test
    void toOpenJiuwenInput_buildsQueryAndConversationId() {
        EngineExecutionScope scope = new EngineExecutionScope("t", "u", "s", "task-7", "echo-agent");
        EngineInput input = new EngineInput("text", List.of(Message.user("你好")), Map.of());
        AgentExecutionContext ctx = new AgentExecutionContext(scope, input);

        Object result = new OpenJiuwenMessageAdapter().toOpenJiuwenInput(ctx);

        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> map = (Map<?, ?>) result;
        assertThat(map.get("query")).isEqualTo("你好");
        assertThat(map.get("conversation_id")).isEqualTo("task-7");
    }
}
