/**
 * Platform Resource Interception SPI surface for M6 Translation &amp;
 * Tool-Intercept.
 *
 * <p>Authority: ADR-0155 §3 (M6 prompt-construction reversal). Status:
 * design_only at this commit. Native Agents inject these beans via DI;
 * Third-party adapters wrap their framework's Model / Toolkit / Memory
 * abstractions around these contracts.</p>
 */
package com.huawei.ascend.service.runtime.intercept.spi;
