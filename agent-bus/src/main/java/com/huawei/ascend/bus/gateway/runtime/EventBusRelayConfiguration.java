package com.huawei.ascend.bus.gateway.runtime;

import com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc.JdbcForwardingInbox;
import com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc.JdbcForwardingOutbox;
import com.huawei.ascend.bus.forwarding.runtime.relay.EventBusRelayWorker;
import com.huawei.ascend.bus.forwarding.runtime.transport.BrokerTopicResolver;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerClientProperties;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerForwardingConsumerPort;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerForwardingRelayPort;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.DeliveryFilter;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.RocketMqBrokerForwardingConsumer;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.RocketMqBrokerForwardingRelay;
import com.huawei.ascend.bus.forwarding.spi.ForwardingInboxPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;

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
 * Event-bus+registry process-form wiring (arch-driven G5-B, decision-tree Q2a —
 * {@link Profile @Profile("eventbus")} on the single {@code AgentBusApplication}).
 *
 * <p>Wires the two-hop governance relay as TWO {@link EventBusRelayWorker} instances:
 * <ul>
 *   <li><b>forward relay</b> — consume hop1 req ({@code ascend_bus_*_req}) → inbox dedup /
 *       tenant / correlation / audit → re-publish hop2 deliver
 *       ({@code ascend_bus_*_deliver});</li>
 *   <li><b>response relay</b> — consume resp_in ({@code ascend_bus_*_resp_in}) → govern →
 *       re-publish resp_out ({@code ascend_bus_*_resp_out}).</li>
 * </ul>
 * The registry plane boots alongside (component-scanned {@code @Configuration} classes in
 * {@code registry.runtime} are already on the classpath). The durable outbox + inbox are
 * {@link JdbcForwardingOutbox} / {@link JdbcForwardingInbox} (Flyway V1 creates the tables).
 *
 * <p><b>Relay consumer filters are tenant-only</b> ({@code DeliveryFilter(Map.of("tenantId", …))})
 * — the relay is the intermediary for its tenant, so it consumes every in-tenant message on
 * its hop-in topic (no {@code targetServiceId} pin; reqs are targeted at runtimes, responses
 * at callers — neither is the relay itself). This is the correct intermediary filter, distinct
 * from the gateway response consumer's targetServiceId-only filter (D13).
 *
 * <p><b>Verification:</b> compile-verified (full suite green); producer {@code start()} +
 * subscribe-at-startup + the relay-consume→re-produce loop boot-correctness is verified by
 * the two-hop IT (G5-E, env-guarded against the real broker). See
 * {@code docs/4plus1/delta/event-bus-relay/deviations.md}.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md §1.2 / §4.1 / §5.1};
 * {@code docs/4plus1/delta/event-bus-relay/decision-tree.md} Q1a/Q2a.
 */
@Configuration
@Profile("eventbus")
@EnableConfigurationProperties(AgentBusBrokerProperties.class)
public class EventBusRelayConfiguration {

    @Bean
    BrokerClientProperties brokerClientProperties(AgentBusBrokerProperties props) {
        return new BrokerClientProperties(props.nameserver(), props.namespace());
    }

