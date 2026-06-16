package com.huawei.ascend.collab.e2e;

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
 * A deterministic, NO-LLM A2A agent for the e2e test: it echoes the user text and
 * completes, with no model call — so the test verifies {@code A2aWorker} over the
 * real A2A wire in CI without an API key. Implements only the neutral
 * {@code AgentRuntimeHandler} SPI (4 methods); the runtime derives the agent card.
 */
@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.collab.e2e",
        "com.huawei.ascend.runtime.boot"})
public class DeterministicEchoAgent {

    public static final String AGENT_ID = "echo-agent";

    public static void main(String[] args) {
        SpringApplication.run(DeterministicEchoAgent.class, args);
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean
        AgentRuntimeHandler echoHandler() {
            return new EchoHandler();
        }
    }

    static final class EchoHandler implements AgentRuntimeHandler, AgentCardProvider {
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
                    .description("Deterministic no-LLM A2A echo agent")
                    .version("0.1.0")
                    .capabilities(AgentCapabilities.builder().streaming(true).pushNotifications(false).build())
                    .defaultInputModes(List.of("text"))
                    .defaultOutputModes(List.of("text"))
                    .skills(List.of(AgentSkill.builder()
                            .id("echo").name("Echo").description("Echoes the user message")
                            .tags(List.of("test")).build()))
                    .supportedInterfaces(List.of(
                            new AgentInterface(TransportProtocol.JSONRPC.asString(), "/a2a")))
                    .build();
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            String reply = "echo: " + context.lastUserText();
            return Stream.of(AgentExecutionResult.output(reply), AgentExecutionResult.completed(reply));
        }

        @Override
        @SuppressWarnings("unchecked")
        public StreamAdapter resultAdapter() {
            return raw -> (Stream<AgentExecutionResult>) raw; // execute() already emits results
        }
    }
}
