package com.huawei.ascend.service.remote;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.boot.RuntimeAutoConfiguration;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.A2aAgentExecutor;
import com.huawei.ascend.runtime.engine.a2a.A2aRemoteAgentOutboundAdapter;
import com.huawei.ascend.runtime.engine.openjiuwen.OpenJiuwenRemoteToolInstaller;
import com.huawei.ascend.runtime.engine.service.RemoteAgentInvocationService;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.RemoteAgentCatalogPort;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The service plane owns remote-agent discovery; these tests pin its wiring and refresh behavior. */
class RemoteAgentAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RuntimeAutoConfiguration.class, RemoteAgentAutoConfiguration.class));

    @Test
    void remoteAgentUrlPropertyWiresCatalogOutboundServiceAndExecutorSupport() {
        contextRunner
                .withUserConfiguration(SimpleHandlerConfiguration.class)
                .withPropertyValues("agent-runtime.remote-agents[0].url=http://localhost:18081")
                .run(context -> {
                    assertThat(context).hasSingleBean(RemoteAgentCatalogPort.class);
                    assertThat(context).hasSingleBean(A2aRemoteAgentOutboundAdapter.class);
                    assertThat(context).hasSingleBean(RemoteAgentInvocationService.class);
                    assertThat(context).hasSingleBean(OpenJiuwenRemoteToolInstaller.class);
                    assertThat(context).hasSingleBean(org.a2aproject.sdk.server.agentexecution.AgentExecutor.class);
                    assertThat(context.getBean(org.a2aproject.sdk.server.agentexecution.AgentExecutor.class))
                            .isInstanceOf(A2aAgentExecutor.class);
                });
    }

    @Test
    void remoteAgentCatalogRefresherRunsOnBackgroundExecutor() {
        RecordingCatalog catalog = new RecordingCatalog();
        RecordingExecutorService executor = new RecordingExecutorService();

        RemoteAgentCatalogRefresher refresher = new RemoteAgentCatalogRefresher(catalog, executor);

        refresher.start();

        assertThat(executor.command).isNotNull();
        refresher.refreshOnce();
        assertThat(catalog.refreshCount).isOne();
        refresher.stop();
    }

    @Test
    void remoteAgentCatalogCreationDoesNotRefreshSynchronously() throws IOException {
        HttpServer server = cardServer();
        server.start();
        try {
            RemoteAgentAutoConfiguration configuration = new RemoteAgentAutoConfiguration();
            RemoteAgentProperties properties =
                    new RemoteAgentProperties(List.of(
                            new RemoteAgentProperties.RemoteAgent(
                                    "http://localhost:" + server.getAddress().getPort())));

            RemoteAgentCatalogPort catalog = configuration.remoteAgentCatalog(properties);

            assertThat(catalog.availableToolSpecs()).isEmpty();
            assertThat(catalog.pendingUrls()).containsExactly(
                    "http://localhost:" + server.getAddress().getPort());
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer cardServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/.well-known/agent-card.json", exchange -> {
            byte[] body = """
                    {
                      "name": "Remote B",
                      "description": "Remote B",
                      "version": "1",
                      "url": "/a2a",
                      "capabilities": {"streaming": true},
                      "defaultInputModes": ["text"],
                      "defaultOutputModes": ["text"],
                      "skills": [{
                        "id": "b",
                        "name": "B",
                        "description": "Remote B skill",
                        "tags": ["remote"]
                      }],
                      "supportedInterfaces": [{
                        "protocolBinding": "JSONRPC",
                        "url": "/a2a"
                      }]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        return server;
    }

    @Test
    void remoteAgentCatalogRefresherTriggersDiscoveryAfterStartup() throws IOException {
        HttpServer server = cardServer();
        server.start();
        try {
            RemoteAgentAutoConfiguration configuration = new RemoteAgentAutoConfiguration();
            RemoteAgentProperties properties =
                    new RemoteAgentProperties(List.of(
                            new RemoteAgentProperties.RemoteAgent(
                                    "http://localhost:" + server.getAddress().getPort())));
            RemoteAgentCatalogPort catalog = configuration.remoteAgentCatalog(properties);

            ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            RemoteAgentCatalogRefresher refresher = new RemoteAgentCatalogRefresher(catalog, executor);
            refresher.refreshOnce();
            executor.shutdownNow();

            assertThat(catalog.availableToolSpecs()).hasSize(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void remoteAgentPropertiesExposeConfiguredUrls() {
        RemoteAgentAutoConfiguration configuration = new RemoteAgentAutoConfiguration();
        RemoteAgentProperties properties =
                new RemoteAgentProperties(List.of(
                        new RemoteAgentProperties.RemoteAgent("http://localhost:18081")));

        RemoteAgentCatalogPort catalog = configuration.remoteAgentCatalog(properties);

        assertThat(catalog.availableToolSpecs()).isEmpty();
        assertThat(catalog.pendingUrls()).containsExactly("http://localhost:18081");
    }

    @Configuration(proxyBeanMethods = false)
    static class SimpleHandlerConfiguration {
        @Bean
        AgentRuntimeHandler handler() {
            return new AgentRuntimeHandler() {
                @Override
                public String agentId() {
                    return "agent-a";
                }

                @Override
                public java.util.stream.Stream<?> execute(AgentExecutionContext context) {
                    return java.util.stream.Stream.empty();
                }

                @Override
                public boolean isHealthy() {
                    return true;
                }

                @Override
                public StreamAdapter resultAdapter() {
                    return raw -> raw.map(value -> com.huawei.ascend.runtime.engine.spi.AgentExecutionResult.completed(""));
                }
            };
        }
    }

    private static final class RecordingExecutorService extends AbstractExecutorService {
        private Runnable command;
        private boolean shutdown;

        @Override
        public void execute(Runnable command) {
            this.command = command;
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }
    }

    private static final class RecordingCatalog extends RemoteAgentCatalog {
        private int refreshCount;

        private RecordingCatalog() {
            super(List.of());
        }

        @Override
        public void refreshPending() {
            refreshCount++;
        }
    }
}
