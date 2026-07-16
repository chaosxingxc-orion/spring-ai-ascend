package com.huawei.ascend.bus.gateway.runtime;

import com.huawei.ascend.bus.forwarding.runtime.transport.broker.InMemoryBroker;
import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.InvocationResponseStatus;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerForwardingConsumerPort;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerForwardingRelayPort;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerInboundMessage;
import com.huawei.ascend.bus.forwarding.spi.broker.BrokerProduceOutcome;
import com.huawei.ascend.bus.forwarding.spi.broker.DeliveryFilter;
import com.huawei.ascend.bus.forwarding.test.InMemoryForwardingOutbox;
import com.huawei.ascend.bus.spi.ingress.IngressEnvelope;
import com.huawei.ascend.bus.spi.ingress.IngressResponse;
import com.huawei.ascend.bus.test.TestAgentRuntime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit + E2E tests for {@link GatewayRuntimeService} — the S2 IngressGateway
 * implementation for FEAT-013 client-invocation event forwarding.
 *
 * <p>Unit tests pin: requestType→eventType envelope mapping, dispatchRequest
 * (enqueue→claim→produce→markAcked), acceptWindow (skip-own self-consumption,
 * match-by-correlationId, timeout→UNKNOWN/deferred, ACCEPTED→ACCEPTED_WITH_TASK,
 * RESPONSE/TERMINAL→COMPLETED_RESPONSE, STREAM_READY→cursor), classify (each
 * eventType), toIngressResponse (each status), and the SSE-classify (STREAM_READY).
 *
 * <p>E2E tests wire {@link InMemoryBroker} + {@link InMemoryForwardingOutbox} +
 * {@link TestAgentRuntime} + {@link GatewayRuntimeService} and cover the L2 §6
 * scenarios: §6.2.1 blocking final response, §6.2.2 degenerate to Task ref,
 * §6.2.3 UNKNOWN (silent), §6.2.4 streaming. The gateway dispatches the request,
 * the runtime polls+processes it (producing responses back to the broker), and the
 * gateway accept-window polls the responses and returns the IngressResponse.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/
 * feat-013-client-invocation-event-forwarding.md §4.2 / §4.3 / §4.6 / §6}.
 */
class GatewayRuntimeServiceTest {

    private static final String TENANT = "tenant-a";
    private static final String GATEWAY = "test-gateway";
    private static final String RUNTIME = "test-agent-runtime";
    private static final String ROUTE = "route-tenant-a";
    private static final String TRACE = "0123456789abcdef0123456789abcdef";
    private static final long ACCEPT_TIMEOUT_MS = 5_000L;
    private static final long RESPONSE_TIMEOUT_MS = 30_000L;
    private static final long NOW = 1_000L;

    // ===== dispatchRequest: envelope mapping =====

    @Test
    void dispatch_request_maps_run_create_to_invocation_requested() {
        ForwardingEnvelope env = dispatch(IngressEnvelope.IngressRequestType.RUN_CREATE);
        assertThat(env.eventType()).isEqualTo(AgentBusEventType.CLIENT_INVOCATION_REQUESTED);
    }

    @Test
    void dispatch_request_maps_each_request_type() {
        assertThat(dispatch(IngressEnvelope.IngressRequestType.RUN_CANCEL).eventType())
                .isEqualTo(AgentBusEventType.CLIENT_INVOCATION_CANCEL_REQUESTED);
        assertThat(dispatch(IngressEnvelope.IngressRequestType.RUN_GET).eventType())
                .isEqualTo(AgentBusEventType.CLIENT_INVOCATION_QUERY_REQUESTED);
        assertThat(dispatch(IngressEnvelope.IngressRequestType.RUN_RESUME).eventType())
                .isEqualTo(AgentBusEventType.CLIENT_STREAM_SUBSCRIBE_REQUESTED);
    }

