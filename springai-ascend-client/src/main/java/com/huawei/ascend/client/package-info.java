/**
 * A2A client SDK facade for external developers calling a spring-ai-ascend
 * A2A ingress (agent-runtime directly, or the agent-service facade edge).
 *
 * <p>The protocol client is the OSS a2a-java-sdk ({@code JSONRPCTransport} +
 * {@code A2ACardResolver}); this package adds only what the platform's wire
 * behavior requires from a client and what every consumer would otherwise
 * re-implement by hand:
 *
 * <ul>
 *   <li>terminal-event detection across both shapes the runtime emits
 *       (final A2A {@code TaskStatusUpdateEvent} states and {@code Message}
 *       metadata {@code runStatus}), and the post-terminal-cancellation rule
 *       (a {@code CancellationException} after a terminal event is normal SSE
 *       unsubscription, never a transport failure);</li>
 *   <li>outbound W3C {@code traceparent} propagation plus correlation with
 *       the {@code traceresponse} header the platform answers on every
 *       response — derived from the call's business span when the optional
 *       {@code com.huawei.ascend.client.telemetry} observability is
 *       configured;</li>
 *   <li>JWT bearer + {@code X-Tenant-Id} cross-check headers matching the
 *       platform's tenant authentication at both ingress edges (ADR-0040).</li>
 * </ul>
 *
 * <p>Main scope is Spring-free and depends on no platform server module, so
 * the SDK embeds in any customer application.
 *
 * <p>Authority: ADR-0162.
 */
package com.huawei.ascend.client;
