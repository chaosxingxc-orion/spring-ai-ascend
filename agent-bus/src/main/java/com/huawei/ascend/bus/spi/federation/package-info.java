/**
 * Federation Hub SPI.
 *
 * <p>Hosts {@link com.huawei.ascend.bus.spi.federation.FederationGateway}
 * — the Mode B Business-Centric deployment topology SPI for cross-network
 * ingress forwarding.
 *
 * <p>Federation broker technology choice (Kafka / NATS / in-house) is
 * deferred to a separate future ADR.
 *
 * <p>SPI purity (Rule R-D): imports only {@code java.*} + own siblings
 * + {@code bus.spi.ingress} carrier types.
 */
package com.huawei.ascend.bus.spi.federation;