    @Test
    void dispatch_request_envelope_carries_control_fields_and_descriptor() {
        UUID requestId = UUID.randomUUID();
        UUID idem = UUID.randomUUID();
        ForwardingEnvelope env = dispatch(requestId, idem, IngressEnvelope.IngressRequestType.RUN_CREATE);

        assertThat(env.tenantId()).isEqualTo(TENANT);
        assertThat(env.traceId()).isEqualTo(TRACE);
        assertThat(env.correlationId()).isEqualTo(requestId.toString());
        assertThat(env.idempotencyKey()).isEqualTo(idem.toString());
        assertThat(env.routeHandle().value()).isEqualTo(ROUTE);
        assertThat(env.routeHandle().tenantScope()).isEqualTo(TENANT);
        assertThat(env.capability()).isEqualTo("cap-1");
        assertThat(env.sourceServiceId()).isEqualTo(GATEWAY);
        assertThat(env.targetServiceId()).isEqualTo(RUNTIME);
        assertThat(env.deadlineMillisEpoch()).isEqualTo(Long.MAX_VALUE);
        assertThat(env.payloadPolicy()).isEqualTo(ForwardingEnvelope.PayloadPolicy.DATA_BEARING);
        // the control descriptor rides in payloadRef
        assertThat(env.payloadRef()).contains("eventType=" + env.eventType().name());
        assertThat(env.payloadRef()).contains("correlationId=" + requestId.toString());
        assertThat(env.payloadRef()).contains("idempotencyKey=" + idem.toString());
        assertThat(env.payloadRef()).contains("routeHandle=" + ROUTE);
        assertThat(env.payloadRef()).contains("capability=cap-1");
        assertThat(env.payloadRef()).contains("deadline=" + Long.MAX_VALUE);
    }

    @Test
    void dispatch_request_enqueues_claims_produces_and_acks() {
        RecordingRelay relay = new RecordingRelay();
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        GatewayRuntimeService gw = newGateway(outbox, outbox, relay, new FakeConsumer());
        ForwardingEnvelope env = gw.dispatchRequest(ingress(UUID.randomUUID(),
                IngressEnvelope.IngressRequestType.RUN_CREATE));

        // the relay saw exactly one produced record, carrying the envelope's routing metadata
        assertThat(relay.produced).hasSize(1);
        ForwardingOutboxRecord record = relay.produced.get(0);
        assertThat(record.messageId()).isEqualTo(env.messageId());
        assertThat(record.tenantId()).isEqualTo(TENANT);
        assertThat(record.sourceServiceId()).isEqualTo(GATEWAY);
        assertThat(record.targetServiceId()).isEqualTo(RUNTIME);
        assertThat(record.correlationId()).isEqualTo(env.correlationId());
        assertThat(record.eventType()).isEqualTo(env.eventType());
        // the outbox record is now ACKED (terminal-success)
        assertThat(outbox.statusOf(env.messageId(), TENANT))
                .isEqualTo(com.huawei.ascend.bus.forwarding.spi.ForwardingStatus.Outbox.ACKED);
    }

    @Test
    void dispatch_request_uses_deadline_override_when_present() {
        IngressEnvelope req = new IngressEnvelope(UUID.randomUUID(), TENANT, UUID.randomUUID(),
                IngressEnvelope.IngressRequestType.RUN_CREATE, "payload", TRACE,
                99_999L, Map.of("routeHandle", ROUTE, "targetServiceId", RUNTIME, "capability", "cap-1"));
        GatewayRuntimeService gw = newGateway(new InMemoryForwardingOutbox(), new InMemoryForwardingOutbox(),
                new RecordingRelay(), new FakeConsumer());
        ForwardingEnvelope env = gw.dispatchRequest(req);
        assertThat(env.deadlineMillisEpoch()).isEqualTo(99_999L);
        assertThat(env.payloadRef()).contains("deadline=99999");
    }

    @Test
    void dispatch_request_defaults_capability_when_omitted() {
        IngressEnvelope req = new IngressEnvelope(UUID.randomUUID(), TENANT, UUID.randomUUID(),
                IngressEnvelope.IngressRequestType.RUN_CREATE, "payload", TRACE, null,
                Map.of("routeHandle", ROUTE, "targetServiceId", RUNTIME));
        GatewayRuntimeService gw = newGateway(new InMemoryForwardingOutbox(), new InMemoryForwardingOutbox(),
                new RecordingRelay(), new FakeConsumer());
        ForwardingEnvelope env = gw.dispatchRequest(req);
        assertThat(env.capability()).isEqualTo("a2a");
        assertThat(env.payloadRef()).contains("capability=a2a");
    }

