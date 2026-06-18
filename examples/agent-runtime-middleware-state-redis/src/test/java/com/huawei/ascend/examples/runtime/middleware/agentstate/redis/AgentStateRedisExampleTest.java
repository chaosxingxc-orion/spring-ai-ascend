package com.huawei.ascend.examples.runtime.middleware.agentstate.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenCheckpointerConfigurer;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("manual")
class AgentStateRedisExampleTest {
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
    void redisCheckpointerPersistsAndReleasesAgentSessionWhenConfigured() {
        String redisUrl = System.getenv("SAA_SAMPLE_OPENJIUWEN_REDIS_URL");
        assumeTrue(hasText(redisUrl), "Set SAA_SAMPLE_OPENJIUWEN_REDIS_URL to run the Redis checkpointer example");

        Checkpointer redisCheckpointer = new RedisCheckpointer.Provider()
                .create(Map.of("connection", Map.of("url", redisUrl)));
        Checkpointer installedCheckpointer = OpenJiuwenCheckpointerConfigurer.setDefault(redisCheckpointer);
        String sessionId = "redis-state-" + UUID.randomUUID();
        AgentSessionApi savedSession = new AgentSessionApi(sessionId);

        installedCheckpointer.preAgentExecute(savedSession.getInner(), Map.of("input", "ping"));
        savedSession.updateState(Map.of("turn", 1, "answer", "pong"));
        installedCheckpointer.postAgentExecute(savedSession.getInner());

        assertThat(installedCheckpointer).isSameAs(redisCheckpointer);
        assertThat(installedCheckpointer.sessionExists(sessionId)).isTrue();

        AgentSessionApi restoredSession = new AgentSessionApi(sessionId);
        installedCheckpointer.preAgentExecute(restoredSession.getInner(), Map.of());
        Map<?, ?> restoredGlobalState = (Map<?, ?>) restoredSession.dumpState().get("global_state");

        assertThat(restoredGlobalState.get("turn")).isEqualTo(1);
        assertThat(restoredGlobalState.get("answer")).isEqualTo("pong");

        installedCheckpointer.release(sessionId);

        assertThat(installedCheckpointer.sessionExists(sessionId)).isFalse();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
