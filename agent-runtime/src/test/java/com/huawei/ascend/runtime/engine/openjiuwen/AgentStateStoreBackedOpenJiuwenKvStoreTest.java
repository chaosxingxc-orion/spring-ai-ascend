package com.huawei.ascend.runtime.engine.openjiuwen;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.service.InMemoryAgentStateStore;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentStateStoreBackedOpenJiuwenKvStoreTest {

    @Test
    void storesOpenJiuwenValuesInsideAgentStateStore() {
        InMemoryAgentStateStore stateStore = new InMemoryAgentStateStore();
        AgentStateStoreBackedOpenJiuwenKvStore kvStore = new AgentStateStoreBackedOpenJiuwenKvStore(stateStore);

        kvStore.set("session-1:agent:a", Map.of("turn", 1));
        kvStore.set("session-1:agent:b", "value-b");
        kvStore.set("session-2:agent:c", "value-c");

        assertThat(stateStore.load("openjiuwen.checkpoint:session-1:agent:a"))
                .hasValueSatisfying(stored -> assertThat(stored).containsEntry("value", Map.of("turn", 1)));
        assertThat(kvStore.get("session-1:agent:a")).isEqualTo(Map.of("turn", 1));
        assertThat(kvStore.getByPrefix("session-1:agent:"))
                .containsOnlyKeys("session-1:agent:a", "session-1:agent:b");
        assertThat(kvStore.mget(List.of("session-1:agent:b", "missing"))).containsExactly("value-b", null);
    }

    @Test
    void supportsDeleteAndPipelineOperationsUsedByPersistenceCheckpointer() {
        AgentStateStoreBackedOpenJiuwenKvStore kvStore = new AgentStateStoreBackedOpenJiuwenKvStore(
                new InMemoryAgentStateStore());

        List<Object> results = kvStore.pipeline()
                .set("session-1:agent:a", "value-a")
                .exists("session-1:agent:a")
                .get("session-1:agent:a")
                .execute();

        assertThat(results).containsExactly(Boolean.TRUE, Boolean.TRUE, "value-a");
        assertThat(kvStore.exclusiveSet("session-1:agent:a", "new-value", null)).isFalse();
        assertThat(kvStore.batchDelete(List.of("session-1:agent:a", "missing"), null)).isEqualTo(1);
        assertThat(kvStore.exists("session-1:agent:a")).isFalse();
    }
}