    @Test
    void dispatch_request_requires_route_handle_attribute() {
        IngressEnvelope req = new IngressEnvelope(UUID.randomUUID(), TENANT, UUID.randomUUID(),
                IngressEnvelope.IngressRequestType.RUN_CREATE, "payload", TRACE, null,
                Map.of("targetServiceId", RUNTIME));
        GatewayRuntimeService gw = newGateway(new InMemoryForwardingOutbox(), new InMemoryForwardingOutbox(),
                new RecordingRelay(), new FakeConsumer());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> gw.dispatchRequest(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("routeHandle");
    }

    // ===== acceptWindow =====

    @Test
    void accept_window_skips_own_request_self_consumption() {
        FakeConsumer consumer = new FakeConsumer();
        UUID requestId = UUID.randomUUID();
        // the gateway's own request (source == gateway) must be skipped (committed) without processing
        consumer.queue.add(inbound(TENANT, "req-1", GATEWAY, requestId.toString(),
                AgentBusEventType.CLIENT_INVOCATION_REQUESTED, "eventType=CLIENT_INVOCATION_REQUESTED"));
        consumer.queue.add(inbound(TENANT, "resp-1", RUNTIME, requestId.toString(),
                AgentBusEventType.INVOCATION_RESPONSE, "taskId=t-1;status=snapshot"));
        GatewayRuntimeService gw = newGateway(consumer);

        IngressResponse resp = gw.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        assertThat(resp.cursor()).isEqualTo("t-1");
        // both messages were consumed (queue drained)
        assertThat(consumer.queue).isEmpty();
    }

    @Test
    void accept_window_commits_non_matching_responses_and_keeps_polling() {
        FakeConsumer consumer = new FakeConsumer();
        UUID requestId = UUID.randomUUID();
        UUID otherRequest = UUID.randomUUID();
        // a response for a DIFFERENT request → committed (advance), keep polling
        consumer.queue.add(inbound(TENANT, "resp-other", RUNTIME, otherRequest.toString(),
                AgentBusEventType.INVOCATION_ACCEPTED, "taskId=t-other"));
        // then the matching response
        consumer.queue.add(inbound(TENANT, "resp-1", RUNTIME, requestId.toString(),
                AgentBusEventType.INVOCATION_RESPONSE, "taskId=t-1;status=snapshot"));
        GatewayRuntimeService gw = newGateway(consumer);

        IngressResponse resp = gw.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        assertThat(resp.cursor()).isEqualTo("t-1");
    }

    @Test
    void accept_window_skips_cross_tenant_response_and_keeps_polling() {
        // D13 response-side: the response consumer is subscribed with a targetServiceId-only broker
        // filter, so a cross-tenant response (same targetServiceId, different tenant) is still
        // delivered to the gateway. The gateway's client-side tenant check (Rule R-C.c) commits and
        // skips it, then keeps polling for this request's own-tenant response. FakeConsumer does no
        // broker-side filtering (supportsBrokerSidePropertyFilter=false), so the cross-tenant response
        // reaches acceptWindow and exercises the client-side tenant gate directly.
        FakeConsumer consumer = new FakeConsumer();
        UUID requestId = UUID.randomUUID();
        // cross-tenant (tenant-b) response with a MATCHING correlationId — must be skipped, not matched
        consumer.queue.add(inbound("tenant-b", "resp-x", RUNTIME, requestId.toString(),
                AgentBusEventType.INVOCATION_RESPONSE, "taskId=t-x;status=snapshot"));
        // then the in-tenant matching response
        consumer.queue.add(inbound(TENANT, "resp-1", RUNTIME, requestId.toString(),
                AgentBusEventType.INVOCATION_RESPONSE, "taskId=t-1;status=snapshot"));
        GatewayRuntimeService gw = newGateway(consumer);

        IngressResponse resp = gw.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        // cursor is the in-tenant taskId (t-1), NOT the cross-tenant one (t-x) — proves the skip
        assertThat(resp.cursor()).isEqualTo("t-1");
        // both responses were consumed (queue drained); the cross-tenant one was not stranded
        assertThat(consumer.queue).isEmpty();
    }

    @Test
    void accept_window_timeout_no_accepted_returns_deferred() {
        FakeConsumer consumer = new FakeConsumer();
        UUID requestId = UUID.randomUUID();
        // no responses at all → drain immediately → UNKNOWN → deferred
        GatewayRuntimeService gw = newGateway(consumer);

        IngressResponse resp = gw.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.DEFERRED);
        assertThat(resp.cursor()).isNull();
        assertThat(resp.rejectionReason()).isNull();
    }

