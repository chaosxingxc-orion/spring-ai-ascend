package com.huawei.ascend.bus.spi.federation;

import com.huawei.ascend.bus.spi.ingress.IngressEnvelope;
import com.huawei.ascend.bus.spi.ingress.IngressResponse;

/**
 * Federation Gateway SPI Mode B Business-Centric
 * deployment.
 *
 * <p>Distinguishes in-process (Mode A) vs networked (Mode B) ingress
 * routing. In Mode A this delegates to the local in-process
 * {@link com.huawei.ascend.bus.spi.ingress.IngressGateway}. In Mode B
 * it forwards eligible requests across the network to the platform
 * Federation Hub.
 *
 * <p>Federation broker technology choice (Kafka / NATS / in-house) is
 * deferred to a separate future ADR. This SPI
 * is broker-agnostic.
 *
 * <p>The in-process bus shim on the business side (Mode B) implements
 * the same {@link com.huawei.ascend.bus.spi.ingress.IngressGateway}
 * SPI; this FederationGateway is the cross-network forwarding layer
 * that wraps it.
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} + own
 * siblings + {@code bus.spi.ingress} carrier types.
 */
public interface FederationGateway {

    /**
     * Route a client request, possibly forwarding across the network
     * to the platform Federation Hub.
     *
     * @param envelope the immutable ingress envelope.
     * @return response carrying either a Task Cursor or a rejection.
     */
    IngressResponse routeFederated(IngressEnvelope envelope);
}
