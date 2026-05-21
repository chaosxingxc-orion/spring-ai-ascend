/**
 * Stateless engine SPI per ADR-0100 (rc22).
 *
 * <p>Contains pure-function compute surface for the Execution Engine
 * Adapter component (per ADR-0100 5-component decomposition of
 * agent-service). The {@link com.huawei.ascend.service.engine.spi.StatelessEngine}
 * interface is the contract; {@link com.huawei.ascend.service.engine.spi.AgentInvokeRequest}
 * and {@link com.huawei.ascend.service.engine.spi.StateDelta} are the carrier records.
 *
 * <p>Wire contract:
 * {@code docs/contracts/agent-invoke-request.v1.yaml} (status:
 * design_only at rc22; reference impl in rc24).
 *
 * <p>SPI purity (Rule R-D): imports only {@code java.*} and own
 * siblings. Concrete adapters land in
 * {@code com.huawei.ascend.service.engine.adapter}.
 */
package com.huawei.ascend.service.engine.spi;
