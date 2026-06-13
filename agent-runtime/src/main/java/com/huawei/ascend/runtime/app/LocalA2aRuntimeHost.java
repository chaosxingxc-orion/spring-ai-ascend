package com.huawei.ascend.runtime.app;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The first {@link RuntimeHost}: boots a Spring context and serves A2A over HTTP.
 * Spring Boot is confined to THIS host — {@link RuntimeApp} / {@link RuntimeHost} stay
 * framework-neutral. The entire runtime (A2A SDK components, engine wiring, and the
 * northbound controllers) is assembled by the single
 * {@code com.huawei.ascend.runtime.boot.RuntimeAutoConfiguration} listed in
 * {@code META-INF/spring/...AutoConfiguration.imports}.
 *
 * <p>The supplied {@link com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler} is
 * registered as a singleton bean, so the
 * auto-configuration discovers it through its {@code ObjectProvider<AgentRuntimeHandler>}
 * injection points the same way it discovers handler beans declared by an application.
 */
public final class LocalA2aRuntimeHost implements RuntimeHost {

    /**
     * Lowest-precedence defaults, overridable by caller-supplied properties or any
     * external config. {@code framework} registers spring-web's ForwardedHeaderFilter
     * so request-derived URLs (agent card) honor X-Forwarded-* behind a reverse proxy.
     */
    private static final Map<String, Object> HOST_DEFAULTS =
            Map.of("server.forward-headers-strategy", "framework");

    private final int port;
    private final Map<String, Object> defaultProperties;
    private final String[] additionalArgs;

    private LocalA2aRuntimeHost(int port) {
        this(port, Map.of());
    }

    LocalA2aRuntimeHost(int port, Map<String, Object> defaultProperties, String... additionalArgs) {
        this.port = port;
        Map<String, Object> merged = new HashMap<>(HOST_DEFAULTS);
        if (defaultProperties != null) {
            merged.putAll(defaultProperties);
        }
        this.defaultProperties = Map.copyOf(merged);
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
     * Boot configuration that stands the runtime up. Deliberately declares no
     * {@code scanBasePackages}: everything (controllers included) is supplied by
     * {@code RuntimeAutoConfiguration}, so this host exercises the same pure-dependency
     * assembly path as an application that merely depends on the jar.
     */
    @SpringBootApplication
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