    @Test
    void accept_window_accepted_then_drained_returns_accepted_with_task() {
        FakeConsumer consumer = new FakeConsumer();
        UUID requestId = UUID.randomUUID();
        consumer.queue.add(inbound(TENANT, "resp-1", RUNTIME, requestId.toString(),
                AgentBusEventType.INVOCATION_ACCEPTED, "taskId=t-2"));
        // no terminal follows → window drains → ACCEPTED_WITH_TASK
        GatewayRuntimeService gw = newGateway(consumer);

        IngressResponse resp = gw.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        assertThat(resp.cursor()).isEqualTo("t-2");
    }

    @Test
    void accept_window_response_returns_completed_accepted() {
        FakeConsumer consumer = new FakeConsumer();
        UUID requestId = UUID.randomUUID();
        consumer.queue.add(inbound(TENANT, "resp-1", RUNTIME, requestId.toString(),
                AgentBusEventType.INVOCATION_RESPONSE, "taskId=t-3;status=snapshot"));
        GatewayRuntimeService gw = newGateway(consumer);

        IngressResponse resp = gw.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        assertThat(resp.cursor()).isEqualTo("t-3");
    }

    @Test
    void accept_window_terminal_completed_returns_completed_accepted() {
        FakeConsumer consumer = new FakeConsumer();
        UUID requestId = UUID.randomUUID();
        consumer.queue.add(inbound(TENANT, "resp-1", RUNTIME, requestId.toString(),
                AgentBusEventType.INVOCATION_TERMINAL, "taskId=t-4;status=completed"));
        GatewayRuntimeService gw = newGateway(consumer);

        IngressResponse resp = gw.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        assertThat(resp.cursor()).isEqualTo("t-4");
    }

