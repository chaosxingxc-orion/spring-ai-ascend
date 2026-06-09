package com.huawei.ascend.runtime.engine.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryAgentStateStoreTest {

    private static final String KEY = "business-state-key";

    @Test
    void saveLoadAndDeleteState() {
        InMemoryAgentStateStore store = new InMemoryAgentStateStore();
        Map<String, Object> state = Map.of("phase", "waiting");

        assertThat(store.save(KEY, state)).isEqualTo(state);
        assertThat(store.load(KEY)).contains(state);

        store.delete(KEY);

        assertThat(store.load(KEY)).isEmpty();
    }
}
