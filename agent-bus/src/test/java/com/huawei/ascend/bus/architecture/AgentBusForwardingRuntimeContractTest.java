package com.huawei.ascend.bus.architecture;

import com.huawei.ascend.bus.forwarding.runtime.EpochClock;
import com.huawei.ascend.bus.forwarding.runtime.ForwardingDispatchLoop;
import com.huawei.ascend.bus.forwarding.runtime.ForwardingDispatcherWorker;
import com.huawei.ascend.bus.forwarding.runtime.ForwardingRetryPolicy;
import com.huawei.ascend.bus.forwarding.runtime.ForwardingStateMachine;
import com.huawei.ascend.bus.forwarding.runtime.RouteCircuitBreaker;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryResult;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingInboxRecord;
import com.huawei.ascend.bus.forwarding.spi.ForwardingLease;
import com.huawei.ascend.bus.forwarding.spi.ForwardingLeaseException;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxClaimPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;
import com.huawei.ascend.bus.forwarding.spi.ForwardingReceipt;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;
import com.huawei.ascend.bus.forwarding.test.InMemoryForwardingDelivery;
import com.huawei.ascend.bus.forwarding.test.InMemoryForwardingDispatcher;
import com.huawei.ascend.bus.forwarding.test.InMemoryForwardingInbox;
import com.huawei.ascend.bus.forwarding.test.InMemoryForwardingOutbox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.huawei.ascend.bus.forwarding.runtime.ForwardingStateMachine.InboxEvent.ARRIVE_NEW;
import static com.huawei.ascend.bus.forwarding.runtime.ForwardingStateMachine.InboxEvent.CONSUME;
import static com.huawei.ascend.bus.forwarding.runtime.ForwardingStateMachine.OutboxEvent.ACK;
import static com.huawei.ascend.bus.forwarding.runtime.ForwardingStateMachine.OutboxEvent.BEGIN_DISPATCH;
import static com.huawei.ascend.bus.forwarding.runtime.ForwardingStateMachine.OutboxEvent.ENQUEUE;
import static com.huawei.ascend.bus.forwarding.runtime.ForwardingStateMachine.OutboxEvent.EXHAUST_RETRIES;
import static com.huawei.ascend.bus.forwarding.runtime.ForwardingStateMachine.OutboxEvent.RETRY;
import static com.huawei.ascend.bus.forwarding.spi.ForwardingStatus.Inbox.CONSUMED;
import static com.huawei.ascend.bus.forwarding.spi.ForwardingStatus.Inbox.DUPLICATE_SUPPRESSED;
import static com.huawei.ascend.bus.forwarding.spi.ForwardingStatus.Inbox.RECEIVED;
import static com.huawei.ascend.bus.forwarding.spi.ForwardingStatus.Inbox.REJECTED;
import static com.huawei.ascend.bus.forwarding.spi.ForwardingStatus.Outbox.ACKED;
import static com.huawei.ascend.bus.forwarding.spi.ForwardingStatus.Outbox.DISPATCHING;
import static com.huawei.ascend.bus.forwarding.spi.ForwardingStatus.Outbox.DLQ;
import static com.huawei.ascend.bus.forwarding.spi.ForwardingStatus.Outbox.PENDING;
import static com.huawei.ascend.bus.forwarding.spi.ForwardingStatus.Outbox.RETRY_SCHEDULED;
import static com.huawei.ascend.bus.forwarding.spi.ForwardingStatus.Outbox.EXPIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runtime contract harness for {@code ICD-Agent-Bus-Forwarding-Runtime}
 * (Stage 7, slice 5).
 *
 * <p>Pins the C3 outbox / inbox runtime contract: the record schema (required
 * fields, unique key, dedup key, forbidden fields), the status / failure-code
 * sets, and the state-machine transition tables. A future edit that silently
 * weakens any of these (dropping a required field, admitting a payload body,
 * renaming a wire failure code, breaking a legal transition) fails the build.
 *
 * <p>The seven {@code forwarding_runtime_*} {@code @Test} method names are
 * mirrored verbatim in the ICD's {@code Contract Tests (harness 镜像, 切片 5)}
 * row and the machine-readable schema's {@code contract_tests}, so a renamed
 * assertion surfaces as ICD / harness drift (same convention as Stage 4).
 *
 * <p>Authority: {@code docs/architecture/l0/05-contracts/human-readable/
 * ICD-agent-bus-forwarding-runtime.md}; Stage 7 plan §3 slice 5.
 */
class AgentBusForwardingRuntimeContractTest {

    private static final Path ICD = Path.of(
            "../docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md");
    private static final Path SCHEMA = Path.of(
            "../docs/architecture/l0/05-contracts/machine-readable/agent-bus-forwarding-runtime.v1.yaml");
    private static final Path PERSISTENCE = Path.of(
            "../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md");

    private static final long NOW = 1_700_000_000_000L;
    private static final String SOURCE_SERVICE = "svc-src";
    private static final String TARGET_SERVICE = "svc-tgt";
    private static final String LEASE_OWNER = "worker-1";
    private static final long LEASE_UNTIL = NOW + 30_000;

    private static String icdText;
    private static String schemaText;
    private static List<String> forwardingSources;

    @BeforeAll
    static void readSources() throws Exception {
        assertThat(ICD)
                .as("ICD-Agent-Bus-Forwarding-Runtime must be reachable from the agent-bus "
                  + "module basedir (surefire working directory)")
                .exists();
        icdText = Files.readString(ICD);
        assertThat(SCHEMA)
                .as("runtime machine-readable schema must be reachable")
                .exists();
        schemaText = Files.readString(SCHEMA);
        forwardingSources = readForwardingProductionSources();
    }

    // ===== 7 contract tests — method names mirror ICD Contract Tests verbatim =====

    @Test
    void forwarding_runtime_outbox_record_has_required_fields() {
        assertThat(icdText).contains("## outbox record 字段");
        assertThat(icdText)
                .as("every required outbox field documented in the ICD record table")
                .contains("`tenantId`", "`messageId`", "`sourceServiceId`",
                          "`targetServiceId`", "`routeHandle`", "`status`",
                          "`attemptCount`", "`createdAt`", "`updatedAt`");
        // structural (Stage 8, MI8-002): the Java record mirrors the ICD outbox fields
        assertThat(recordFieldNames(ForwardingOutboxRecord.class))
                .as("ForwardingOutboxRecord components mirror the ICD outbox record fields")
                .contains("tenantId", "messageId", "sourceServiceId", "targetServiceId",
                          "routeHandle", "status", "attemptCount",
                          "createdAtMillisEpoch", "updatedAtMillisEpoch");
    }

    @Test
    void forwarding_runtime_inbox_record_has_required_fields() {
        assertThat(icdText).contains("## inbox record 字段");
        assertThat(icdText)
                .as("every required inbox field documented in the ICD record table")
                .contains("`tenantId`", "`messageId`", "`consumerServiceId`",
                          "`status`", "`receivedAt`");
    }

    @Test
    void forwarding_runtime_outbox_unique_key_is_tenant_and_message_id() {
        assertThat(schemaText)
                .as("outbox unique key is (tenantId, messageId) per ICD / L2 §5")
                .contains("unique_key: [tenantId, messageId]");
        // behaviour: re-enqueue of the same (tenantId, messageId) is idempotent
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        ForwardingEnvelope env = envelope("msg-uk", "tenant-a");
        ForwardingReceipt first = outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        ForwardingReceipt second = outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        assertThat(first.accepted()).isTrue();
        assertThat(second.accepted()).isTrue();
        assertThat(outbox.entryCount())
                .as("duplicate (tenantId, messageId) must not create a second outbox record")
                .isEqualTo(1);
        assertThat(outbox.statusOf(env.messageId(), env.tenantId())).isEqualTo(PENDING);
    }

    @Test
    void forwarding_runtime_inbox_dedup_key_includes_consumer() {
        assertThat(schemaText)
                .as("inbox dedup key includes consumerServiceId per ICD / L2 §5")
                .contains("dedup_key: [tenantId, messageId, consumerServiceId]");
        InMemoryForwardingInbox inbox = new InMemoryForwardingInbox();
        ForwardingEnvelope env = envelope("msg-dk", "tenant-a");
        // distinct consumers each receive fresh — no cross-consumer dedup
        assertThat(inbox.receive(env, "consumer-1", NOW)).isEqualTo(RECEIVED);
        assertThat(inbox.receive(env, "consumer-2", NOW)).isEqualTo(RECEIVED);
        // same consumer re-receives the same message → duplicate suppressed
        assertThat(inbox.receive(env, "consumer-1", NOW)).isEqualTo(DUPLICATE_SUPPRESSED);
    }

    @Test
    void forwarding_runtime_forbids_payload_body_token_stream_task_state_endpoint() {
        assertThat(schemaText)
                .as("runtime schema lists every forbidden field")
                .contains("- payload_body", "- token_stream",
                          "- task_execution_state", "- physical_endpoint");
        // structural guard: the envelope + receipt records carry none of these fields
        assertThat(recordFieldNames(ForwardingEnvelope.class))
                .as("ForwardingEnvelope must not carry a payload body / token stream / "
                  + "task execution state / physical endpoint field (HD4 forbidden payload)")
                .doesNotContain("payloadBody", "payload_body", "tokenStream", "token_stream",
                                 "taskExecutionState", "task_execution_state",
                                 "physicalEndpoint", "physical_endpoint");
        assertThat(recordFieldNames(ForwardingReceipt.class))
                .doesNotContain("payloadBody", "payload_body", "tokenStream",
                                "taskExecutionState", "physicalEndpoint");
    }

