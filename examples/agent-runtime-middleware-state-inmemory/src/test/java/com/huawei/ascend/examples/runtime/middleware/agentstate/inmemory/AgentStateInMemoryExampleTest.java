package com.huawei.ascend.examples.runtime.middleware.agentstate.inmemory;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenCheckpointerConfigurer;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentStateInMemoryExampleTest {
    private Checkpointer originalCheckpointer;

    @BeforeEach
    void captureOriginalCheckpointer() {
        originalCheckpointer = CheckpointerFactory.getCheckpointer();
    }

    @AfterEach
    void restoreOriginalCheckpointer() {
        CheckpointerFactory.setDefaultCheckpointer(originalCheckpointer);
    }

    @Test
    void inMemoryCheckpointerPersistsAndReleasesAgentSession() {
        Checkpointer inMemoryCheckpointer = OpenJiuwenCheckpointerConfigurer.setInMemoryDefault();
        String sessionId = "in-memory-state-" + UUID.randomUUID();
        AgentSessionApi savedSession = new AgentSessionApi(sessionId);

        inMemoryCheckpointer.preAgentExecute(savedSession.getInner(), Map.of("input", "ping"));
        savedSession.updateState(Map.of("turn", 1, "answer", "pong"));
        inMemoryCheckpointer.postAgentExecute(savedSession.getInner());

        assertThat(inMemoryCheckpointer).isInstanceOf(InMemoryCheckpointer.class);
        assertThat(inMemoryCheckpointer.sessionExists(sessionId)).isTrue();

        AgentSessionApi restoredSession = new AgentSessionApi(sessionId);
        inMemoryCheckpointer.preAgentExecute(restoredSession.getInner(), Map.of());
        Map<?, ?> restoredGlobalState = (Map<?, ?>) restoredSession.dumpState().get("global_state");

        assertThat(restoredGlobalState.get("turn")).isEqualTo(1);
        assertThat(restoredGlobalState.get("answer")).isEqualTo("pong");

        inMemoryCheckpointer.release(sessionId);

        assertThat(inMemoryCheckpointer.sessionExists(sessionId)).isFalse();
    }
}
