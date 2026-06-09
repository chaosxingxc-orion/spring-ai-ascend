package com.huawei.ascend.runtime.engine.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryAgentStateStoreTest {

    private static final AgentStateKey KEY = new AgentStateKey("tenant", "user", "session", "task", "agent");

    @Test
    void saveLoadAndDeleteSnapshot() {
        InMemoryAgentStateStore store = new InMemoryAgentStateStore();
        AgentStateSnapshot snapshot = new AgentStateSnapshot(KEY, 1, Map.of("phase", "waiting"), Instant.EPOCH);

        assertThat(store.save(snapshot)).isEqualTo(snapshot);
        assertThat(store.load(KEY)).contains(snapshot);

        store.delete(KEY);

        assertThat(store.load(KEY)).isEmpty();
    }
}
