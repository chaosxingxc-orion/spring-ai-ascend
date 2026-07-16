package com.huawei.ascend.bus.gateway.runtime;

import com.huawei.ascend.bus.common.AgentBusBrokerProperties;
import com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc.JdbcForwardingOutbox;
import com.huawei.ascend.bus.forwarding.runtime.transport.BrokerTopicResolver;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerClientProperties;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerForwardingConsumerPort;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerForwardingRelayPort;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.DeliveryFilter;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.RocketMqBrokerForwardingConsumer;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.RocketMqBrokerForwardingRelay;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.spi.ingress.IngressGateway;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Gateway process-form wiring (arch-driven G5-B, decision-tree Q2a —
 * {@link Profile @Profile("gateway")} on the single {@code AgentBusApplication}).
 *
 * <p>Wires the gateway side of the two-hop relay: a durable outbox
 * ({@link JdbcForwardingOutbox}), the hop1 produce relay (RocketMQ producer →
 * {@code BrokerTopicResolver("req")} → {@code ascend_bus_*_req}), the response consumer
 * (polls {@code ascend_bus_*_resp_out}), the {@link GatewayRuntimeService} bean, and the
 * subscribe-at-startup that registers the response consumer on the resp_out topics.
 * {@link GatewayRuntimeController} is component-scanned + gated to this profile.
 *
 * <p><b>Verification:</b> compile-verified (full suite green); the broker-producer
 * {@code start()} + subscribe-at-startup boot-correctness is verified by the two-hop IT
 * (G5-E, env-guarded against the real broker). See {@code docs/4plus1/delta/event-bus-relay/deviations.md}.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md §4.1 / §5.1};
 * {@code docs/4plus1/delta/event-bus-relay/decision-tree.md} Q1a/Q2a.
 */
@Configuration
@Profile("gateway")
@EnableConfigurationProperties(AgentBusBrokerProperties.class)
public class GatewayRuntimeConfiguration {

    @Bean
    BrokerClientProperties brokerClientProperties(AgentBusBrokerProperties props) {
        return new BrokerClientProperties(props.nameserver(), props.namespace());
    }

    /** Gateway RocketMQ producer (hop1 produce). Lifecycle: start on bean creation, shutdown on close. */
    @Bean(destroyMethod = "shutdown")
    DefaultMQProducer gatewayProducer(BrokerClientProperties broker, AgentBusBrokerProperties props) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer(props.producerGroup());
        producer.setNamesrvAddr(broker.nameserverEndpoints());
        producer.start();
        return producer;
    }

    /** Gateway durable outbox — implements {@link ForwardingOutboxPort} AND ForwardingOutboxClaimPort. */
    @Bean
    JdbcForwardingOutbox gatewayOutbox(DataSource dataSource) {
        return new JdbcForwardingOutbox(dataSource);
    }

    @Bean(name = "gatewayRelay")
    BrokerForwardingRelayPort gatewayRelay(DefaultMQProducer gatewayProducer) {
        return new RocketMqBrokerForwardingRelay(new BrokerTopicResolver("req"),
                RocketMqBrokerForwardingRelay.defaultSender(gatewayProducer));
    }

    @Bean(name = "gatewayResponseConsumer", destroyMethod = "close")
    BrokerForwardingConsumerPort gatewayResponseConsumer(BrokerClientProperties broker, AgentBusBrokerProperties props) {
        return new RocketMqBrokerForwardingConsumer(new BrokerTopicResolver("resp_out"),
                RocketMqBrokerForwardingConsumer.defaultPollerFactory(broker.nameserverEndpoints()),
                props.pollWaitMillis());
    }

    @Bean
    IngressGateway gatewayRuntimeService(JdbcForwardingOutbox gatewayOutbox,
                                         @Qualifier("gatewayRelay") BrokerForwardingRelayPort relay,
                                         @Qualifier("gatewayResponseConsumer") BrokerForwardingConsumerPort responseConsumer,
                                         AgentBusBrokerProperties props) {
        return new GatewayRuntimeService(gatewayOutbox, gatewayOutbox, relay, responseConsumer,
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
     */
    @Bean
    SmartLifecycle gatewayResponseSubscription(
            @Qualifier("gatewayResponseConsumer") BrokerForwardingConsumerPort responseConsumer,
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
