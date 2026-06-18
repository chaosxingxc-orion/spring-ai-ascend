package com.huawei.ascend.examples.runtime.middleware.agentstate.redis;

import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenCheckpointerConfigurer;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class AgentStateRedisApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentStateRedisApplication.class, args);
    }
}

@Configuration(proxyBeanMethods = false)
class AgentStateRedisConfiguration {
    @Bean
    Checkpointer openJiuwenCheckpointer(
            @Value("${sample.openjiuwen.redis-url:${SAA_SAMPLE_OPENJIUWEN_REDIS_URL:redis://localhost:6379}}")
            String redisUrl) {
        Checkpointer redisCheckpointer = new RedisCheckpointer.Provider()
                .create(Map.of("connection", Map.of("url", redisUrl)));
        return OpenJiuwenCheckpointerConfigurer.setDefault(redisCheckpointer);
    }
}

@RestController
class AgentStateRedisController {
    private final Checkpointer checkpointer;

    AgentStateRedisController(Checkpointer checkpointer) {
        this.checkpointer = checkpointer;
    }

    @PostMapping("/sample/state/save")
    Map<String, Object> save(@RequestBody StateRequest request) {
        String stateKey = normalizeStateKey(request.stateKey());
        String userInput = request.input() == null ? "" : request.input();
        String agentAnswer = request.answer() == null ? "" : request.answer();
        AgentSessionApi stateSession = new AgentSessionApi(stateKey);
        checkpointer.preAgentExecute(stateSession.getInner(), Map.of("input", userInput));
        stateSession.updateState(Map.of("turn", request.turn(), "answer", agentAnswer));
        checkpointer.postAgentExecute(stateSession.getInner());
        return Map.of("stateKey", stateKey, "exists", checkpointer.sessionExists(stateKey));
    }

    @GetMapping("/sample/state/exists")
    Map<String, Object> exists(@RequestParam(defaultValue = "demo-state") String stateKey) {
        return Map.of("stateKey", stateKey, "exists", checkpointer.sessionExists(stateKey));
    }

    @GetMapping("/sample/state/load")
    Map<String, Object> load(@RequestParam(defaultValue = "demo-state") String stateKey) {
        AgentSessionApi restoredSession = new AgentSessionApi(stateKey);
        checkpointer.preAgentExecute(restoredSession.getInner(), Map.of());
        return Map.of("stateKey", stateKey, "exists", checkpointer.sessionExists(stateKey), "state", restoredSession.dumpState());
    }

    @DeleteMapping("/sample/state/{stateKey}")
    Map<String, Object> release(@PathVariable String stateKey) {
        checkpointer.release(stateKey);
        return Map.of("stateKey", stateKey, "exists", checkpointer.sessionExists(stateKey));
    }

    private static String normalizeStateKey(String stateKey) {
        return stateKey == null || stateKey.isBlank() ? "demo-state" : stateKey;
    }

    record StateRequest(String stateKey, String input, int turn, String answer) {
    }
}
