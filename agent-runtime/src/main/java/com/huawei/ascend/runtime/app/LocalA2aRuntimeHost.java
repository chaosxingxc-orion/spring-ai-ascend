package com.huawei.ascend.runtime.app;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The first {@link RuntimeHost}: boots the runtime's five-layer Spring context and serves A2A over
 * HTTP. Spring Boot is confined to THIS host — {@link RuntimeApp} / {@link RuntimeHost} stay
 * framework-neutral. Replaces the retired {@code bootstrap.AgentRuntimeApplication} entry: the
 * access layer is component-scanned; session / queue / control / engine and the cross-layer wiring
 * contribute through their {@code AutoConfiguration} imports.
 *
 * <p>The supplied {@link AgentRuntimeHandler} is registered as a singleton bean so the engine's
 * {@code AgentRuntimeHandlerRegistry} discovers it the same way it discovers handler beans declared
 * by an application.
 */
public final class LocalA2aRuntimeHost implements RuntimeHost {

    private final int port;
    private final Map<String, Object> defaultProperties;
    private final String[] additionalArgs;

    private LocalA2aRuntimeHost(int port) {
        this(port, Map.of());
    }

    LocalA2aRuntimeHost(int port, Map<String, Object> defaultProperties, String... additionalArgs) {
        this.port = port;
        this.defaultProperties = defaultProperties == null ? Map.of() : Map.copyOf(defaultProperties);
        this.additionalArgs = additionalArgs == null ? new String[0] : additionalArgs.clone();
    }

    /**
     * @param port HTTP port to bind; {@code 0} binds an ephemeral port readable via
     *             {@link RunningRuntime#port()} after start.
     */
    public static LocalA2aRuntimeHost port(int port) {
        return new LocalA2aRuntimeHost(port);
    }

    @Override
    public RunningRuntime start(RuntimeComponents components) {
        SpringApplication app = new SpringApplication(HostBoot.class);
        app.setDefaultProperties(defaultProperties);
        app.addInitializers(context -> context.getBeanFactory()
                .registerSingleton("primaryAgentRuntimeHandler", components.handler()));
        String[] args = new String[additionalArgs.length + 1];
        args[0] = "--server.port=" + port;
        System.arraycopy(additionalArgs, 0, args, 1, additionalArgs.length);
        ConfigurableApplicationContext context = app.run(args);
        int boundPort = ((WebServerApplicationContext) context).getWebServer().getPort();
        return new SpringRunningRuntime(context, boundPort);
    }

    /**
     * Boot configuration that stands the runtime up. Scans the access layer
     * ({@code @Configuration} + A2A controllers); every other layer + the cross-layer wiring are
     * supplied by {@code META-INF/spring/...AutoConfiguration.imports}.
     */
    @SpringBootApplication(scanBasePackages = "com.huawei.ascend.runtime.access")
    static class HostBoot {
    }

    private record SpringRunningRuntime(ConfigurableApplicationContext context, int port)
            implements RunningRuntime {

        @Override
        public void close() {
            context.close();
        }
    }
}
