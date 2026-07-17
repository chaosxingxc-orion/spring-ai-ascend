package com.huawei.ascend.bus.gateway.runtime;

import com.huawei.ascend.bus.forwarding.common.AgentBusBrokerProperties;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxClaimPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerForwardingConsumerPort;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerForwardingRelayPort;
import com.huawei.ascend.bus.forwarding.spi.broker.DeliveryFilter;
import com.huawei.ascend.bus.spi.ingress.IngressGateway;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Map;

/**
 * Gateway process-form wiring — SPI-port-injection form (arch-driven
 * gateway-assembly-purify, the flagged ADR-0163 follow-on; G4 sign-off 2026-07-16;
 * de-gateway-ification follow-on 2026-07-16).
 *
 * <p>Prior form (ADR-0163 as-built) constructed 5 concrete-adapter {@code @Bean} HERE
 * — {@code brokerClientProperties}, {@code gatewayProducer} ({@code DefaultMQProducer}),
 * {@code gatewayOutbox} ({@code JdbcForwardingOutbox}), {@code gatewayRelay}
 * ({@code RocketMqBrokerForwardingRelay}), {@code gatewayResponseConsumer}
 * ({@code RocketMqBrokerForwardingConsumer}) — importing {@code rocketmq.*} + the JDBC
 * adapter + the transport resolver + broker-common, i.e. crossing the plane into
 * {@code forwarding.runtime.*} (the ADR-0163 accepted drift: the literal
 * {@code gateway↛forwarding.runtime} was infeasible). This change moves that assembly
 * into two forwarding adapter {@code @Configuration}s co-located with their adapters:
 * {@link com.huawei.ascend.bus.forwarding.runtime.transport.broker.rocketmq.RocketMqBrokerClientConfiguration}
 * (owns the generic client-side broker beans — {@code defaultProducer} /
 * {@code requestRelay} / {@code responseConsumer}, de-gateway-ified so any caller
 * reuses them) +
 * {@link com.huawei.ascend.bus.forwarding.common.AgentBusInfrastructureConfiguration}
 * (owns the shared outbox/inbox/broker-client-properties). This {@code @Configuration}
 * now keeps ONLY the 2 SPI-only {@code @Bean} — the {@link GatewayRuntimeService} bean
 * (injects the SPI ports) + the {@code gatewayResponseSubscription} SmartLifecycle
 * (subscribe-at-startup, SPI-only) — and imports only {@code forwarding.spi} +
 * {@code forwarding.spi.broker} + {@code forwarding.common} + {@code bus.spi.ingress}.
 * The gateway plane now depends on NO {@code forwarding.runtime} type:
 * {@code gateway.runtime.. ↛ forwarding.runtime..} holds literally (the ADR-0163 drift
 * closed).
 *
 * <p>{@link AgentBusBrokerProperties} is enabled by
 * {@link com.huawei.ascend.bus.forwarding.common.AgentBusInfrastructureConfiguration}
 * (the shared infra config, no {@code @Profile}); this config injects it from the shared
 * context. The gateway injects the client-side broker beans by qualifier —
 * {@code @Qualifier("requestRelay")} + {@code @Qualifier("responseConsumer")} —
 * provided by {@link com.huawei.ascend.bus.forwarding.runtime.transport.broker.rocketmq.RocketMqBrokerClientConfiguration}
 * (no {@code @Profile}; the bean names are role-agnostic, de-gateway-ified).
 *
 * <p>Authority: {@code docs/4plus1/delta/gateway-assembly-purify/} (G2 as-is · G3
 * decision tree · G4 to-be + sign-off); ADR-0163 (the forwarding-reorg this closes).
 */
@Configuration
@Profile("gateway")
public class GatewayRuntimeConfiguration {

    @Bean
    IngressGateway gatewayRuntimeService(ForwardingOutboxPort outbox,
                                         ForwardingOutboxClaimPort outboxClaim,
                                         @Qualifier("requestRelay") BrokerForwardingRelayPort relay,
                                         @Qualifier("responseConsumer") BrokerForwardingConsumerPort responseConsumer,
                                         AgentBusBrokerProperties props) {
        return new GatewayRuntimeService(outbox, outboxClaim, relay, responseConsumer,
                props.gatewayServiceId(), props.acceptTimeoutMs(), props.responseTimeoutMs(),
                System::currentTimeMillis);
    }

    /**
     * Subscribe-at-startup: the gateway response consumer registers on the FEAT-013/014
     * resp_out topics so {@code acceptWindow} can poll them. The filter is
     * <b>targetServiceId-only</b> ({@code DeliveryFilter(Map.of("targetServiceId", gatewayId))})
     * — D13-compliant for a multi-tenant gateway (the broker delivers every response
     * targeted at this gateway; tenant is filtered client-side in {@code acceptWindow}).
     * This RESOLVES the deferred-#3 D13 drift for the response consumer (the slice-3 IT
     * used {@code forRuntime}, which pins tenant — wrong for multi-tenant).
     *
     * <p>Unchanged by gateway-assembly-purify: this bean already took only SPI types
     * ({@link BrokerForwardingConsumerPort} + {@link DeliveryFilter} +
     * {@link ForwardingRouteHandle}); it now receives the adapter-provided
     * {@code responseConsumer} bean (de-gateway-ified from the prior
     * {@code gatewayResponseConsumer} name) instead of a gateway-constructed one.
     */
    @Bean
    SmartLifecycle gatewayResponseSubscription(
            @Qualifier("responseConsumer") BrokerForwardingConsumerPort responseConsumer,
            AgentBusBrokerProperties props) {
        return new SmartLifecycle() {
            private boolean started;

            @Override
            public synchronized void start() {
                String gateway = props.gatewayServiceId();
                DeliveryFilter filter = new DeliveryFilter(Map.of("targetServiceId", gateway));
                // routes "invocation" + "a2a" → BrokerTopicResolver("resp_out") resolves to
                // ascend_bus_invocation_resp_out / ascend_bus_a2a_resp_out.
                responseConsumer.subscribe(gateway, new ForwardingRouteHandle("invocation", gateway), filter);
                responseConsumer.subscribe(gateway, new ForwardingRouteHandle("a2a", gateway), filter);
                started = true;
            }

            @Override
            public synchronized void stop() {
                started = false;
            }

            @Override
            public boolean isRunning() {
                return started;
            }

            @Override
            public int getPhase() {
                return Integer.MIN_VALUE + 100; // subscribe before the app is ready
            }
        };
    }
}
