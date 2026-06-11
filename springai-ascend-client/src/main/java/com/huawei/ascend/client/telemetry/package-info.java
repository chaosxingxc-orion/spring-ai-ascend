/**
 * Optional client-side observability for the A2A facade: business spans per
 * send/stream, outbound {@code traceparent} derived from the active span,
 * {@code traceresponse} correlation as a span attribute, posture-gated PII
 * redaction, and a self-contained OTLP/HTTP export pipeline.
 *
 * <p>This is the only package allowed to touch {@code io.opentelemetry.*}
 * (enforced by {@code ClientPackageBoundaryTest}), and all OTel dependencies
 * are optional: the always-loaded surface ({@link
 * com.huawei.ascend.client.telemetry.ClientTelemetry}, {@link
 * com.huawei.ascend.client.telemetry.ClientCallSpan}, {@link
 * com.huawei.ascend.client.telemetry.Posture}) references no OTel type, so a
 * consumer without OTel on the classpath keeps the full client surface and
 * only the OTel-backed factories are unavailable.
 *
 * <p>Authority: ADR-0162.
 */
package com.huawei.ascend.client.telemetry;