    @Test
    void forwarding_runtime_status_values_match_l2_state_machine() {
        assertThat(EnumSet.allOf(ForwardingStatus.Outbox.class))
                .containsExactlyInAnyOrder(
                        PENDING, DISPATCHING, ACKED, RETRY_SCHEDULED, DLQ, EXPIRED);
        assertThat(EnumSet.allOf(ForwardingStatus.Inbox.class))
                .containsExactlyInAnyOrder(
                        RECEIVED, DUPLICATE_SUPPRESSED, CONSUMED, REJECTED);

        ForwardingStateMachine sm = new ForwardingStateMachine();
        // outbox happy path: new → PENDING → DISPATCHING → ACKED
        assertThat(sm.transitOutbox(null, ENQUEUE)).isEqualTo(PENDING);
        assertThat(sm.transitOutbox(PENDING, BEGIN_DISPATCH)).isEqualTo(DISPATCHING);
        assertThat(sm.transitOutbox(DISPATCHING, ACK)).isEqualTo(ACKED);
        // inbox happy path
        assertThat(sm.transitInbox(null, ARRIVE_NEW)).isEqualTo(RECEIVED);
        assertThat(sm.transitInbox(RECEIVED, CONSUME)).isEqualTo(CONSUMED);
        // terminal states reject further transitions
        assertThatThrownBy(() -> sm.transitOutbox(ACKED, BEGIN_DISPATCH))
                .isInstanceOf(ForwardingStateMachine.IllegalStateTransitionException.class);
        assertThatThrownBy(() -> sm.transitInbox(CONSUMED, CONSUME))
                .isInstanceOf(ForwardingStateMachine.IllegalStateTransitionException.class);
    }

    @Test
    void forwarding_runtime_failure_codes_cover_l2_semantics() {
        Set<String> wireCodes = Arrays.stream(ForwardingFailureCode.values())
                .map(ForwardingFailureCode::wireCode)
                .collect(Collectors.toSet());
        assertThat(wireCodes)
                .as("ForwardingFailureCode wire names mirror the ICD failure modes + payload_ref_invalid + remote_task_failed")
                .containsExactlyInAnyOrder(
                        "route_not_found", "tenant_mismatch", "delivery_timeout",
                        "receiver_unavailable", "backpressure_rejected",
                        "duplicate_suppressed", "payload_ref_invalid",
                        // Stage 18 (MI18-001): remote agent terminal business failure (A2A
                        // FAILED/CANCELED/REJECTED) → non-retryable → direct DLQ.
                        "remote_task_failed");
        // the schema classifies them (non-retryable / retryable / dedup)
        assertThat(schemaText).contains("non_retryable", "retryable", "dedup");
    }

    // ===== Stage 7 slice 5 behavioural scenarios =====

