package com.huawei.ascend.service.engine.adapter.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.service.engine.handler.AgentExecutionContext;
import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.model.EngineInput;
import com.huawei.ascend.service.schema.Message;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenJiuwenMessageConverterTest {

    @Test
    void toOpenJiuwenInput_buildsQueryAndConversationId() {
        EngineExecutionScope scope = new EngineExecutionScope("t", "u", "s", "task-7", "echo-agent");
        EngineInput input = new EngineInput("text", List.of(Message.user("你好")), Map.of());
        AgentExecutionContext ctx = new AgentExecutionContext(scope, input);

        Object result = new OpenJiuwenMessageConverter().toOpenJiuwenInput(ctx);

        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> map = (Map<?, ?>) result;
        assertThat(map.get("query")).isEqualTo("你好");
        assertThat(map.get("conversation_id")).isEqualTo("task-7");
    }
}