    @Test
    void accept_window_terminal_failed_returns_rejected() {
        FakeConsumer consumer = new FakeConsumer();
        UUID requestId = UUID.randomUUID();
        // no reason= token → reason falls back to the status token ("failed")
        consumer.queue.add(inbound(TENANT, "resp-1", RUNTIME, requestId.toString(),
                AgentBusEventType.INVOCATION_TERMINAL, "taskId=t-5;status=failed"));
        GatewayRuntimeService gw = newGateway(consumer);

        IngressResponse resp = gw.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.REJECTED);
        assertThat(resp.rejectionReason()).isEqualTo("failed");
    }

    @Test
    void accept_window_rejected_returns_rejected() {
        FakeConsumer consumer = new FakeConsumer();
        UUID requestId = UUID.randomUUID();
        consumer.queue.add(inbound(TENANT, "resp-1", RUNTIME, requestId.toString(),
                AgentBusEventType.INVOCATION_REJECTED, "reason=not_allowed"));
        GatewayRuntimeService gw = newGateway(consumer);

        IngressResponse resp = gw.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.REJECTED);
        assertThat(resp.rejectionReason()).isEqualTo("not_allowed");
    }

    @Test
    void accept_window_failed_returns_rejected() {
        FakeConsumer consumer = new FakeConsumer();
        UUID requestId = UUID.randomUUID();
        consumer.queue.add(inbound(TENANT, "resp-1", RUNTIME, requestId.toString(),
                AgentBusEventType.INVOCATION_FAILED, "reason=internal_error"));
        GatewayRuntimeService gw = newGateway(consumer);

        IngressResponse resp = gw.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.REJECTED);
        assertThat(resp.rejectionReason()).isEqualTo("internal_error");
    }

    @Test
    void accept_window_stream_ready_returns_accepted_with_stream_ref_cursor() {
        FakeConsumer consumer = new FakeConsumer();
        UUID requestId = UUID.randomUUID();
        consumer.queue.add(inbound(TENANT, "resp-1", RUNTIME, requestId.toString(),
                AgentBusEventType.INVOCATION_ACCEPTED, "taskId=t-6"));
        consumer.queue.add(inbound(TENANT, "resp-2", RUNTIME, requestId.toString(),
                AgentBusEventType.INVOCATION_STREAM_READY, "taskId=t-6;streamRef=stream://t-6"));
        GatewayRuntimeService gw = newGateway(consumer);

        IngressResponse resp = gw.acceptWindow(requestId, TENANT);

        // SSE bridge (S2): STREAM_READY → accepted(cursor=streamRef)
        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        assertThat(resp.cursor()).isEqualTo("stream://t-6");
    }

    @Test
    void accept_window_accepted_then_response_uses_recorded_task_id_as_cursor() {
        FakeConsumer consumer = new FakeConsumer();
        UUID requestId = UUID.randomUUID();
        consumer.queue.add(inbound(TENANT, "resp-1", RUNTIME, requestId.toString(),
                AgentBusEventType.INVOCATION_ACCEPTED, "taskId=t-7"));
        // RESPONSE carries a snapshot payloadRef but no taskId token — cursor falls back to the recorded taskId
        consumer.queue.add(inbound(TENANT, "resp-2", RUNTIME, requestId.toString(),
                AgentBusEventType.INVOCATION_RESPONSE, "status=snapshot"));
        GatewayRuntimeService gw = newGateway(consumer);

        IngressResponse resp = gw.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        assertThat(resp.cursor()).isEqualTo("t-7");
    }

    // ===== classify (by NATIVE eventType — no descriptor decoding) =====

    @Test
    void classify_maps_each_event_type() {
        GatewayRuntimeService gw = newGateway(new FakeConsumer());
        assertThat(gw.classify(resp(AgentBusEventType.INVOCATION_RESPONSE, null)))
                .isEqualTo(InvocationResponseStatus.COMPLETED_RESPONSE);
        assertThat(gw.classify(resp(AgentBusEventType.A2A_CALL_RESPONSE, null)))
                .isEqualTo(InvocationResponseStatus.COMPLETED_RESPONSE);
        assertThat(gw.classify(resp(AgentBusEventType.INVOCATION_ACCEPTED, null)))
                .isEqualTo(InvocationResponseStatus.ACCEPTED_WITH_TASK);
        assertThat(gw.classify(resp(AgentBusEventType.A2A_CALL_ACCEPTED, null)))
                .isEqualTo(InvocationResponseStatus.ACCEPTED_WITH_TASK);
        assertThat(gw.classify(resp(AgentBusEventType.INVOCATION_STREAM_READY, null)))
                .isEqualTo(InvocationResponseStatus.STREAM_READY);
        assertThat(gw.classify(resp(AgentBusEventType.A2A_STREAM_READY, null)))
                .isEqualTo(InvocationResponseStatus.STREAM_READY);
        assertThat(gw.classify(resp(AgentBusEventType.INVOCATION_REJECTED, null)))
                .isEqualTo(InvocationResponseStatus.REJECTED);
        assertThat(gw.classify(resp(AgentBusEventType.A2A_CALL_REJECTED, null)))
                .isEqualTo(InvocationResponseStatus.REJECTED);
        assertThat(gw.classify(resp(AgentBusEventType.INVOCATION_FAILED, null)))
                .isEqualTo(InvocationResponseStatus.FAILED);
        assertThat(gw.classify(resp(AgentBusEventType.A2A_CALL_FAILED, null)))
                .isEqualTo(InvocationResponseStatus.FAILED);
    }

    @Test
    void classify_terminal_completed_vs_failed_by_status_token() {
        GatewayRuntimeService gw = newGateway(new FakeConsumer());
        assertThat(gw.classify(resp(AgentBusEventType.INVOCATION_TERMINAL, "status=completed")))
                .isEqualTo(InvocationResponseStatus.COMPLETED_RESPONSE);
        assertThat(gw.classify(resp(AgentBusEventType.INVOCATION_TERMINAL, "status=failed")))
                .isEqualTo(InvocationResponseStatus.FAILED);
        assertThat(gw.classify(resp(AgentBusEventType.A2A_CALL_TERMINAL, "status=completed")))
                .isEqualTo(InvocationResponseStatus.COMPLETED_RESPONSE);
        assertThat(gw.classify(resp(AgentBusEventType.A2A_CALL_TERMINAL, "status=cancelled")))
                .isEqualTo(InvocationResponseStatus.FAILED);
    }

    @Test
    void classify_null_event_type_returns_unknown() {
        GatewayRuntimeService gw = newGateway(new FakeConsumer());
        BrokerInboundMessage m = inbound(TENANT, "m-1", RUNTIME, "corr-1", null, null);
        assertThat(gw.classify(m)).isEqualTo(InvocationResponseStatus.UNKNOWN);
    }

    @Test
    void classify_request_event_type_as_response_returns_unknown() {
        GatewayRuntimeService gw = newGateway(new FakeConsumer());
        assertThat(gw.classify(resp(AgentBusEventType.CLIENT_INVOCATION_REQUESTED, null)))
                .isEqualTo(InvocationResponseStatus.UNKNOWN);
    }

    // ===== toIngressResponse =====

    @Test
    void to_ingress_response_maps_each_status() {
        GatewayRuntimeService gw = newGateway(new FakeConsumer());
        UUID id = UUID.randomUUID();

        assertThat(gw.toIngressResponse(id, InvocationResponseStatus.COMPLETED_RESPONSE, "t-1", null, null))
                .isEqualTo(IngressResponse.accepted(id, "t-1"));
        // COMPLETED_RESPONSE allows null cursor (no prior ACCEPTED, no taskId token)
        assertThat(gw.toIngressResponse(id, InvocationResponseStatus.COMPLETED_RESPONSE, null, null, null))
                .isEqualTo(IngressResponse.accepted(id, null));
        assertThat(gw.toIngressResponse(id, InvocationResponseStatus.ACCEPTED_WITH_TASK, "t-2", null, null))
                .isEqualTo(IngressResponse.accepted(id, "t-2"));
        assertThat(gw.toIngressResponse(id, InvocationResponseStatus.STREAM_READY, null, "stream://t", null))
                .isEqualTo(IngressResponse.accepted(id, "stream://t"));
        assertThat(gw.toIngressResponse(id, InvocationResponseStatus.REJECTED, null, null, "denied"))
                .isEqualTo(IngressResponse.rejected(id, "denied"));
        assertThat(gw.toIngressResponse(id, InvocationResponseStatus.FAILED, null, null, "boom"))
                .isEqualTo(IngressResponse.rejected(id, "boom"));
        assertThat(gw.toIngressResponse(id, InvocationResponseStatus.UNKNOWN, null, null, null))
                .isEqualTo(IngressResponse.deferred(id));
    }

    @Test
    void to_ingress_response_defaults_rejection_reason_when_blank() {
        GatewayRuntimeService gw = newGateway(new FakeConsumer());
        UUID id = UUID.randomUUID();
        IngressResponse resp = gw.toIngressResponse(id, InvocationResponseStatus.REJECTED, null, null, null);
        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.REJECTED);
        assertThat(resp.rejectionReason()).isNotBlank();
    }

    // ===== E2E with InMemoryBroker + InMemoryForwardingOutbox + TestAgentRuntime =====

    @Test
    void e2e_blocking_call_returns_completed_response() {
        // L2 §6.2.1: runtime BLOCKING → ACCEPTED + RESPONSE + TERMINAL → gateway COMPLETED_RESPONSE → accepted
        E2eFixture fx = newE2e();
        fx.runtime.setResponseMode(TestAgentRuntime.ResponseMode.BLOCKING);
        UUID requestId = UUID.randomUUID();
        IngressEnvelope req = ingress(requestId, IngressEnvelope.IngressRequestType.RUN_CREATE);

        fx.gateway.dispatchRequest(req);
        fx.runtime.pollAndProcess(2_000L);
        IngressResponse resp = fx.gateway.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        assertThat(resp.cursor()).startsWith("task-");
        assertThat(resp.rejectionReason()).isNull();
    }

    @Test
    void e2e_accepted_only_returns_accepted_with_task() {
        // L2 §6.2.2: runtime ACCEPTED_ONLY → ACCEPTED only → gateway ACCEPTED_WITH_TASK → accepted(cursor=taskId)
        E2eFixture fx = newE2e();
        fx.runtime.setResponseMode(TestAgentRuntime.ResponseMode.ACCEPTED_ONLY);
        UUID requestId = UUID.randomUUID();
        IngressEnvelope req = ingress(requestId, IngressEnvelope.IngressRequestType.RUN_CREATE);

        fx.gateway.dispatchRequest(req);
        fx.runtime.pollAndProcess(2_000L);
        IngressResponse resp = fx.gateway.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        assertThat(resp.cursor()).startsWith("task-");
    }

    @Test
    void e2e_silent_returns_deferred_unknown() {
        // L2 §6.2.3: runtime SILENT → creates task but emits nothing → gateway times out → UNKNOWN → deferred
        E2eFixture fx = newE2e();
        fx.runtime.setResponseMode(TestAgentRuntime.ResponseMode.SILENT);
        UUID requestId = UUID.randomUUID();
        IngressEnvelope req = ingress(requestId, IngressEnvelope.IngressRequestType.RUN_CREATE);

        fx.gateway.dispatchRequest(req);
        fx.runtime.pollAndProcess(2_000L);
        IngressResponse resp = fx.gateway.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.DEFERRED);
        assertThat(resp.cursor()).isNull();
        assertThat(resp.rejectionReason()).isNull();
    }

    @Test
    void e2e_streaming_returns_accepted_with_stream_ref_cursor() {
        // L2 §6.2.4: runtime STREAMING → ACCEPTED + STREAM_READY → gateway STREAM_READY → accepted(cursor=streamRef)
        E2eFixture fx = newE2e();
        fx.runtime.setResponseMode(TestAgentRuntime.ResponseMode.STREAMING);
        UUID requestId = UUID.randomUUID();
        IngressEnvelope req = ingress(requestId, IngressEnvelope.IngressRequestType.RUN_CREATE);

        fx.gateway.dispatchRequest(req);
        fx.runtime.pollAndProcess(2_000L);
        IngressResponse resp = fx.gateway.acceptWindow(requestId, TENANT);

        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        assertThat(resp.cursor()).startsWith("stream://");
    }

    @Test
    void route_client_request_dispatches_then_accepts_in_one_call() {
        // routeClientRequest = dispatchRequest + acceptWindow in sequence (unit test with a fake consumer).
        // The fake consumer is pre-loaded so the accept phase observes a matching RESPONSE.
        RecordingRelay relay = new RecordingRelay();
        FakeConsumer consumer = new FakeConsumer();
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        GatewayRuntimeService gw = newGateway(outbox, outbox, relay, consumer);
        UUID requestId = UUID.randomUUID();
        // the gateway's own request (self-consume) + a matching response
        consumer.queue.add(inbound(TENANT, "req-self", GATEWAY, requestId.toString(),
                AgentBusEventType.CLIENT_INVOCATION_REQUESTED, "eventType=CLIENT_INVOCATION_REQUESTED"));
        consumer.queue.add(inbound(TENANT, "resp-1", RUNTIME, requestId.toString(),
                AgentBusEventType.INVOCATION_RESPONSE, "taskId=t-8;status=snapshot"));

        IngressResponse resp = gw.routeClientRequest(ingress(requestId, IngressEnvelope.IngressRequestType.RUN_CREATE));

        // dispatch produced the request onto the relay
        assertThat(relay.produced).hasSize(1);
        // accept observed the response → COMPLETED_RESPONSE → accepted(cursor=taskId)
        assertThat(resp.status()).isEqualTo(IngressResponse.IngressStatus.ACCEPTED);
        assertThat(resp.cursor()).isEqualTo("t-8");
    }

    // ===== fixtures =====

    private GatewayRuntimeService newGateway(FakeConsumer consumer) {
        return newGateway(new InMemoryForwardingOutbox(), new InMemoryForwardingOutbox(),
                new RecordingRelay(), consumer);
    }

    private GatewayRuntimeService newGateway(InMemoryForwardingOutbox outbox,
                                            InMemoryForwardingOutbox claimOutbox,
                                            BrokerForwardingRelayPort relay,
                                            BrokerForwardingConsumerPort consumer) {
        return new GatewayRuntimeService(outbox, claimOutbox, relay, consumer,
                GATEWAY, ACCEPT_TIMEOUT_MS, RESPONSE_TIMEOUT_MS, () -> NOW);
    }

    private ForwardingEnvelope dispatch(IngressEnvelope.IngressRequestType type) {
        return dispatch(UUID.randomUUID(), UUID.randomUUID(), type);
    }

    private ForwardingEnvelope dispatch(UUID requestId, UUID idem,
                                       IngressEnvelope.IngressRequestType type) {
        GatewayRuntimeService gw = newGateway(new InMemoryForwardingOutbox(), new InMemoryForwardingOutbox(),
                new RecordingRelay(), new FakeConsumer());
        return gw.dispatchRequest(ingress(requestId, idem, type));
    }

    private static IngressEnvelope ingress(UUID requestId, IngressEnvelope.IngressRequestType type) {
        return ingress(requestId, UUID.randomUUID(), type);
    }

    private static IngressEnvelope ingress(UUID requestId, UUID idem,
                                           IngressEnvelope.IngressRequestType type) {
        return new IngressEnvelope(requestId, TENANT, idem, type, "payload", TRACE, null,
                Map.of("routeHandle", ROUTE, "targetServiceId", RUNTIME, "capability", "cap-1"));
    }

    private E2eFixture newE2e() {
        InMemoryBroker broker = new InMemoryBroker(
                r -> Optional.of("topic-" + r.tenantScope()));
        InMemoryForwardingOutbox gatewayOutbox = new InMemoryForwardingOutbox();
        InMemoryForwardingOutbox runtimeOutbox = new InMemoryForwardingOutbox();
        TestAgentRuntime runtime = new TestAgentRuntime(broker, runtimeOutbox, RUNTIME, TENANT);
        // the gateway's response consumer: a per-consumer double subscribed to receive responses
        // targeted at the gateway (targetServiceId == GATEWAY), within TENANT. The runtime's own
        // request consumer is subscribed inside TestAgentRuntime; both share the one broker.
        BrokerForwardingConsumerPort responseConsumer = broker.consumerFor(GATEWAY);
        responseConsumer.subscribe(GATEWAY, new ForwardingRouteHandle("gateway-" + GATEWAY, TENANT),
                DeliveryFilter.forRuntime(TENANT, GATEWAY));
        GatewayRuntimeService gateway = new GatewayRuntimeService(
                gatewayOutbox, gatewayOutbox, broker, responseConsumer,
                GATEWAY, ACCEPT_TIMEOUT_MS, RESPONSE_TIMEOUT_MS, () -> NOW);
        return new E2eFixture(broker, gatewayOutbox, runtimeOutbox, runtime, gateway);
    }

    private record E2eFixture(InMemoryBroker broker, InMemoryForwardingOutbox gatewayOutbox,
                              InMemoryForwardingOutbox runtimeOutbox, TestAgentRuntime runtime,
                              GatewayRuntimeService gateway) {}

    // ===== test doubles =====

    /** Records produced outbox records without a real broker; returns ACCEPTED. */
    static final class RecordingRelay implements BrokerForwardingRelayPort {
        final List<ForwardingOutboxRecord> produced = new ArrayList<>();
        @Override
        public BrokerProduceOutcome produce(ForwardingOutboxRecord record, long nowMillisEpoch) {
            Objects.requireNonNull(record, "record is required");
            produced.add(record);
            return BrokerProduceOutcome.accepted();
        }
    }

    /** Pre-loaded consumer that returns queued messages in order, then empty. */
    static final class FakeConsumer implements BrokerForwardingConsumerPort {
        final Deque<BrokerInboundMessage> queue = new ArrayDeque<>();
        @Override
        public void subscribe(String consumerServiceId, ForwardingRouteHandle route, DeliveryFilter filter) {
            // pre-loaded fake: the queue is hand-loaded per test; no real subscription / filtering.
        }
        @Override
        public Optional<BrokerInboundMessage> poll(long nowMillisEpoch) {
            return Optional.ofNullable(queue.poll());
        }
        @Override
        public void commit(BrokerInboundMessage message) {
            // no-op (messages are already removed from the queue at poll)
        }
        @Override
        public void reject(BrokerInboundMessage message, ForwardingFailureCode code) {
            // no-op
        }
        @Override
        public void close() {
            // no-op
        }
        @Override
        public boolean supportsBrokerSidePropertyFilter() {
            return false; // pre-loaded fake does no broker-side filtering
        }
    }

    // ===== BrokerInboundMessage builders =====

    private static BrokerInboundMessage resp(AgentBusEventType eventType, String payloadRef) {
        return inbound(TENANT, "m-" + UUID.randomUUID(), RUNTIME, "corr-1", eventType, payloadRef);
    }

    private static BrokerInboundMessage inbound(String tenantId, String messageId, String sourceServiceId,
                                                String correlationId, AgentBusEventType eventType,
                                                String payloadRef) {
        return new BrokerInboundMessage(tenantId, messageId, sourceServiceId, GATEWAY,
                GATEWAY, payloadRef, correlationId, eventType);
    }
}
