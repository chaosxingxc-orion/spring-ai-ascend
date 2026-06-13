package com.huawei.ascend.runtime.boot;

import com.huawei.ascend.runtime.engine.a2a.A2aAgentExecutor;
import com.huawei.ascend.runtime.engine.a2a.AgentCardProvider;
import com.huawei.ascend.runtime.engine.a2a.AgentCards;
import com.huawei.ascend.runtime.engine.a2a.RemoteAgentInvocationService;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.Redactor;
import com.huawei.ascend.runtime.engine.spi.TrajectorySinkFactory;
import java.util.List;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.QueueManager;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.tasks.PushNotificationConfigStore;
import org.a2aproject.sdk.server.tasks.TaskStore;
import org.a2aproject.sdk.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the A2A execution surface: agent executor, request handler, agent card,
 * and the northbound HTTP controllers for servlet hosts.
 */
@Configuration(proxyBeanMethods = false)
class A2aExecutionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(A2aExecutionConfiguration.class);

    @Bean @ConditionalOnMissingBean
    public AgentExecutor a2aAgentExecutor(ObjectProvider<AgentRuntimeHandler> handlers,
            ObjectProvider<RemoteAgentInvocationService> remoteInvocationService,
            RuntimeReadiness readiness, TrajectoryProperties trajectoryProperties,
            ObjectProvider<TrajectorySinkFactory> sinkFactories,
            ObjectProvider<Redactor> redactorProvider) {
        var registered = handlers.orderedStream().toList();
        RemoteAgentInvocationService invocationService = remoteInvocationService.getIfAvailable();
        if (registered.isEmpty()) {
            // Tolerated so the A2A surface can boot for card discovery; every
            // execution will be rejected until a handler bean is registered.
            log.warn("No AgentRuntimeHandler registered - A2A executions will be rejected");
            return new A2aAgentExecutor(null, invocationService, readiness::isReady);
        }
        if (registered.size() > 1) {
            throw new IllegalStateException(
                    "Multiple AgentRuntimeHandler beans registered but the runtime hosts exactly one agent."
                    + " Found: " + registered.stream().map(AgentRuntimeHandler::agentId).toList()
                    + ". Register exactly one AgentRuntimeHandler bean, or split agents into separate"
                    + " runtime instances.");
        }
        return new A2aAgentExecutor(registered.get(0), invocationService, readiness::isReady,
                RuntimeAutoConfiguration.toTrajectorySettings(trajectoryProperties,
                        redactorProvider.getIfAvailable()),
                sinkFactories.orderedStream().toList());
    }

    @Bean @ConditionalOnMissingBean
    public RequestHandler a2aRequestHandler(AgentExecutor agentExecutor, TaskStore store,
            QueueManager queueManager, PushNotificationConfigStore pushStore, MainEventBusProcessor eventBus,
            RuntimeAutoConfiguration.A2aServerExecutor exec) {
        return DefaultRequestHandler.create(agentExecutor, store, queueManager, pushStore, eventBus,
                exec.executor(), exec.executor());
    }

    /**
     * Default agent card: an explicit {@code agent-card.name} wins, then the
     * configured {@code default-agent-id} selects among registered handlers (with
     * a WARN when it matches none), then the first registered handler. The card
     * shape is built by {@link AgentCards#create(String, String, String, String,
     * String, String)} so YAML-driven fields override rather than fork the card
     * construction.
     */
    @Bean @ConditionalOnMissingBean
    public AgentCard a2aAgentCard(ObjectProvider<AgentCardProvider> cardProviders,
                                   ObjectProvider<AgentRuntimeHandler> handlers,
                                   RuntimeAccessProperties access,
                                   AgentCardProperties cardProperties) {
        var cp = cardProviders.getIfAvailable();
        if (cp != null) {
            return cp.agentCard();
        }
        String name;
        if (cardProperties.hasExplicitName()) {
            name = cardProperties.getName();
        } else {
            List<String> agentIds = handlers.orderedStream().map(AgentRuntimeHandler::agentId).toList();
            String configured = access.getDefaultAgentId();
            if (configured != null && !configured.isBlank() && agentIds.contains(configured.trim())) {
                name = configured.trim();
            } else {
                if (configured != null && !configured.isBlank()) {
                    log.warn("agent-runtime.access.a2a.default-agent-id '{}' matches no registered handler;"
                            + " available agent ids: {}", configured.trim(), agentIds);
                }
                name = agentIds.isEmpty() ? "agent" : agentIds.get(0);
            }
        }
        return AgentCards.create(name, cardProperties.getDescription(), cardProperties.getVersion(),
                cardProperties.getEndpoint(), cardProperties.getOrganization(),
                cardProperties.getOrganizationUrl());
    }

    /**
     * Registers the northbound HTTP surface for hosts that only depend on the jar:
     * without these bean methods a pure-dependency host boots with the full engine
     * wired but every northbound route silently 404s, because the controllers are
     * plain {@code @RestController} classes that only component scanning would find.
     * Hosts that do scan {@code runtime.boot} get the same beans by stereotype; the
     * {@code @ConditionalOnMissingBean} guards keep the two paths from colliding.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class NorthboundControllerConfiguration {

        @Bean @ConditionalOnMissingBean
        A2aJsonRpcController a2aJsonRpcController(RequestHandler handler, RuntimeAccessProperties access) {
            return new A2aJsonRpcController(handler, access);
        }

        @Bean @ConditionalOnMissingBean
        AgentCardController agentCardController(AgentCard agentCard, RuntimeAccessProperties access) {
            return new AgentCardController(agentCard, access);
        }
    }
}
