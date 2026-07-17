package com.huawei.ascend.bus.forwarding.common;

import com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc.JdbcForwardingInbox;
import com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc.JdbcForwardingOutbox;
import com.huawei.ascend.bus.forwarding.runtime.transport.broker.BrokerClientProperties;
import com.huawei.ascend.bus.forwarding.spi.ForwardingInboxPort;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Agent-bus shared infrastructure bean assembly — default (no {@code @Profile}, always
 * assembled for every process form).
 *
 * <p>Owns the beans common to BOTH the gateway process form
 * ({@code @Profile("gateway")}) and the event-bus process form
 * ({@code @Profile("eventbus")}):
 * <ul>
 *   <li>{@link BrokerClientProperties} — generic, broker-neutral connection config
 *       (no RocketMQ / Kafka types); consumed by every broker adapter;</li>
 *   <li>{@link JdbcForwardingOutbox} — durable outbox, implements BOTH
 *       {@code ForwardingOutboxPort} AND {@code ForwardingOutboxClaimPort}; shared by
 *       the gateway (request dispatch) and the event-bus relay (re-publish);</li>
 *   <li>{@link ForwardingInboxPort} — durable inbox for event-bus relay governance
 *       dedup (gateway does not use it, but the bean is harmless when idle).</li>
 * </ul>
 *
 * <p>These are role-agnostic — every process form that connects to the broker or
 * persists forwarding state needs them. Co-locating them here removes the duplicate
 * bean declarations that previously lived in {@code JdbcForwardingGatewayConfiguration}
 * (gateway, now deleted) and {@code EventBusRelayConfiguration} (eventbus).
 * {@link EnableConfigurationProperties} is enabled HERE so both process-form configs
 * inject {@link AgentBusBrokerProperties} without re-enabling it.
 *
 * <p><b>Package choice.</b> Lives in {@code forwarding.common} (the shared-config plane,
 * already Spring-licensed by the {@link AgentBusBrokerProperties} record exemption in
 * {@link com.huawei.ascend.bus.architecture.AgentBusForwardingSpiPurityTest}) rather
 * than {@code forwarding.runtime} (which is Spring-forbidden at the direct-package
 * level — only its {@code .relay} / {@code .persistence.jdbc} /
 * {@code .transport.broker} subpackages are Spring-licensed). This mirrors how
 * {@code AgentBusBrokerProperties} itself lives here.
 *
 * <p><b>Role-specific beans stay in their process-form configs:</b>
 * <ul>
 *   <li>{@code DefaultMQProducer} — the gateway and eventbus use different producer
 *       groups ({@code props.producerGroup()} vs {@code props.producerGroup() + "-relay"}),
 *       so each process form declares its own;</li>
 *   <li>relay / consumer beans — each binds a role-specific topic suffix
 *       ({@code "req"} / {@code "resp_out"} / {@code "deliver"} / {@code "resp_in"}),
 *       so they cannot be shared.</li>
 * </ul>
 *
 * <p>Authority: arch-driven gateway-assembly-purify follow-on (shared-infra extraction);
 * ADR-0163 (forwarding-reorg layering).
 */
@Configuration
@EnableConfigurationProperties(AgentBusBrokerProperties.class)
public class AgentBusInfrastructureConfiguration {

    /** Generic broker connection config — broker-neutral (no RocketMQ / Kafka types). */
    @Bean
    BrokerClientProperties brokerClientProperties(AgentBusBrokerProperties props) {
        return new BrokerClientProperties(props.nameserver(), props.namespace());
    }

    /**
     * Durable outbox — implements {@code ForwardingOutboxPort} AND
     * {@code ForwardingOutboxClaimPort}; typed for injection as either SPI port. Shared
     * by the gateway (request dispatch) and the event-bus relay (re-publish).
     */
    @Bean
    JdbcForwardingOutbox forwardingOutbox(DataSource dataSource) {
        return new JdbcForwardingOutbox(dataSource);
    }

    /**
     * Durable inbox — event-bus relay governance dedup. Declared on the shared config so
     * the event-bus process form injects it without owning the bean (the gateway does not
     * use it, but the bean is harmless when idle).
     */
    @Bean
    ForwardingInboxPort forwardingInbox(DataSource dataSource) {
        return new JdbcForwardingInbox(dataSource);
    }
}
