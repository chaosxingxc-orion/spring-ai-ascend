package com.huawei.ascend.examples.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.MemoryProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryMemoryProviderTest {

    @Test
    void savesAndSearchesWithinAgentStateKeyScope() {
        InMemoryMemoryProvider provider = new InMemoryMemoryProvider();
        AgentExecutionContext firstScope = context("scope-a");
        AgentExecutionContext secondScope = context("scope-b");

        provider.init(firstScope);
        provider.save(firstScope, List.of(new MemoryProvider.MemoryRecord(null, "assistant",
                "remember the user likes green tea", Map.of())));

        assertThat(provider.search(firstScope, "green tea", 5))
                .singleElement()
                .satisfies(hit -> assertThat(hit.content()).contains("green tea"));
        assertThat(provider.search(secondScope, "green tea", 5)).isEmpty();
        assertThat(provider.records(firstScope))
                .singleElement()
                .satisfies(record -> assertThat(record.id()).isNotBlank());
    }

    private static AgentExecutionContext context(String stateKey) {
        return new AgentExecutionContext(new RuntimeIdentity("tenant", "user", "session", "task", "agent"),
                "USER_MESSAGE", List.of(RuntimeMessage.user("ping")),
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, stateKey));
    }
}