    @Test
    void envelope_construction_rejects_tenant_mismatch() {
        assertThatThrownBy(() -> new ForwardingEnvelope(
                new ForwardingMessageId("msg-tm"), "tenant-a", "trace-tm", "corr-tm", "idem-tm",
                new ForwardingRouteHandle("route-1", "tenant-other"), // different tenant scope
                "cap-tm", Long.MAX_VALUE,
                ForwardingEnvelope.PayloadPolicy.CONTROL_ONLY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant_mismatch");
    }

    @Test
    void envelope_construction_rejects_null_route_handle() {
        assertThatThrownBy(() -> new ForwardingEnvelope(
                new ForwardingMessageId("msg-nr"), "tenant-a", "trace-nr", "corr-nr", "idem-nr",
                null, // missing route handle
                "cap-nr", Long.MAX_VALUE,
                ForwardingEnvelope.PayloadPolicy.CONTROL_ONLY, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("routeHandle");
    }

    @Test
    void envelope_data_bearing_message_requires_payload_ref() {
        // CONTROL_ONLY with no payloadRef is fine
        ForwardingEnvelope control = envelope("msg-co", "tenant-a");
        assertThat(control.carriesPayloadRef()).isFalse();
        // DATA_BEARING with null payloadRef is rejected (MI5-003 option B)
        assertThatThrownBy(() -> new ForwardingEnvelope(
                new ForwardingMessageId("msg-db"), "tenant-a", "trace-db", "corr-db", "idem-db",
                new ForwardingRouteHandle("route-1", "tenant-a"), "cap-db", Long.MAX_VALUE,
                ForwardingEnvelope.PayloadPolicy.DATA_BEARING, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("payloadRef");
        // DATA_BEARING with a payloadRef is accepted
        ForwardingEnvelope data = new ForwardingEnvelope(
                new ForwardingMessageId("msg-db2"), "tenant-a", "trace-db2", "corr-db2", "idem-db2",
                new ForwardingRouteHandle("route-1", "tenant-a"), "cap-db2", Long.MAX_VALUE,
                ForwardingEnvelope.PayloadPolicy.DATA_BEARING, "ref://payload/123");
        assertThat(data.carriesPayloadRef()).isTrue();
    }

    @Test
    void outbox_retry_path_dispatching_to_retry_scheduled_back_to_dispatching() {
        ForwardingStateMachine sm = new ForwardingStateMachine();
        ForwardingStatus.Outbox s = sm.transitOutbox(null, ENQUEUE);
        s = sm.transitOutbox(s, BEGIN_DISPATCH);
        s = sm.transitOutbox(s, RETRY);
        assertThat(s).isEqualTo(RETRY_SCHEDULED);
        s = sm.transitOutbox(s, BEGIN_DISPATCH);
        assertThat(s).isEqualTo(DISPATCHING);
        assertThat(sm.transitOutbox(s, ACK)).isEqualTo(ACKED);
    }

    @Test
    void outbox_exhaust_retries_terminates_at_dlq() {
        ForwardingStateMachine sm = new ForwardingStateMachine();
        ForwardingStatus.Outbox s = sm.transitOutbox(null, ENQUEUE);
        s = sm.transitOutbox(s, BEGIN_DISPATCH);
        assertThat(sm.transitOutbox(s, EXHAUST_RETRIES)).isEqualTo(DLQ);
        // DLQ is terminal
        assertThatThrownBy(() -> sm.transitOutbox(DLQ, BEGIN_DISPATCH))
                .isInstanceOf(ForwardingStateMachine.IllegalStateTransitionException.class);
    }

    @Test
    void dispatcher_enqueue_and_drive_to_acked_via_ports() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        InMemoryForwardingDispatcher dispatcher = new InMemoryForwardingDispatcher(outbox);
        ForwardingEnvelope env = envelope("msg-flow", "tenant-a");

        ForwardingReceipt receipt = dispatcher.dispatch(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        assertThat(receipt.accepted()).isTrue();
        assertThat(receipt.failureCode()).isNull();
        assertThat(outbox.statusOf(env.messageId(), env.tenantId())).isEqualTo(PENDING);

        // DISPATCHING is entered only via claim / lease (Stage 9, MI9-001:
        // markDispatching was removed — claim is the sole path into DISPATCHING)
        assertThat(outbox.claimDue("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL)).hasSize(1);
        assertThat(outbox.statusOf(env.messageId(), env.tenantId())).isEqualTo(DISPATCHING);
        outbox.markAcked(env.messageId(), env.tenantId(), LEASE_OWNER);
        assertThat(outbox.statusOf(env.messageId(), env.tenantId())).isEqualTo(ACKED);
    }

    @Test
    void outbox_retry_increments_attempt_count() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        ForwardingEnvelope env = envelope("msg-retry", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        // DISPATCHING via claim, then lease-owner guarded RETRY (Stage 9, MI9-001)
        outbox.claimDue("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL);
        outbox.scheduleRetry(env.messageId(), env.tenantId(), LEASE_OWNER,
                ForwardingFailureCode.RECEIVER_UNAVAILABLE, NOW + 5_000);
        assertThat(outbox.statusOf(env.messageId(), env.tenantId())).isEqualTo(RETRY_SCHEDULED);
        assertThat(outbox.attemptCountOf(env.messageId(), env.tenantId()))
                .as("each RETRY increments attemptCount")
                .isEqualTo(1);
    }

    @Test
    void forwarding_production_sources_carry_no_payload_body_nor_task_state_nor_broker() {
        assertThat(forwardingSources)
                .as("forwarding production sources must be discovered")
                .isNotEmpty();
        assertThat(forwardingSources)
                .as("forwarding production code: no payload body field, no Task execution state, "
                  + "no concrete broker / MQ client (decision §6.2 — always forbidden). JDBC is "
                  + "now licensed for the persistence.jdbc adapter (Stage 12); its package-level "
                  + "confinement is enforced by AgentBusForwardingSpiPurityTest, not this scan. "
                  + "Stage 15 likewise excludes runtime.transport.a2a — that adapter parses the "
                  + "remote A2A wire format (Task / TaskStatus) to map a remote Task lifecycle onto "
                  + "ForwardingDeliveryResult; it never stores Task state on the outbox record.")
                .allSatisfy(src -> assertThat(src)
                        .doesNotContain("payloadBody", "payload_body")
                        .doesNotContain("TaskExecutionState", "TaskExecution", "TaskStatus")
                        .doesNotContain("org.apache.kafka", "com.rabbitmq",
                                        "org.apache.rocketmq", "io.nats.client"));
    }

    // ===== Stage 8 slice 6 — record projection, claim / lease, dispatcher worker =====

    @Test
    void forwarding_runtime_outbox_record_carries_source_and_target_service_id() {
        // structural: source/target live on the record, not the envelope (MI8-002)
        assertThat(recordFieldNames(ForwardingOutboxRecord.class))
                .as("ForwardingOutboxRecord carries sourceServiceId + targetServiceId")
                .contains("sourceServiceId", "targetServiceId");
        // behaviour: gateway-written source/target survive onto the record
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        ForwardingEnvelope env = envelope("msg-st", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        ForwardingOutboxRecord record = outbox.recordOf(env.messageId(), env.tenantId());
        assertThat(record.sourceServiceId()).isEqualTo(SOURCE_SERVICE);
        assertThat(record.targetServiceId()).isEqualTo(TARGET_SERVICE);
        assertThat(record.status()).isEqualTo(PENDING);
        assertThat(record.attemptCount()).isZero();
        assertThat(record.lease()).isNull();
    }

    @Test
    void claim_due_returns_only_tenant_scoped_due_non_terminal_records() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        ForwardingOutboxClaimPort claim = outbox;
        ForwardingEnvelope envA = envelope("msg-cl-a", "tenant-a");
        ForwardingEnvelope envB = envelope("msg-cl-b", "tenant-b");
        outbox.enqueue(envA, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        outbox.enqueue(envB, SOURCE_SERVICE, TARGET_SERVICE, NOW);

        List<ForwardingOutboxRecord> claimed =
                claim.claimDue("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL);

        assertThat(claimed).hasSize(1);
        ForwardingOutboxRecord r = claimed.get(0);
        assertThat(r.messageId().value()).isEqualTo("msg-cl-a");
        assertThat(r.status()).isEqualTo(DISPATCHING);
        assertThat(r.lease()).isNotNull();
        assertThat(r.lease().leaseOwner()).isEqualTo(LEASE_OWNER);
        // tenant-b record untouched (tenant scope, Rule R-C.c)
        assertThat(outbox.statusOf(envB.messageId(), "tenant-b")).isEqualTo(PENDING);
    }

    @Test
    void claim_due_grants_exclusive_lease_one_owner_at_a_time() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        ForwardingOutboxClaimPort claim = outbox;
        ForwardingEnvelope env = envelope("msg-excl", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);

        List<ForwardingOutboxRecord> first =
                claim.claimDue("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL);
        assertThat(first).hasSize(1);
        // second owner at the same instant: record is DISPATCHING under a live lease
        List<ForwardingOutboxRecord> second =
                claim.claimDue("tenant-a", NOW, 10, "worker-2", LEASE_UNTIL);
        assertThat(second)
                .as("a record under a live lease cannot be claimed by another owner")
                .isEmpty();
    }

    @Test
    void expired_lease_can_be_reclaimed() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        ForwardingOutboxClaimPort claim = outbox;
        ForwardingEnvelope env = envelope("msg-reclaim", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        // first owner claims with a short lease
        claim.claimDue("tenant-a", NOW, 10, LEASE_OWNER, NOW + 1_000);
        // after the lease expires, a second owner reclaims the stuck DISPATCHING record
        long later = NOW + 5_000;
        List<ForwardingOutboxRecord> reclaimed =
                claim.claimDue("tenant-a", later, 10, "worker-2", later + 30_000);
        assertThat(reclaimed)
                .as("an expired lease on a stuck DISPATCHING record can be reclaimed")
                .hasSize(1);
        assertThat(reclaimed.get(0).lease().leaseOwner()).isEqualTo("worker-2");
    }

    @Test
    void terminal_outbox_record_is_not_claimable() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        ForwardingOutboxClaimPort claim = outbox;
        ForwardingEnvelope env = envelope("msg-term", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        // drive to ACKED via claim + lease-owner guarded ack (Stage 9, MI9-001)
        outbox.claimDue("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL);
        outbox.markAcked(env.messageId(), env.tenantId(), LEASE_OWNER);
        assertThat(outbox.statusOf(env.messageId(), env.tenantId())).isEqualTo(ACKED);
        assertThat(claim.claimDue("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL))
                .as("a terminal record is never claimable")
                .isEmpty();
    }

    @Test
    void dispatcher_worker_routes_ack_retry_dlq_expired_via_fake_delivery() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        InMemoryForwardingDelivery delivery = new InMemoryForwardingDelivery();
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(outbox, outbox, delivery);

        // ACK path
        ForwardingEnvelope ack = envelope("msg-w-ack", "tenant-a");
        outbox.enqueue(ack, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        delivery.put("msg-w-ack", ForwardingDeliveryResult.acked());
        ForwardingDispatcherWorker.DispatchTickResult tick =
                worker.runOnce("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL);
        assertThat(tick.claimed()).isEqualTo(1);
        assertThat(tick.acked()).isEqualTo(1);
        assertThat(outbox.statusOf(ack.messageId(), "tenant-a")).isEqualTo(ACKED);

        // RETRY path — increments attemptCount, sets nextAttemptAt + lastFailureCode
        ForwardingEnvelope retr = envelope("msg-w-retry", "tenant-a");
        outbox.enqueue(retr, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        delivery.put("msg-w-retry",
                ForwardingDeliveryResult.retry(ForwardingFailureCode.RECEIVER_UNAVAILABLE));
        worker.runOnce("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL);
        assertThat(outbox.statusOf(retr.messageId(), "tenant-a")).isEqualTo(RETRY_SCHEDULED);
        assertThat(outbox.attemptCountOf(retr.messageId(), "tenant-a"))
                .as("each RETRY increments attemptCount")
                .isEqualTo(1);
        assertThat(outbox.recordOf(retr.messageId(), "tenant-a").lastFailureCode())
                .isEqualTo(ForwardingFailureCode.RECEIVER_UNAVAILABLE);

        // DLQ path (non-retryable failure)
        ForwardingEnvelope dlq = envelope("msg-w-dlq", "tenant-a");
        outbox.enqueue(dlq, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        delivery.put("msg-w-dlq", ForwardingDeliveryResult.dlq(ForwardingFailureCode.ROUTE_NOT_FOUND));
        worker.runOnce("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL);
        assertThat(outbox.statusOf(dlq.messageId(), "tenant-a")).isEqualTo(DLQ);

        // EXPIRED path (deadline exceeded)
        ForwardingEnvelope exp = envelope("msg-w-exp", "tenant-a");
        outbox.enqueue(exp, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        delivery.put("msg-w-exp", ForwardingDeliveryResult.expired());
        worker.runOnce("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL);
        assertThat(outbox.statusOf(exp.messageId(), "tenant-a")).isEqualTo(EXPIRED);
    }

    /**
     * Slice 1 (MI10-001): a record reclaimed by another worker mid-tick (between
     * claim and ack) makes the worker's ack trip the lease guard with
     * {@code OWNER_MISMATCH}. The worker treats that as "skip this record" —
     * counted as {@code skipped} — and the tick keeps going, never aborting on a
     * single reclaimed record.
     */
    @Test
    void worker_skips_record_when_lease_reclaimed_mid_tick() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        ForwardingEnvelope env = envelope("msg-skip", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);

        // mid-delivery reclaim: worker-2 claims the record worker-1 is dispatching
        // at a later instant where worker-1's short lease has expired, so worker-1's
        // subsequent markAcked trips OWNER_MISMATCH.
        ForwardingDeliveryPort reclaimingDelivery = new ForwardingDeliveryPort() {
            @Override
            public ForwardingDeliveryResult deliver(ForwardingOutboxRecord record, long nowMillisEpoch) {
                outbox.claimDue("tenant-a", nowMillisEpoch + 1_000, 10, "worker-2",
                        nowMillisEpoch + 1_000 + 30_000);
                return ForwardingDeliveryResult.acked();
            }
        };
        ForwardingDispatcherWorker worker =
                new ForwardingDispatcherWorker(outbox, outbox, reclaimingDelivery);

        // worker-1 claims with a short lease (NOW + 500), then runs the tick
        ForwardingDispatcherWorker.DispatchTickResult tick =
                worker.runOnce("tenant-a", NOW, 10, "worker-1", NOW + 500);

        assertThat(tick.claimed()).isEqualTo(1);
        assertThat(tick.acked())
                .as("the reclaimed record is not acked by the stale worker")
                .isZero();
        assertThat(tick.skipped())
                .as("the reclaimed record is counted as skipped, not an aborted tick")
                .isEqualTo(1);
        // the record survives, still DISPATCHING, now owned by worker-2
        assertThat(outbox.statusOf(env.messageId(), "tenant-a")).isEqualTo(DISPATCHING);
    }

    /**
     * Slice 2 (MI10-002; Stage 11 MI11-001 clock): when the remaining lease TTL —
     * read against the injected {@code EpochClock} — drops below the policy
     * threshold, the worker renews before delivery so a long {@code deliver} does
     * not outlive the lease. Stage 11 drives the check from a clock advanced near
     * the lease expiry (not from a near-expiry caller leaseUntil), so renewal
     * fires on real elapsed time — the path a natural dispatch loop can trigger
     * once a real deliver takes time. The renewed {@code leaseUntil} is
     * observable at delivery time.
     */
    @Test
    void worker_renews_short_lease_before_delivery() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        ForwardingEnvelope env = envelope("msg-renew", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);

        long[] observedLeaseUntil = {0};
        ForwardingDeliveryPort observingDelivery = new ForwardingDeliveryPort() {
            @Override
            public ForwardingDeliveryResult deliver(ForwardingOutboxRecord record, long nowMillisEpoch) {
                observedLeaseUntil[0] = outbox.recordOf(record.messageId(), "tenant-a")
                        .lease().leaseUntilMillisEpoch();
                return ForwardingDeliveryResult.acked();
            }
        };
        // Stage 11 (MI11-001): inject a clock advanced to near the lease expiry, so
        // renewal fires on real elapsed time rather than a near-expiry caller leaseUntil.
        // leaseUntil = NOW + 30000; clock at NOW + 29500 -> remaining 500 < 1000 -> renew
        long[] clockNow = {NOW + 29_500};
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                outbox, outbox, observingDelivery,
                new ForwardingDispatcherWorker.DispatchLeasePolicy(1_000, 30_000),
                () -> clockNow[0]);

        // worker-1 claims at NOW (leaseUntil NOW + 30000); renewal extends to NOW + 60000
        ForwardingDispatcherWorker.DispatchTickResult tick =
                worker.runOnce("tenant-a", NOW, 10, "worker-1", NOW + 30_000);

        assertThat(tick.claimed()).isEqualTo(1);
        assertThat(tick.acked()).isEqualTo(1);
        assertThat(observedLeaseUntil[0])
                .as("the near-expiry lease was renewed before delivery (NOW+30000 extended by 30000)")
                .isEqualTo(NOW + 30_000 + 30_000);
        assertThat(outbox.statusOf(env.messageId(), "tenant-a")).isEqualTo(ACKED);
    }

    /**
     * Slice 2 (MI10-002; Stage 11 MI11-001 clock): a lease whose remaining TTL —
     * read against the injected clock — is already above the policy threshold is
     * not renewed; the worker keeps the caller's leaseUntil.
     */
    @Test
    void worker_does_not_renew_when_lease_sufficient() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        ForwardingEnvelope env = envelope("msg-norenew", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);

        long[] observedLeaseUntil = {0};
        ForwardingDeliveryPort observingDelivery = new ForwardingDeliveryPort() {
            @Override
            public ForwardingDeliveryResult deliver(ForwardingOutboxRecord record, long nowMillisEpoch) {
                observedLeaseUntil[0] = outbox.recordOf(record.messageId(), "tenant-a")
                        .lease().leaseUntilMillisEpoch();
                return ForwardingDeliveryResult.acked();
            }
        };
        // Stage 11 (MI11-001): clock at NOW + 1000; leaseUntil = NOW + 30000 -> remaining
        // 29000 >= 1000 -> no renew (leaseUntil stays NOW + 30000)
        long[] clockNow = {NOW + 1_000};
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                outbox, outbox, observingDelivery,
                new ForwardingDispatcherWorker.DispatchLeasePolicy(1_000, 30_000),
                () -> clockNow[0]);

        ForwardingDispatcherWorker.DispatchTickResult tick =
                worker.runOnce("tenant-a", NOW, 10, "worker-1", NOW + 30_000);

        assertThat(tick.acked()).isEqualTo(1);
        assertThat(observedLeaseUntil[0])
                .as("a sufficient lease is not renewed: leaseUntil stays NOW+30000")
                .isEqualTo(NOW + 30_000);
    }

    /**
     * Slice 2 (MI10-002; Stage 11 MI11-001 clock): if {@code renewLease} returns
     * {@code false} (the lease was reclaimed / is no longer held between claim and
     * the renewal attempt), the worker skips the record — like a
     * {@link ForwardingLeaseException} — and never delivers it.
     */
    @Test
    void worker_skips_when_lease_renew_fails() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        ForwardingEnvelope env = envelope("msg-renewfail", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);

        InMemoryForwardingDelivery delivery = new InMemoryForwardingDelivery();
        delivery.put("msg-renewfail", ForwardingDeliveryResult.acked());
        // claimPort whose renewLease always fails — simulates the lease reclaimed by
        // another owner between claim and the renewal attempt
        ForwardingOutboxClaimPort failingRenew = new ForwardingOutboxClaimPort() {
            @Override
            public List<ForwardingOutboxRecord> claimDue(String tenantId, long nowMillisEpoch,
                                                          int limit, String leaseOwner,
                                                          long leaseUntilMillisEpoch) {
                return outbox.claimDue(tenantId, nowMillisEpoch, limit, leaseOwner, leaseUntilMillisEpoch);
            }
            @Override
            public boolean renewLease(ForwardingMessageId id, String tenantId, String leaseOwner,
                                      long leaseUntilMillisEpoch) {
                return false;
            }
            @Override
            public boolean releaseLease(ForwardingMessageId id, String tenantId, String leaseOwner) {
                return outbox.releaseLease(id, tenantId, leaseOwner);
            }
        };
        // Stage 11 (MI11-001): clock advanced near the lease expiry triggers the
        // renewal, which fails -> skip, never deliver
        long[] clockNow = {NOW + 29_500};
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                failingRenew, outbox, delivery,
                new ForwardingDispatcherWorker.DispatchLeasePolicy(1_000, 30_000),
                () -> clockNow[0]);

        ForwardingDispatcherWorker.DispatchTickResult tick =
                worker.runOnce("tenant-a", NOW, 10, "worker-1", NOW + 30_000);

        assertThat(tick.claimed()).isEqualTo(1);
        assertThat(tick.acked())
                .as("delivery is skipped when the renewal fails")
                .isZero();
        assertThat(tick.skipped()).isEqualTo(1);
        assertThat(outbox.statusOf(env.messageId(), "tenant-a"))
                .as("the record is left DISPATCHING for its true owner / next reclaim")
                .isEqualTo(DISPATCHING);
    }

    /**
     * Slice 3 (MI10-004): the dispatch loop drives {@code runOnce} ticks purely
     * from an injected {@link ForwardingDispatchLoop.TickSource} — no clock, no
     * scheduler, no thread. It runs ticks until the source stops, aggregates the
     * counts (self-consistent, like a single tick), and triggers the
     * {@link ForwardingDispatchLoop.IdleStrategy} on a tick that claims nothing.
     */
    @Test
    void dispatch_loop_drives_ticks_from_injected_source_until_it_stops() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        InMemoryForwardingDelivery delivery = new InMemoryForwardingDelivery();
        ForwardingEnvelope env1 = envelope("msg-loop-1", "tenant-a");
        ForwardingEnvelope env2 = envelope("msg-loop-2", "tenant-a");
        outbox.enqueue(env1, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        outbox.enqueue(env2, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        delivery.put("msg-loop-1", ForwardingDeliveryResult.acked());
        delivery.put("msg-loop-2", ForwardingDeliveryResult.acked());
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(outbox, outbox, delivery);

        // three instants then stop; limit 1 per tick -> 2 productive ticks + 1 idle tick
        long[] instants = {NOW, NOW + 1_000, NOW + 2_000};
        int[] cursor = {0};
        int[] idleHits = {0};
        ForwardingDispatchLoop.TickSource source = () ->
                cursor[0] >= instants.length
                        ? OptionalLong.empty()
                        : OptionalLong.of(instants[cursor[0]++]);
        ForwardingDispatchLoop.IdleStrategy idle = tick -> idleHits[0]++;

        ForwardingDispatchLoop loop = new ForwardingDispatchLoop(worker, source, idle);
        ForwardingDispatcherWorker.DispatchTickResult agg =
                loop.run("tenant-a", 1, LEASE_OWNER, 30_000);

        assertThat(agg.claimed())
                .as("two records claimed across the two productive ticks")
                .isEqualTo(2);
        assertThat(agg.acked()).isEqualTo(2);
        assertThat(agg.skipped()).isZero();
        assertThat(idleHits[0])
                .as("the third tick (no due records) triggered the idle strategy exactly once")
                .isEqualTo(1);
        assertThat(outbox.statusOf(env1.messageId(), "tenant-a")).isEqualTo(ACKED);
        assertThat(outbox.statusOf(env2.messageId(), "tenant-a")).isEqualTo(ACKED);
    }

    @Test
    void forwarding_persistence_does_not_write_task_state_nor_introduce_broker() {
        // re-assert the production-source purity boundary for the Stage 8 additions
        assertThat(forwardingSources)
                .as("forwarding production code stays free of Task state and concrete broker / "
                  + "MQ client (decision §6.2 — always forbidden). JDBC is licensed only for the "
                  + "persistence.jdbc adapter (Stage 12); package-level confinement is enforced "
                  + "by AgentBusForwardingSpiPurityTest. Stage 15 excludes runtime.transport.a2a "
                  + "(A2A wire-format parser; never stores Task state on the record).")
                .allSatisfy(src -> assertThat(src)
                        .doesNotContain("TaskExecutionState", "TaskExecution", "TaskStatus")
                        .doesNotContain("org.apache.kafka", "com.rabbitmq",
                                        "org.apache.rocketmq", "io.nats.client"));
    }

    // ===== Stage 9 — lease-safe mutation, lease lifecycle, record invariants, failure classification =====

    /**
     * Slice 1 (MI9-001): a worker whose lease expired and was reclaimed by
     * another worker cannot mutate the record — the guard trips as
     * {@code OWNER_MISMATCH}, while the new holder can. This is the canonical
     * stale-worker race a real JDBC CAS
     * ({@code WHERE lease_owner = ? AND lease_until > now()}) prevents.
     */
    @Test
    void stale_worker_acks_after_lease_reclaimed_by_another_owner_fails() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        ForwardingEnvelope env = envelope("msg-stale", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);

        // worker-1 claims with a short lease
        outbox.claimDue("tenant-a", NOW, 10, "worker-1", NOW + 1_000);
        // lease expires; worker-2 reclaims the stuck DISPATCHING record
        long later = NOW + 5_000;
        outbox.claimDue("tenant-a", later, 10, "worker-2", later + 30_000);

        // worker-1 (stale) tries to ACK → rejected, not the current holder
        assertThatThrownBy(() -> outbox.markAcked(env.messageId(), "tenant-a", "worker-1"))
                .isInstanceOf(ForwardingLeaseException.class)
                .hasFieldOrPropertyWithValue("reason",
                        ForwardingLeaseException.Reason.OWNER_MISMATCH);
        // record is still DISPATCHING, owned by worker-2
        assertThat(outbox.statusOf(env.messageId(), "tenant-a")).isEqualTo(DISPATCHING);
        // the current holder can still drive it to terminal
        assertThat(outbox.markAcked(env.messageId(), "tenant-a", "worker-2")).isEqualTo(ACKED);
    }

    /**
     * Slice 1 (MI9-001): the lease-owner guard distinguishes its other failure
     * modes — an unknown record and an unclaimed (PENDING) record.
     */
    @Test
    void lease_guarded_mutation_reports_record_not_found_and_no_lease() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        // RECORD_NOT_FOUND: unknown message
        assertThatThrownBy(() -> outbox.markAcked(
                new ForwardingMessageId("msg-missing"), "tenant-a", LEASE_OWNER))
                .isInstanceOf(ForwardingLeaseException.class)
                .hasFieldOrPropertyWithValue("reason",
                        ForwardingLeaseException.Reason.RECORD_NOT_FOUND);
        // NO_LEASE: a PENDING record was never claimed
        ForwardingEnvelope env = envelope("msg-nolease", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        assertThatThrownBy(() -> outbox.markAcked(env.messageId(), "tenant-a", LEASE_OWNER))
                .isInstanceOf(ForwardingLeaseException.class)
                .hasFieldOrPropertyWithValue("reason",
                        ForwardingLeaseException.Reason.NO_LEASE);
    }

    /**
     * Slice 2 (MI9-002): terminal + retry states clear the lease; only an
     * active DISPATCHING record holds one.
     */
    @Test
    void terminal_and_retry_states_clear_lease_only_dispatching_holds_it() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();

        // ACKED clears the lease
        ForwardingEnvelope ack = envelope("msg-lc-ack", "tenant-a");
        outbox.enqueue(ack, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        outbox.claimDue("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL);
        outbox.markAcked(ack.messageId(), "tenant-a", LEASE_OWNER);
        assertThat(outbox.recordOf(ack.messageId(), "tenant-a").lease())
                .as("ACKED record holds no lease (MI9-002)").isNull();

        // DLQ clears the lease
        ForwardingEnvelope dlq = envelope("msg-lc-dlq", "tenant-a");
        outbox.enqueue(dlq, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        outbox.claimDue("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL);
        outbox.moveToDlq(dlq.messageId(), "tenant-a", LEASE_OWNER,
                ForwardingFailureCode.ROUTE_NOT_FOUND);
        assertThat(outbox.recordOf(dlq.messageId(), "tenant-a").lease())
                .as("DLQ record holds no lease (MI9-002)").isNull();

        // EXPIRED clears the lease
        ForwardingEnvelope exp = envelope("msg-lc-exp", "tenant-a");
        outbox.enqueue(exp, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        outbox.claimDue("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL);
        outbox.markExpired(exp.messageId(), "tenant-a", LEASE_OWNER);
        assertThat(outbox.recordOf(exp.messageId(), "tenant-a").lease())
                .as("EXPIRED record holds no lease (MI9-002)").isNull();

        // RETRY_SCHEDULED clears the lease
        ForwardingEnvelope retr = envelope("msg-lc-retry", "tenant-a");
        outbox.enqueue(retr, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        outbox.claimDue("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL);
        outbox.scheduleRetry(retr.messageId(), "tenant-a", LEASE_OWNER,
                ForwardingFailureCode.RECEIVER_UNAVAILABLE, NOW + 5_000);
        assertThat(outbox.recordOf(retr.messageId(), "tenant-a").lease())
                .as("RETRY_SCHEDULED record holds no lease (MI9-002)").isNull();

        // active DISPATCHING holds a live lease
        ForwardingEnvelope disp = envelope("msg-lc-disp", "tenant-a");
        outbox.enqueue(disp, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        outbox.claimDue("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL);
        ForwardingLease lease = outbox.recordOf(disp.messageId(), "tenant-a").lease();
        assertThat(lease).as("DISPATCHING record holds a live lease (MI9-002)").isNotNull();
        assertThat(lease.leaseOwner()).isEqualTo(LEASE_OWNER);
        assertThat(lease.isExpiredAt(NOW)).isFalse();
    }

    /**
     * Slice 3 (MI9-003): the outbox record compact constructor rejects a record
     * that violates a per-status condition-field invariant.
     */
    @Test
    void outbox_record_rejects_invalid_status_invariants() {
        ForwardingMessageId id = new ForwardingMessageId("msg-bad");
        ForwardingRouteHandle route = new ForwardingRouteHandle("route-1", "tenant-a");

        // RETRY_SCHEDULED without nextAttemptAt
        assertThatThrownBy(() -> new ForwardingOutboxRecord("tenant-a", id, SOURCE_SERVICE,
                TARGET_SERVICE, route, null, RETRY_SCHEDULED, 0, 0L, NOW, NOW,
                ForwardingFailureCode.RECEIVER_UNAVAILABLE, null))
                .isInstanceOf(IllegalArgumentException.class);
        // RETRY_SCHEDULED with a non-retryable code
        assertThatThrownBy(() -> new ForwardingOutboxRecord("tenant-a", id, SOURCE_SERVICE,
                TARGET_SERVICE, route, null, RETRY_SCHEDULED, 0, NOW + 5_000, NOW, NOW,
                ForwardingFailureCode.ROUTE_NOT_FOUND, null))
                .isInstanceOf(IllegalArgumentException.class);
        // ACKED must not carry a lastFailureCode
        assertThatThrownBy(() -> new ForwardingOutboxRecord("tenant-a", id, SOURCE_SERVICE,
                TARGET_SERVICE, route, null, ACKED, 0, 0L, NOW, NOW,
                ForwardingFailureCode.ROUTE_NOT_FOUND, null))
                .isInstanceOf(IllegalArgumentException.class);
        // DLQ requires a lastFailureCode
        assertThatThrownBy(() -> new ForwardingOutboxRecord("tenant-a", id, SOURCE_SERVICE,
                TARGET_SERVICE, route, null, DLQ, 0, 0L, NOW, NOW, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // DISPATCHING requires a non-null lease
        assertThatThrownBy(() -> new ForwardingOutboxRecord("tenant-a", id, SOURCE_SERVICE,
                TARGET_SERVICE, route, null, DISPATCHING, 0, 0L, NOW, NOW, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // terminal ACKED must not hold a lease
        assertThatThrownBy(() -> new ForwardingOutboxRecord("tenant-a", id, SOURCE_SERVICE,
                TARGET_SERVICE, route, null, ACKED, 0, 0L, NOW, NOW, null,
                new ForwardingLease(LEASE_OWNER, LEASE_UNTIL)))
                .isInstanceOf(IllegalArgumentException.class);
        // tenant mismatch: tenantId != routeHandle.tenantScope
        assertThatThrownBy(() -> new ForwardingOutboxRecord("tenant-other", id, SOURCE_SERVICE,
                TARGET_SERVICE, route, null, PENDING, 0, 0L, NOW, NOW, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant_mismatch");
        // negative attemptCount
        assertThatThrownBy(() -> new ForwardingOutboxRecord("tenant-a", id, SOURCE_SERVICE,
                TARGET_SERVICE, route, null, PENDING, -1, 0L, NOW, NOW, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Slice 3 (MI9-003): the inbox record compact constructor rejects a record
     * that violates a per-status condition-field invariant.
     */
    @Test
    void inbox_record_rejects_invalid_status_invariants() {
        ForwardingMessageId id = new ForwardingMessageId("msg-inbox-bad");
        // CONSUMED requires consumedAt > 0
        assertThatThrownBy(() -> new ForwardingInboxRecord("tenant-a", id, "consumer-1",
                CONSUMED, NOW, 0L, null))
                .isInstanceOf(IllegalArgumentException.class);
        // CONSUMED must not carry a failureCode
        assertThatThrownBy(() -> new ForwardingInboxRecord("tenant-a", id, "consumer-1",
                CONSUMED, NOW, NOW, ForwardingFailureCode.PAYLOAD_REF_INVALID))
                .isInstanceOf(IllegalArgumentException.class);
        // RECEIVED must not carry a failureCode
        assertThatThrownBy(() -> new ForwardingInboxRecord("tenant-a", id, "consumer-1",
                RECEIVED, NOW, 0L, ForwardingFailureCode.TENANT_MISMATCH))
                .isInstanceOf(IllegalArgumentException.class);
        // REJECTED requires a failureCode
        assertThatThrownBy(() -> new ForwardingInboxRecord("tenant-a", id, "consumer-1",
                REJECTED, NOW, 0L, null))
                .isInstanceOf(IllegalArgumentException.class);
        // DUPLICATE_SUPPRESSED requires the DUPLICATE_SUPPRESSED failureCode
        assertThatThrownBy(() -> new ForwardingInboxRecord("tenant-a", id, "consumer-1",
                DUPLICATE_SUPPRESSED, NOW, 0L, ForwardingFailureCode.TENANT_MISMATCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Slice 4 (MI9-004): the failure-code classification drives RETRY / DLQ
     * routing at construction time.
     */
    @Test
    void failure_code_classification_drives_retry_and_dlq_routing() {
        // classification
        assertThat(ForwardingFailureCode.DELIVERY_TIMEOUT.retryable()).isTrue();
        assertThat(ForwardingFailureCode.RECEIVER_UNAVAILABLE.retryable()).isTrue();
        assertThat(ForwardingFailureCode.BACKPRESSURE_REJECTED.retryable()).isTrue();
        assertThat(ForwardingFailureCode.ROUTE_NOT_FOUND.nonRetryable()).isTrue();
        assertThat(ForwardingFailureCode.TENANT_MISMATCH.nonRetryable()).isTrue();
        assertThat(ForwardingFailureCode.PAYLOAD_REF_INVALID.nonRetryable()).isTrue();
        // Stage 18 (MI18-001): a remote agent's terminal business failure (A2A
        // FAILED/CANCELED/REJECTED) is non-retryable — distinct from infra-layer
        // retryable failures; routes straight to DLQ, not retried.
        assertThat(ForwardingFailureCode.REMOTE_TASK_FAILED.nonRetryable()).isTrue();
        assertThat(ForwardingFailureCode.REMOTE_TASK_FAILED.retryable()).isFalse();
        assertThat(ForwardingFailureCode.REMOTE_TASK_FAILED.wireCode()).isEqualTo("remote_task_failed");
        assertThat(ForwardingFailureCode.DUPLICATE_SUPPRESSED.dedup()).isTrue();

        // retry(...) rejects a non-retryable code
        assertThatThrownBy(() -> ForwardingDeliveryResult.retry(
                ForwardingFailureCode.ROUTE_NOT_FOUND))
                .isInstanceOf(IllegalArgumentException.class);
        // retry(...) rejects the dedup outcome
        assertThatThrownBy(() -> ForwardingDeliveryResult.retry(
                ForwardingFailureCode.DUPLICATE_SUPPRESSED))
                .isInstanceOf(IllegalArgumentException.class);
        // dlq(...) rejects the dedup outcome
        assertThatThrownBy(() -> ForwardingDeliveryResult.dlq(
                ForwardingFailureCode.DUPLICATE_SUPPRESSED))
                .isInstanceOf(IllegalArgumentException.class);
        // dlq(...) accepts a retryable code whose retries are exhausted
        assertThat(ForwardingDeliveryResult.dlq(ForwardingFailureCode.DELIVERY_TIMEOUT).outcome())
                .isEqualTo(ForwardingDeliveryResult.Outcome.DLQ);
        // Stage 18: dlq(...) accepts REMOTE_TASK_FAILED (non-retryable) — a remote
        // agent's terminal business failure routes straight to DLQ.
        assertThat(ForwardingDeliveryResult.dlq(ForwardingFailureCode.REMOTE_TASK_FAILED).outcome())
                .isEqualTo(ForwardingDeliveryResult.Outcome.DLQ);
        // Stage 18: retry(...) rejects REMOTE_TASK_FAILED (non-retryable, not transient infra).
        assertThatThrownBy(() -> ForwardingDeliveryResult.retry(
                ForwardingFailureCode.REMOTE_TASK_FAILED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Slice 6 (MI9-006): the persistence DDL carries the condition CHECK
     * constraints that mirror the Java record invariants, plus the claim
     * (MI8-001) and lease-owner guarded state-update (MI9-001) SQL — so a
     * future edit that drops a guard fails the build. DDL / SQL remain a
     * contract draft (path B: no JDBC / Flyway in agent-bus).
     */
    @Test
    void forwarding_persistence_ddl_enforces_record_invariants() {
        assertThat(PERSISTENCE)
                .as("forwarding-persistence.md must be reachable from agent-bus basedir")
                .exists();
        String ddl;
        try {
            ddl = Files.readString(PERSISTENCE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // outbox condition CHECK constraints (MI9-006) mirror the MI9-003 record invariants
        assertThat(ddl).contains("ck_outbox_attempt_count",
                "ck_outbox_lease_paired", "ck_outbox_retry_has_next_attempt",
                "ck_outbox_failure_code", "ck_outbox_lease_status");
        // inbox condition CHECK constraints
        assertThat(ddl).contains("ck_inbox_consumed_at", "ck_inbox_failure_code",
                "ck_inbox_dup_code");
        // claim (MI8-001) + lease-owner guarded state mutation (MI9-001) SQL
        assertThat(ddl).contains("FOR UPDATE SKIP LOCKED");
        assertThat(ddl).contains("lease_owner = :leaseOwner");
        assertThat(ddl).contains("lease_until > :now");
    }

    // ===== Stage 11 — deliver-exception swallow (MI11-002) + runOnce fail-fast (MI11-003) =====
    //
    // Slice 1 (MI11-001): lease renewal now reads the injected EpochClock (real elapsed
    // time), not the tick-start instant — so renewal can fire under a natural dispatch
    // loop whose every tick stamps leaseUntil = now + leaseDurationMillis. The three
    // renewal scenarios (renew / no renew / renew fails) are covered by
    // worker_renews_short_lease_before_delivery, worker_does_not_renew_when_lease_sufficient
    // and worker_skips_when_lease_renew_fails, rewritten above for the injected clock.

    /**
     * Slice 2 (MI11-002): a {@code deliver} that throws a non-lease
     * {@link RuntimeException} (a real transport binding must map transport errors to a
     * {@link ForwardingDeliveryResult} and not throw — see the ICD) is swallowed as a
     * {@code skipped} record: the record is left DISPATCHING (reclaimed on lease expiry)
     * and the tick continues, never aborting on a single failing delivery.
     */
    @Test
    void worker_skips_record_when_delivery_throws() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        ForwardingEnvelope env = envelope("msg-throw", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);

        // a delivery port that throws on every deliver — simulates a transport binding
        // that violates the "never throw a non-lease RuntimeException" contract
        ForwardingDeliveryPort throwingDelivery = new ForwardingDeliveryPort() {
            @Override
            public ForwardingDeliveryResult deliver(ForwardingOutboxRecord record, long nowMillisEpoch) {
                throw new UncheckedIOException(new IOException("transport down"));
            }
        };
        ForwardingDispatcherWorker worker =
                new ForwardingDispatcherWorker(outbox, outbox, throwingDelivery);

        ForwardingDispatcherWorker.DispatchTickResult tick =
                worker.runOnce("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL);

        assertThat(tick.claimed()).isEqualTo(1);
        assertThat(tick.acked()).isZero();
        assertThat(tick.skipped())
                .as("a throwing deliver is counted as skipped, not an aborted tick")
                .isEqualTo(1);
        // the record survives, still DISPATCHING, reclaimed on lease expiry
        assertThat(outbox.statusOf(env.messageId(), "tenant-a")).isEqualTo(DISPATCHING);
    }

    /**
     * Slice 3 (MI11-003): {@code runOnce} throws {@link IllegalArgumentException} only
     * for illegal arguments (blank tenant / blank lease owner, non-positive limit) — a
     * caller bug it fails fast on — and {@link ForwardingDispatchLoop} propagates that
     * fail-fast rather than masking it (no loop-level tick-exception swallowing).
     */
    @Test
    void run_once_fails_fast_on_blank_tenant_and_loop_propagates() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        InMemoryForwardingDelivery delivery = new InMemoryForwardingDelivery();
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(outbox, outbox, delivery);

        // blank tenant -> fail fast
        assertThatThrownBy(() -> worker.runOnce("", NOW, 10, LEASE_OWNER, LEASE_UNTIL))
                .isInstanceOf(IllegalArgumentException.class);
        // blank lease owner -> fail fast
        assertThatThrownBy(() -> worker.runOnce("tenant-a", NOW, 10, "", LEASE_UNTIL))
                .isInstanceOf(IllegalArgumentException.class);
        // non-positive limit -> fail fast
        assertThatThrownBy(() -> worker.runOnce("tenant-a", NOW, 0, LEASE_OWNER, LEASE_UNTIL))
                .isInstanceOf(IllegalArgumentException.class);

        // the dispatch loop propagates the fail-fast — it does not swallow a caller bug
        ForwardingDispatchLoop loop = new ForwardingDispatchLoop(worker,
                () -> OptionalLong.of(NOW), ForwardingDispatchLoop.NO_BACKOFF);
        assertThatThrownBy(() -> loop.run("", 1, LEASE_OWNER, 30_000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===== Stage 14 — deliver retry / backoff policy (separation of concerns) =====
    //
    // Retry timing is now governance owned by ForwardingRetryPolicy, lifted out of
    // ForwardingDeliveryResult (which reports WHAT happened, not WHEN to retry).
    // On a RETRY_SCHEDULED result the worker asks the policy: exhausted? -> DLQ
    // (a retryable code is a legal DLQ code); else nextAttemptAt -> RETRY.

    /**
     * Stage 14: exponential backoff is monotonic, capped, and overflow-safe —
     * a runaway attemptCount never yields a negative or absurd delay.
     */
    @Test
    void retry_policy_backoff_is_monotonic_and_capped() {
        ForwardingRetryPolicy.ExponentialBackoff policy =
                new ForwardingRetryPolicy.ExponentialBackoff(100L, 1_000L, 10, () -> 0L);
        ForwardingFailureCode code = ForwardingFailureCode.RECEIVER_UNAVAILABLE;
        long d0 = policy.nextAttemptAt(code, 0, NOW) - NOW;
        long d1 = policy.nextAttemptAt(code, 1, NOW) - NOW;
        long d2 = policy.nextAttemptAt(code, 2, NOW) - NOW;
        long d3 = policy.nextAttemptAt(code, 3, NOW) - NOW;
        long d4 = policy.nextAttemptAt(code, 4, NOW) - NOW;
        assertThat(d0).as("attempt 0 -> base").isEqualTo(100L);
        assertThat(d1).as("attempt 1 -> 2*base").isEqualTo(200L);
        assertThat(d2).as("attempt 2 -> 4*base").isEqualTo(400L);
        assertThat(d3).as("attempt 3 -> 8*base").isEqualTo(800L);
        assertThat(d4).as("attempt 4 -> capped (100<<4=1600 > cap 1000)").isEqualTo(1_000L);
        assertThat(d0).isLessThanOrEqualTo(d1);
        assertThat(d1).isLessThanOrEqualTo(d2);
        assertThat(d2).isLessThanOrEqualTo(d3);
        assertThat(d3).isLessThanOrEqualTo(d4);
        assertThat(policy.nextAttemptAt(code, 100, NOW) - NOW)
                .as("attempt 100 clamped to cap (overflow-safe)")
                .isEqualTo(1_000L);
    }

    /** Stage 14: jitter is added to the backoff, and a negative jitter is clamped. */
    @Test
    void retry_policy_jitter_is_added_and_negative_clamped() {
        ForwardingRetryPolicy.ExponentialBackoff withJitter =
                new ForwardingRetryPolicy.ExponentialBackoff(100L, 1_000L, 5, () -> 50L);
        assertThat(withJitter.nextAttemptAt(ForwardingFailureCode.DELIVERY_TIMEOUT, 0, NOW))
                .as("jitter added: now + base(100) + jitter(50)")
                .isEqualTo(NOW + 150L);
        ForwardingRetryPolicy.ExponentialBackoff negativeJitter =
                new ForwardingRetryPolicy.ExponentialBackoff(100L, 1_000L, 5, () -> -10L);
        assertThat(negativeJitter.nextAttemptAt(ForwardingFailureCode.DELIVERY_TIMEOUT, 0, NOW))
                .as("negative jitter clamped to 0: now + base(100)")
                .isEqualTo(NOW + 100L);
    }

    /** Stage 14: exhausted() flips at the budget; maxAttempts=0 disables retry. */
    @Test
    void retry_policy_exhausted_flips_at_budget() {
        ForwardingRetryPolicy.ExponentialBackoff budget =
                new ForwardingRetryPolicy.ExponentialBackoff(100L, 1_000L, 2, () -> 0L);
        assertThat(budget.exhausted(0)).as("attempt 0 under budget").isFalse();
        assertThat(budget.exhausted(1)).as("attempt 1 under budget").isFalse();
        assertThat(budget.exhausted(2)).as("attempt 2 == budget -> exhausted").isTrue();
        ForwardingRetryPolicy.ExponentialBackoff noRetry =
                new ForwardingRetryPolicy.ExponentialBackoff(100L, 1_000L, 0, () -> 0L);
        assertThat(noRetry.exhausted(0))
                .as("maxAttempts=0 disables retry: the first failure is exhausted").isTrue();
    }

    /**
     * Stage 14: a retryable failure whose retries are exhausted is routed to DLQ
     * (a retryable code is a legal DLQ code), not RETRY. maxAttempts=0 disables
     * retry entirely — the first failure goes straight to DLQ.
     */
    @Test
    void worker_routes_exhausted_retryable_failure_to_dlq() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        InMemoryForwardingDelivery delivery = new InMemoryForwardingDelivery();
        ForwardingRetryPolicy noRetry =
                new ForwardingRetryPolicy.ExponentialBackoff(100L, 1_000L, 0, () -> 0L);
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                outbox, outbox, delivery,
                ForwardingDispatcherWorker.DispatchLeasePolicy.DISABLED, EpochClock.SYSTEM, noRetry);

        ForwardingEnvelope env = envelope("msg-exh", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        delivery.put("msg-exh",
                ForwardingDeliveryResult.retry(ForwardingFailureCode.BACKPRESSURE_REJECTED));

        ForwardingDispatcherWorker.DispatchTickResult tick =
                worker.runOnce("tenant-a", NOW, 10, LEASE_OWNER, LEASE_UNTIL);

        assertThat(tick.claimed()).isEqualTo(1);
        assertThat(tick.retried())
                .as("an exhausted retryable failure is not retried")
                .isZero();
        assertThat(tick.dlqd())
                .as("an exhausted retryable failure goes to DLQ")
                .isEqualTo(1);
        assertThat(outbox.statusOf(env.messageId(), "tenant-a")).isEqualTo(DLQ);
        assertThat(outbox.recordOf(env.messageId(), "tenant-a").lastFailureCode())
                .isEqualTo(ForwardingFailureCode.BACKPRESSURE_REJECTED);
    }

    /**
     * Stage 14: with a real retry budget, a retryable failure first RETRY-schedules
     * (attemptCount increments) and only reaches DLQ once the budget is spent.
     * maxAttempts=2: attempts 0 and 1 retry; attempt 2 is exhausted -> DLQ.
     */
    @Test
    void worker_retries_then_exhausts_to_dlq() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        InMemoryForwardingDelivery delivery = new InMemoryForwardingDelivery();
        ForwardingRetryPolicy budget =
                new ForwardingRetryPolicy.ExponentialBackoff(100L, 1_000L, 2, () -> 0L);
        long[] clockNow = {NOW};
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                outbox, outbox, delivery,
                ForwardingDispatcherWorker.DispatchLeasePolicy.DISABLED,
                (EpochClock) () -> clockNow[0], budget);

        ForwardingEnvelope env = envelope("msg-budget", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        delivery.put("msg-budget",
                ForwardingDeliveryResult.retry(ForwardingFailureCode.RECEIVER_UNAVAILABLE));

        // attempt 0: not exhausted (0 < 2) -> RETRY, attemptCount -> 1, nextAttemptAt = NOW+100
        worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        assertThat(outbox.statusOf(env.messageId(), "tenant-a")).isEqualTo(RETRY_SCHEDULED);
        assertThat(outbox.attemptCountOf(env.messageId(), "tenant-a")).isEqualTo(1);

        // advance past the scheduled nextAttemptAt (NOW + 100) and reclaim
        clockNow[0] = NOW + 200;
        worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        assertThat(outbox.statusOf(env.messageId(), "tenant-a")).isEqualTo(RETRY_SCHEDULED);
        assertThat(outbox.attemptCountOf(env.messageId(), "tenant-a"))
                .as("attempt 1: still under budget -> retry again, attemptCount -> 2")
                .isEqualTo(2);

        // attempt 2: exhausted (2 >= 2) -> DLQ
        clockNow[0] = NOW + 1_000;
        ForwardingDispatcherWorker.DispatchTickResult tick =
                worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        assertThat(tick.dlqd())
                .as("attempt 2: budget exhausted -> DLQ")
                .isEqualTo(1);
        assertThat(outbox.statusOf(env.messageId(), "tenant-a")).isEqualTo(DLQ);
    }

    // ===== Stage 16 — per-route circuit breaker wired into the worker =====
    //
    // A RouteCircuitBreaker (failureThreshold / cooldownMillis / clock) is injected
    // through the 7-arg constructor. Before each delivery the worker asks
    // allowsDelivery; an OPEN route short-circuits along the existing skip path
    // (left DISPATCHING, reclaimed on lease expiry, consuming no attemptCount).
    // After each delivery — and from the deliver-exception catch as a
    // RECEIVER_UNAVAILABLE failure — the outcome is fed back so the breaker's
    // three-state machine advances and a HALF_OPEN probe can never strand its
    // in-flight marker.

    /**
     * Stage 16: consecutive retryable failures on a route trip the breaker; once
     * OPEN, the next claim is short-circuited — skipped (not delivered), left
     * DISPATCHING, and its attemptCount frozen. The short-circuit consumes no
     * retry budget, exactly like the lease / deliver-exception skip paths, so the
     * {@code DispatchTickResult} self-consistency invariant still holds.
     */
    @Test
    void circuit_breaker_short_circuits_failing_route_and_freezes_attempt_count() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        InMemoryForwardingDelivery delivery = new InMemoryForwardingDelivery();
        delivery.setDefault(ForwardingDeliveryResult.retry(ForwardingFailureCode.RECEIVER_UNAVAILABLE));
        long[] clockNow = {NOW};
        RouteCircuitBreaker breaker = new RouteCircuitBreaker(3, 100_000L, () -> clockNow[0]);
        ForwardingRetryPolicy budget =
                new ForwardingRetryPolicy.ExponentialBackoff(100L, 10_000L, 10, () -> 0L);
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                outbox, outbox, delivery,
                ForwardingDispatcherWorker.DispatchLeasePolicy.DISABLED,
                (EpochClock) () -> clockNow[0], budget, breaker);
        ForwardingRouteHandle route = new ForwardingRouteHandle("route-for-tenant-a", "tenant-a");

        ForwardingEnvelope env = envelope("msg-cb-trip", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);

        // three consecutive retryable failures trip the breaker (threshold = 3)
        worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        assertThat(outbox.attemptCountOf(env.messageId(), "tenant-a")).isEqualTo(1);
        clockNow[0] = NOW + 200;
        worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        assertThat(outbox.attemptCountOf(env.messageId(), "tenant-a")).isEqualTo(2);
        clockNow[0] = NOW + 500;
        worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        assertThat(outbox.attemptCountOf(env.messageId(), "tenant-a"))
                .as("attempt 3 is still delivered — the breaker trips only AFTER this failure")
                .isEqualTo(3);
        assertThat(breaker.stateOf(route)).isEqualTo(RouteCircuitBreaker.State.OPEN);

        // the breaker is now OPEN: the next claim is short-circuited before delivery
        clockNow[0] = NOW + 1_000;
        ForwardingDispatcherWorker.DispatchTickResult tick =
                worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        assertThat(tick.skipped())
                .as("an OPEN route is skipped, not delivered")
                .isEqualTo(1);
        assertThat(tick.retried()).isZero();
        assertThat(outbox.statusOf(env.messageId(), "tenant-a"))
                .as("a short-circuited record is left DISPATCHING (reclaimed on lease expiry)")
                .isEqualTo(DISPATCHING);
        assertThat(outbox.attemptCountOf(env.messageId(), "tenant-a"))
                .as("the short-circuit consumes no attemptCount")
                .isEqualTo(3);
    }

    /**
     * Stage 16: once OPEN, after the cooldown elapses the breaker half-opens and
     * allows a single probe; a probe that succeeds restores CLOSED (count reset),
     * so a subsequent delivery on the same route goes through normally again.
     */
    @Test
    void circuit_breaker_half_opens_after_cooldown_and_closes_on_probe_success() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        InMemoryForwardingDelivery delivery = new InMemoryForwardingDelivery();
        delivery.setDefault(ForwardingDeliveryResult.retry(ForwardingFailureCode.RECEIVER_UNAVAILABLE));
        long[] clockNow = {NOW};
        RouteCircuitBreaker breaker = new RouteCircuitBreaker(3, 1_000L, () -> clockNow[0]);
        ForwardingRetryPolicy budget =
                new ForwardingRetryPolicy.ExponentialBackoff(100L, 10_000L, 10, () -> 0L);
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                outbox, outbox, delivery,
                ForwardingDispatcherWorker.DispatchLeasePolicy.DISABLED,
                (EpochClock) () -> clockNow[0], budget, breaker);
        ForwardingRouteHandle route = new ForwardingRouteHandle("route-for-tenant-a", "tenant-a");

        ForwardingEnvelope env = envelope("msg-cb-probe", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);

        // trip the breaker (3 retryable failures); OPEN at NOW + 500
        worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        clockNow[0] = NOW + 200;
        worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        clockNow[0] = NOW + 500;
        worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        assertThat(breaker.stateOf(route)).isEqualTo(RouteCircuitBreaker.State.OPEN);

        // past cooldown: the breaker half-opens one probe; make the probe succeed
        clockNow[0] = NOW + 1_500;
        delivery.put("msg-cb-probe", ForwardingDeliveryResult.acked());
        ForwardingDispatcherWorker.DispatchTickResult probe =
                worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        assertThat(probe.acked())
                .as("the HALF_OPEN probe is delivered and, on success, acked")
                .isEqualTo(1);
        assertThat(breaker.stateOf(route))
                .as("a successful probe restores CLOSED (count reset)")
                .isEqualTo(RouteCircuitBreaker.State.CLOSED);
        assertThat(outbox.statusOf(env.messageId(), "tenant-a")).isEqualTo(ACKED);

        // CLOSED again: a fresh record on the same route is delivered normally
        ForwardingEnvelope env2 = envelope("msg-cb-recovered", "tenant-a");
        outbox.enqueue(env2, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        delivery.put("msg-cb-recovered", ForwardingDeliveryResult.acked());
        clockNow[0] = NOW + 1_600;
        ForwardingDispatcherWorker.DispatchTickResult after =
                worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        assertThat(after.acked())
                .as("once CLOSED, the route delivers again (no short-circuit)")
                .isEqualTo(1);
    }

    /**
     * Stage 16: a {@code deliver} that throws a non-lease {@link RuntimeException}
     * (a real transport binding must map transport errors and not throw — see the
     * ICD) is fed back to the breaker as a retryable RECEIVER_UNAVAILABLE failure,
     * so thrown deliveries still count toward the threshold. Three thrown delivers
     * on the same route trip the breaker; a fourth record on that route is then
     * short-circuited rather than delivered into the throwing transport.
     */
    @Test
    void deliver_exception_counts_as_breaker_failure() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        ForwardingDeliveryPort throwingDelivery = new ForwardingDeliveryPort() {
            @Override
            public ForwardingDeliveryResult deliver(ForwardingOutboxRecord record, long nowMillisEpoch) {
                throw new UncheckedIOException(new IOException("transport down"));
            }
        };
        long[] clockNow = {NOW};
        RouteCircuitBreaker breaker = new RouteCircuitBreaker(3, 100_000L, () -> clockNow[0]);
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                outbox, outbox, throwingDelivery,
                ForwardingDispatcherWorker.DispatchLeasePolicy.DISABLED,
                (EpochClock) () -> clockNow[0],
                ForwardingRetryPolicy.DEFAULT, breaker);
        ForwardingRouteHandle route = new ForwardingRouteHandle("route-for-tenant-a", "tenant-a");

        // three records on the same route, all thrown -> three failures fed back -> OPEN
        outbox.enqueue(envelope("msg-throw-1", "tenant-a"), SOURCE_SERVICE, TARGET_SERVICE, NOW);
        outbox.enqueue(envelope("msg-throw-2", "tenant-a"), SOURCE_SERVICE, TARGET_SERVICE, NOW);
        outbox.enqueue(envelope("msg-throw-3", "tenant-a"), SOURCE_SERVICE, TARGET_SERVICE, NOW);
        ForwardingDispatcherWorker.DispatchTickResult tick1 =
                worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        assertThat(tick1.claimed()).isEqualTo(3);
        assertThat(tick1.skipped())
                .as("each thrown deliver is skipped (left DISPATCHING)")
                .isEqualTo(3);
        assertThat(breaker.stateOf(route))
                .as("three thrown delivers tripped the breaker (the catch fed each failure back)")
                .isEqualTo(RouteCircuitBreaker.State.OPEN);

        // a fourth record on the now-OPEN route is short-circuited before delivery
        outbox.enqueue(envelope("msg-throw-4", "tenant-a"), SOURCE_SERVICE, TARGET_SERVICE, NOW);
        ForwardingDispatcherWorker.DispatchTickResult tick2 =
                worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        assertThat(tick2.skipped())
                .as("the fourth record is short-circuited, not delivered into the throwing transport")
                .isEqualTo(1);
        assertThat(outbox.statusOf(new ForwardingMessageId("msg-throw-4"), "tenant-a"))
                .isEqualTo(DISPATCHING);
    }

    /**
     * Stage 16: a HALF_OPEN probe whose {@code deliver} throws is fed back as a
     * failure (the catch records a RECEIVER_UNAVAILABLE), so the breaker re-opens
     * and clears the in-flight probe marker — it is not stranded in HALF_OPEN.
     * After the refreshed cooldown a fresh probe is allowed again, proving the
     * thrown probe did not leak its {@code probeInFlight} marker.
     */
    @Test
    void thrown_half_open_probe_reopens_without_stranding() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        InMemoryForwardingDelivery backing = new InMemoryForwardingDelivery();
        backing.setDefault(ForwardingDeliveryResult.retry(ForwardingFailureCode.RECEIVER_UNAVAILABLE));
        boolean[] throwNext = {false};
        ForwardingDeliveryPort delivery = new ForwardingDeliveryPort() {
            @Override
            public ForwardingDeliveryResult deliver(ForwardingOutboxRecord record, long nowMillisEpoch) {
                if (throwNext[0]) {
                    throw new UncheckedIOException(new IOException("transport down"));
                }
                return backing.deliver(record, nowMillisEpoch);
            }
        };
        long[] clockNow = {NOW};
        RouteCircuitBreaker breaker = new RouteCircuitBreaker(3, 1_000L, () -> clockNow[0]);
        ForwardingRetryPolicy budget =
                new ForwardingRetryPolicy.ExponentialBackoff(100L, 10_000L, 10, () -> 0L);
        ForwardingDispatcherWorker worker = new ForwardingDispatcherWorker(
                outbox, outbox, delivery,
                ForwardingDispatcherWorker.DispatchLeasePolicy.DISABLED,
                (EpochClock) () -> clockNow[0], budget, breaker);
        ForwardingRouteHandle route = new ForwardingRouteHandle("route-for-tenant-a", "tenant-a");

        ForwardingEnvelope env = envelope("msg-leak", "tenant-a");
        outbox.enqueue(env, SOURCE_SERVICE, TARGET_SERVICE, NOW);

        // trip the breaker via 3 retryable failures; OPEN at NOW + 500
        worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        clockNow[0] = NOW + 200;
        worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        clockNow[0] = NOW + 500;
        worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        assertThat(breaker.stateOf(route)).isEqualTo(RouteCircuitBreaker.State.OPEN);

        // past cooldown: the breaker half-opens a probe, but deliver throws. The catch
        // feeds the failure back so the breaker re-opens and the probe marker is cleared.
        clockNow[0] = NOW + 1_500;
        throwNext[0] = true;
        ForwardingDispatcherWorker.DispatchTickResult thrownProbe =
                worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        assertThat(thrownProbe.skipped()).isEqualTo(1);
        assertThat(breaker.stateOf(route))
                .as("the thrown probe re-opened the breaker (openedAt refreshed to NOW + 1500)")
                .isEqualTo(RouteCircuitBreaker.State.OPEN);

        // after the refreshed cooldown (from NOW + 1500), a fresh probe is allowed again —
        // proving the thrown probe did not strand a HALF_OPEN marker. Make this probe succeed.
        throwNext[0] = false;
        ForwardingEnvelope env2 = envelope("msg-leak-2", "tenant-a");
        outbox.enqueue(env2, SOURCE_SERVICE, TARGET_SERVICE, NOW);
        backing.put("msg-leak-2", ForwardingDeliveryResult.acked());
        clockNow[0] = NOW + 2_500;
        ForwardingDispatcherWorker.DispatchTickResult recovered =
                worker.runOnce("tenant-a", clockNow[0], 10, LEASE_OWNER, clockNow[0] + 30_000);
        assertThat(recovered.acked())
                .as("a fresh probe is allowed after the refreshed cooldown (no stranded marker)")
                .isEqualTo(1);
        assertThat(breaker.stateOf(route)).isEqualTo(RouteCircuitBreaker.State.CLOSED);
    }

    // ===== helpers =====

    private static ForwardingEnvelope envelope(String messageIdValue, String tenantId) {
        return new ForwardingEnvelope(
                new ForwardingMessageId(messageIdValue),
                tenantId,
                "trace-" + messageIdValue,
                "corr-" + messageIdValue,
                "idem-" + messageIdValue,
                new ForwardingRouteHandle("route-for-" + tenantId, tenantId),
                "capability-" + messageIdValue,
                Long.MAX_VALUE,
                ForwardingEnvelope.PayloadPolicy.CONTROL_ONLY,
                null);
    }

    private static Set<String> recordFieldNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }

    private static List<String> readForwardingProductionSources() throws IOException {
        Path root = Path.of("src/main/java/com/huawei/ascend/bus/forwarding");
        // Stage 15: the A2A transport adapter (runtime/transport/a2a) parses the
        // remote A2A wire format and legitimately references the SDK's Task /
        // TaskStatus types to map a remote Task lifecycle onto
        // ForwardingDeliveryResult — it does NOT store Task state on the outbox
        // record (§6.2 still holds for the forwarding core: ports, state machine,
        // worker, loop, record). Excluded from this §6.2 text scan, mirroring how
        // AgentBusForwardingSpiPurityTest confines org.a2aproject to that subpackage
        // (and how JDBC is confined to persistence.jdbc, Stage 12).
        Path a2aTransportAdapter =
                Path.of("src/main/java/com/huawei/ascend/bus/forwarding/runtime/transport/a2a");
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.startsWith(a2aTransportAdapter))
                    .map(AgentBusForwardingRuntimeContractTest::readStringUnchecked)
                    .toList();
        }
    }

    private static String readStringUnchecked(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
