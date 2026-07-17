package com.huawei.ascend.bus.forwarding.runtime.transport.broker.rocketmq;

import com.huawei.ascend.bus.forwarding.common.AgentBusBrokerProperties;
import com.huawei.ascend.bus.forwarding.runtime.transport.BrokerTopicResolver;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerClientProperties;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerForwardingConsumerPort;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerForwardingRelayPort;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ broker client bean assembly — <b>role-agnostic, no {@code @Profile}</b>
 * (arch-driven gateway-assembly-purify follow-on: de-gateway-ification, 2026-07-16).
 *
 * <p>Owns the generic <b>client-side</b> RocketMQ beans — the ones any process form
 * needs when it acts as a <b>caller</b> (produces hop1 requests + consumes hop
 * responses):
 * <ul>
 *   <li>{@code defaultProducer} ({@link DefaultMQProducer}, group=
 *       {@code props.producerGroup()}) — the base producer group; consumed by
 *       {@link #requestRelay} for hop1 produce. The event-bus process form declares
 *       its OWN {@code relayProducer} (group={@code props.producerGroup() + "-relay"})
 *       because it needs a distinct group for governance traceability;</li>
 *   <li>{@code requestRelay} ({@link RocketMqBrokerForwardingRelay} with
 *       {@code BrokerTopicResolver("req")} → {@code ascend_bus_*_req}) — hop1 request
 *       produce. Used by the gateway AND by any agent-runtime acting as a caller
 *       (invoking another agent);</li>
 *   <li>{@code responseConsumer} ({@link RocketMqBrokerForwardingConsumer} with
 *       {@code BrokerTopicResolver("resp_out")} → {@code ascend_bus_*_resp_out}) —
 *       response consume. Used by the gateway AND by any agent-runtime awaiting a
 *       response to its own outbound call.</li>
 * </ul>
 *
 * <p><b>Why these beans are NOT gateway-specific.</b> The prior name
 * ({@code RocketMqGatewayBrokerConfiguration}) + bean names
 * ({@code gatewayProducer} / {@code gatewayRelay} / {@code gatewayResponseConsumer})
 * implied gateway-only ownership — but the only gateway-specific thing here is the
 * caller role. Any agent-runtime that calls another agent (e.g. an orchestration
 * runtime invoking a tool agent) produces the same hop1 {@code req} topic and
 * consumes the same {@code resp_out} topic. Renaming to {@code defaultProducer} /
 * {@code requestRelay} / {@code responseConsumer} makes that explicit and lets an
 * agent-runtime reuse these beans instead of re-declaring them.
 *
 * <p><b>No {@code @Profile}.</b> The gateway process form activates its wiring via
 * {@code @Profile("gateway")} on {@code GatewayRuntimeConfiguration} (which injects
 * these beans by qualifier); the event-bus process form declares its own
 * relay/consumer beans in {@code EventBusRelayConfiguration} (different topic
 * suffixes: {@code deliver} / {@code resp_in}) and ignores these. When both process
 * forms co-reside in a dev / test context, the client-side beans here are harmless
 * (created but never injected into event-bus workers). The shared
 * {@link BrokerClientProperties} bean is provided by
 * {@link com.huawei.ascend.bus.forwarding.common.AgentBusInfrastructureConfiguration}.
 *
 * <p>Co-located with the RocketMQ adapters in {@code transport.broker.rocketmq} —
 * the package licensed for a concrete RocketMQ client (ADR-0163 Decision 4 +
 * {@link com.huawei.ascend.bus.architecture.AgentBusForwardingSpiPurityTest}'s
 * {@code transport.broker..} rocketmq-import exemption). The role→resolver mapping
 * ("req" / "resp_out") lives with the beans it constructs.
 *
 * <p>The {@code brokerType} switch (RocketMQ / Kafka / InMemory) is DELIBERATELY
 * DEFERRED: only RocketMQ exists in production (Kafka is purity-forbidden; no
 * production InMemory broker), so a 3-way {@code @ConditionalOnProperty} switch
 * would be 1 real arm + 2 non-existent stubs (premature abstraction). A future 2nd
 * broker adds its own {@code @ConditionalOnProperty} config as a clean additive
 * follow-on (sibling {@code kafka/} / {@code nats/} package structure is ready).
 *
 * <p>Authority: {@code docs/4plus1/delta/gateway-assembly-purify/} (de-gateway-
 * ification follow-on); ADR-0163 (forwarding-reorg layering).
 */
@Configuration
public class RocketMqBrokerClientConfiguration {

    /**
     * Default RocketMQ producer — base group ({@code props.producerGroup()}), used by
     * {@link #requestRelay} for hop1 request produce. Any caller (gateway or
     * agent-runtime) can inject this for direct produce. Lifecycle: start on bean
     * creation, shutdown on close.
     */
    @Bean(destroyMethod = "shutdown")
    DefaultMQProducer defaultProducer(BrokerClientProperties broker, AgentBusBrokerProperties props) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer(props.producerGroup());
        producer.setNamesrvAddr(broker.nameserverEndpoints());
        producer.start();
        return producer;
    }

    /**
     * Hop1 request relay — produces to {@code ascend_bus_*_req} via
     * {@code BrokerTopicResolver("req")}. Used by the gateway AND by any
     * agent-runtime acting as a caller (invoking another agent).
     */
    @Bean(name = "requestRelay")
    BrokerForwardingRelayPort requestRelay(DefaultMQProducer defaultProducer) {
        return new RocketMqBrokerForwardingRelay(new BrokerTopicResolver("req"),
                RocketMqBrokerForwardingRelay.defaultSender(defaultProducer));
    }

    /**
     * Response consumer — consumes from {@code ascend_bus_*_resp_out} via
     * {@code BrokerTopicResolver("resp_out")}. Used by the gateway AND by any
     * agent-runtime awaiting a response to its own outbound call.
     */
    @Bean(name = "responseConsumer", destroyMethod = "close")
    BrokerForwardingConsumerPort responseConsumer(BrokerClientProperties broker, AgentBusBrokerProperties props) {
        return new RocketMqBrokerForwardingConsumer(new BrokerTopicResolver("resp_out"),
                RocketMqBrokerForwardingConsumer.defaultPollerFactory(broker.nameserverEndpoints()),
                props.pollWaitMillis());
    }
}
