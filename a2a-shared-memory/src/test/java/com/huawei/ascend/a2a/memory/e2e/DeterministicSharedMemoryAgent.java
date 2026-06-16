package com.huawei.ascend.a2a.memory.e2e;

import com.huawei.ascend.a2a.memory.shared.InMemorySharedMemoryStore;
import com.huawei.ascend.a2a.memory.shared.OwnershipViolationException;
import com.huawei.ascend.a2a.memory.shared.SharedEntry;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryKit;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryStore;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.AgentCardProvider;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.List;
import java.util.stream.Stream;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A deterministic, NO-LLM A2A agent that uses the A2A shared blackboard: over the
 * real A2A wire it executes simple commands ({@code PUT <agent> <key> <value>} /
 * {@code GET <key>}) against {@link SharedMemoryKit} keyed by the A2A contextId.
 * Two messages on the same contextId therefore share state — proving real
 * agent-to-agent shared memory over the wire, with no API key. One shared
 * {@link SharedMemoryStore} bean stands in for the blackboard so different
 * acting agents (the {@code PUT} actor argument) hit the same board.
 */
@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.a2a.memory.e2e",
        "com.huawei.ascend.runtime.boot"})
public class DeterministicSharedMemoryAgent {

    public static final String AGENT_ID = "shared-mem-agent";

    public static void main(String[] args) {
        SpringApplication.run(DeterministicSharedMemoryAgent.class, args);
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean
        SharedMemoryStore sharedMemoryStore() {
            return new InMemorySharedMemoryStore();
        }

        @Bean
        AgentRuntimeHandler sharedMemoryHandler(SharedMemoryStore store) {
            return new SharedMemoryHandler(store);
        }
    }

    static final class SharedMemoryHandler implements AgentRuntimeHandler, AgentCardProvider {
        private final SharedMemoryStore store;

        SharedMemoryHandler(SharedMemoryStore store) {
            this.store = store;
        }

        @Override
        public String agentId() {
            return AGENT_ID;
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public AgentCard agentCard() {
            return AgentCard.builder()
                    .name(AGENT_ID)
                    .description("Deterministic no-LLM A2A agent exercising the shared blackboard")
                    .version("0.1.0")
                    .capabilities(AgentCapabilities.builder().streaming(true).pushNotifications(false).build())
                    .defaultInputModes(List.of("text"))
                    .defaultOutputModes(List.of("text"))
                    .skills(List.of(AgentSkill.builder()
                            .id("shared-memory").name("SharedMemory").description("PUT/GET on the shared blackboard")
                            .tags(List.of("test")).build()))
                    .supportedInterfaces(List.of(
                            new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a")))
                    .build();
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            String reply = handle(context);
            return Stream.of(AgentExecutionResult.output(reply), AgentExecutionResult.completed(reply));
        }

        private String handle(AgentExecutionContext context) {
            String text = context.lastUserText() == null ? "" : context.lastUserText().trim();
            String tenant = context.getScope().tenantId();
            String contextId = context.getScope().sessionId(); // A2A contextId = shared collaboration root
            SharedMemoryKit board = SharedMemoryKit.forCollaboration(store, tenant, contextId);
            String[] p = text.split("\\s+", 4);
            try {
                if (p.length >= 4 && p[0].equals("PUT")) {
                    SharedEntry e = board.put(p[2], p[3], p[1]); // key, value, actingAgentId(owner)
                    return "OK v" + e.version();
                }
                if (p.length >= 2 && p[0].equals("GET")) {
                    return board.get(p[1]).orElse("MISS");
                }
                return "UNKNOWN";
            } catch (OwnershipViolationException e) {
                return "DENIED";
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public StreamAdapter resultAdapter() {
            return raw -> (Stream<AgentExecutionResult>) raw;
        }
    }
}
