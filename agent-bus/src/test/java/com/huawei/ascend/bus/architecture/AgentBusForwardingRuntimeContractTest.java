package com.huawei.ascend.bus.architecture;

import com.huawei.ascend.bus.forwarding.runtime.ForwardingStateMachine;
import com.huawei.ascend.bus.forwarding.spi.ForwardingEnvelope;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingReceipt;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;
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

    private static final long NOW = 1_700_000_000_000L;

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
        ForwardingReceipt first = outbox.enqueue(env, NOW);
        ForwardingReceipt second = outbox.enqueue(env, NOW);
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
                .as("ForwardingFailureCode wire names mirror the ICD failure modes + payload_ref_invalid")
                .containsExactlyInAnyOrder(
                        "route_not_found", "tenant_mismatch", "delivery_timeout",
                        "receiver_unavailable", "backpressure_rejected",
                        "duplicate_suppressed", "payload_ref_invalid");
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

        ForwardingReceipt receipt = dispatcher.dispatch(env, NOW);
        assertThat(receipt.accepted()).isTrue();
        assertThat(receipt.failureCode()).isNull();
        assertThat(outbox.statusOf(env.messageId(), env.tenantId())).isEqualTo(PENDING);

        outbox.markDispatching(env.messageId(), env.tenantId());
        assertThat(outbox.statusOf(env.messageId(), env.tenantId())).isEqualTo(DISPATCHING);
        outbox.markAcked(env.messageId(), env.tenantId());
        assertThat(outbox.statusOf(env.messageId(), env.tenantId())).isEqualTo(ACKED);
    }

    @Test
    void outbox_retry_increments_attempt_count() {
        InMemoryForwardingOutbox outbox = new InMemoryForwardingOutbox();
        ForwardingEnvelope env = envelope("msg-retry", "tenant-a");
        outbox.enqueue(env, NOW);
        outbox.markDispatching(env.messageId(), env.tenantId());
        outbox.scheduleRetry(env.messageId(), env.tenantId(),
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
                .as("Stage 7 production code: no payload body field, no Task execution state, "
                  + "no JDBC driver, no concrete broker / MQ client (decision §6.2)")
                .allSatisfy(src -> assertThat(src)
                        .doesNotContain("payloadBody", "payload_body")
                        .doesNotContain("TaskExecutionState", "TaskExecution", "TaskStatus")
                        .doesNotContain("java.sql.", "javax.sql.")
                        .doesNotContain("org.apache.kafka", "com.rabbitmq",
                                        "org.apache.rocketmq", "io.nats.client"));
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
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java"))
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