    /** Relay RocketMQ producer (hop2 deliver + resp_out produce). */
    @Bean(destroyMethod = "shutdown")
    DefaultMQProducer relayProducer(BrokerClientProperties broker, AgentBusBrokerProperties props) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer(props.producerGroup() + "-relay");
        producer.setNamesrvAddr(broker.nameserverEndpoints());
        producer.start();
        return producer;
    }

    @Bean
    JdbcForwardingOutbox relayOutbox(DataSource dataSource) {
        return new JdbcForwardingOutbox(dataSource);
    }

    @Bean
    ForwardingInboxPort relayInbox(DataSource dataSource) {
        return new JdbcForwardingInbox(dataSource);
    }

    // ===== forward relay: hop1 req → hop2 deliver =====

    @Bean(name = "forwardRelayConsumer", destroyMethod = "close")
    BrokerForwardingConsumerPort forwardRelayConsumer(BrokerClientProperties broker, AgentBusBrokerProperties props) {
        return new RocketMqBrokerForwardingConsumer(new BrokerTopicResolver("req"),
                RocketMqBrokerForwardingConsumer.defaultPollerFactory(broker.nameserverEndpoints()),
                props.pollWaitMillis());
    }

    @Bean(name = "forwardRelayProducer")
    BrokerForwardingRelayPort forwardRelayProducer(DefaultMQProducer relayProducer) {
        return new RocketMqBrokerForwardingRelay(new BrokerTopicResolver("deliver"),
                RocketMqBrokerForwardingRelay.defaultSender(relayProducer));
    }

    @Bean(name = "forwardRelayWorker")
    EventBusRelayWorker forwardRelayWorker(@Qualifier("forwardRelayConsumer") BrokerForwardingConsumerPort consumer,
                                           ForwardingInboxPort inbox, JdbcForwardingOutbox outbox,
                                           @Qualifier("forwardRelayProducer") BrokerForwardingRelayPort relay,
                                           AgentBusBrokerProperties props) {
        return new EventBusRelayWorker(consumer, inbox, outbox, outbox, relay,
                props.eventBusServiceId(), props.eventBusServiceId(), props.leaseDurationMs(),
                EventBusRelayWorker.FORWARD_REQUEST_TYPES);
    }

    // ===== response relay: resp_in → resp_out =====

    @Bean(name = "responseRelayConsumer", destroyMethod = "close")
    BrokerForwardingConsumerPort responseRelayConsumer(BrokerClientProperties broker, AgentBusBrokerProperties props) {
        return new RocketMqBrokerForwardingConsumer(new BrokerTopicResolver("resp_in"),
                RocketMqBrokerForwardingConsumer.defaultPollerFactory(broker.nameserverEndpoints()),
                props.pollWaitMillis());
    }

    @Bean(name = "responseRelayProducer")
    BrokerForwardingRelayPort responseRelayProducer(DefaultMQProducer relayProducer) {
        return new RocketMqBrokerForwardingRelay(new BrokerTopicResolver("resp_out"),
                RocketMqBrokerForwardingRelay.defaultSender(relayProducer));
    }

    @Bean(name = "responseRelayWorker")
    EventBusRelayWorker responseRelayWorker(@Qualifier("responseRelayConsumer") BrokerForwardingConsumerPort consumer,
                                            ForwardingInboxPort inbox, JdbcForwardingOutbox outbox,
                                            @Qualifier("responseRelayProducer") BrokerForwardingRelayPort relay,
                                            AgentBusBrokerProperties props) {
        return new EventBusRelayWorker(consumer, inbox, outbox, outbox, relay,
                props.eventBusServiceId() + "-resp", props.eventBusServiceId(), props.leaseDurationMs(),
                EventBusRelayWorker.RESPONSE_TYPES);
    }

    /**
     * Subscribe-at-startup: both relay consumers register on their hop-in topics with a
     * <b>tenant-only</b> filter (the intermediary consumes every in-tenant message on its
     * hop-in topic). FIXME (G5-E): the {@code tenant} scope is a single configured value
     * (single-tenant-per-deployment); a multi-tenant relay subscribe is a refinement.
     */
    @Bean
    SmartLifecycle relaySubscriptions(
            @Qualifier("forwardRelayConsumer") BrokerForwardingConsumerPort forwardConsumer,
            @Qualifier("responseRelayConsumer") BrokerForwardingConsumerPort responseConsumer,
            AgentBusBrokerProperties props) {
        return new SmartLifecycle() {
            private boolean started;

            @Override
            public synchronized void start() {
                String eventBus = props.eventBusServiceId();
                String tenant = props.tenant();
                DeliveryFilter tenantFilter = new DeliveryFilter(Map.of("tenantId", tenant));
                // routes "invocation" + "a2a" → BrokerTopicResolver resolves to the right hop-in topic.
                forwardConsumer.subscribe(eventBus, new ForwardingRouteHandle("invocation", tenant), tenantFilter);
                forwardConsumer.subscribe(eventBus, new ForwardingRouteHandle("a2a", tenant), tenantFilter);
                responseConsumer.subscribe(eventBus + "-resp", new ForwardingRouteHandle("invocation", tenant), tenantFilter);
                responseConsumer.subscribe(eventBus + "-resp", new ForwardingRouteHandle("a2a", tenant), tenantFilter);
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
                return Integer.MIN_VALUE + 100;
            }
        };
    }
}
