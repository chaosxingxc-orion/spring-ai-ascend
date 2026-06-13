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
    private Checkpointer original;

    @BeforeEach
    void captureOriginalCheckpointer() {
        original = CheckpointerFactory.getCheckpointer();
    }

    @AfterEach
    void restoreOriginalCheckpointer() {
        CheckpointerFactory.setDefaultCheckpointer(original);
    }

    @Test
    void inMemoryCheckpointerPersistsAndReleasesAgentSession() {
        Checkpointer checkpointer = OpenJiuwenCheckpointerConfigurer.setInMemoryDefault();
        String sessionId = "in-memory-state-" + UUID.randomUUID();
        AgentSessionApi session = new AgentSessionApi(sessionId);

        checkpointer.preAgentExecute(session.getInner(), Map.of("input", "ping"));
        session.updateState(Map.of("turn", 1, "answer", "pong"));
        checkpointer.postAgentExecute(session.getInner());

        assertThat(checkpointer).isInstanceOf(InMemoryCheckpointer.class);
        assertThat(checkpointer.sessionExists(sessionId)).isTrue();

        checkpointer.release(sessionId);

        assertThat(checkpointer.sessionExists(sessionId)).isFalse();
    }
}
