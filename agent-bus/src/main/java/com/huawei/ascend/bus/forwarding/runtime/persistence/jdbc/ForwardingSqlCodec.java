package com.huawei.ascend.bus.forwarding.runtime.persistence.jdbc;

import com.huawei.ascend.bus.forwarding.spi.AgentBusEventType;
import com.huawei.ascend.bus.forwarding.spi.ForwardingFailureCode;
import com.huawei.ascend.bus.forwarding.spi.ForwardingInboxRecord;
import com.huawei.ascend.bus.forwarding.spi.ForwardingLease;
import com.huawei.ascend.bus.forwarding.spi.ForwardingMessageId;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;
import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;
import com.huawei.ascend.bus.forwarding.spi.ForwardingStatus;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Column ↔ record codec for the C3 forwarding JDBC adapter (Stage 12, MI12-002).
 *
 * <p>The single place that translates between the
 * {@code agent_bus_forwarding_outbox} / {@code agent_bus_forwarding_inbox} table
 * columns and the immutable {@link ForwardingOutboxRecord} /
 * {@link ForwardingInboxRecord} value objects. Two encoding rules live here and
 * nowhere else:
 * <ul>
 *   <li>{@code status} is stored as the Java enum name (UPPER) and read back with
 *       {@code Enum.valueOf} — the DDL {@code CHECK} constraint lists the same
 *       UPPER literals.</li>
 *   <li>{@code last_failure_code} / {@code failure_code} are stored as the
 *       snake_case ICD wire code ({@link ForwardingFailureCode#wireCode()}) — the
 *       inbox {@code CHECK ck_inbox_dup_code} hard-codes
 *       {@code 'duplicate_suppressed'}, so the wire form is the on-disk contract.</li>
 * </ul>
 *
 * <p>{@code route_handle} stores {@code routeHandle.value()}; {@code tenantScope}
 * is recovered from {@code tenant_id} — the record invariant
 * {@code routeHandle.tenantScope() == tenantId} holds by envelope construction, so
 * it is never persisted as a separate column.
 *
 * <p>Nullable epoch columns ({@code next_attempt_at}, {@code lease_until},
 * {@code consumed_at}) are read via {@link ResultSet#wasNull()} and coerced to the
 * primitive the record expects ({@code 0} when absent); the record constructor's
 * per-status invariants (MI9-003) then accept or reject that representation.
 *
 * <p>Authority: {@code architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md}
 * §3 / §9 (field-consistency table); {@code ICD-Agent-Bus-Forwarding-Runtime}
 * (record fields).
 */
final class ForwardingSqlCodec {

    private ForwardingSqlCodec() {
        throw new AssertionError("ForwardingSqlCodec is a namespace of static codecs");
    }

    /** Maps an {@code agent_bus_forwarding_outbox} row to a {@link ForwardingOutboxRecord}. */
    static final RowMapper<ForwardingOutboxRecord> OUTBOX_ROW_MAPPER =
            (rs, rowNum) -> mapOutbox(rs);

    /** Maps an {@code agent_bus_forwarding_inbox} row to a {@link ForwardingInboxRecord}. */
    static final RowMapper<ForwardingInboxRecord> INBOX_ROW_MAPPER =
            (rs, rowNum) -> mapInbox(rs);

    static ForwardingOutboxRecord mapOutbox(ResultSet rs) throws SQLException {
        String tenantId = rs.getString("tenant_id");
        String leaseOwner = rs.getString("lease_owner");
        // ck_outbox_lease_paired guarantees lease_until is non-null when lease_owner is.
        ForwardingLease lease = (leaseOwner == null)
                ? null
                : new ForwardingLease(leaseOwner, rs.getLong("lease_until"));
        return new ForwardingOutboxRecord(
                tenantId,
                new ForwardingMessageId(rs.getString("message_id")),
                rs.getString("source_service_id"),
                rs.getString("target_service_id"),
                new ForwardingRouteHandle(rs.getString("route_handle"), tenantId),
                rs.getString("payload_ref"),
                ForwardingStatus.Outbox.valueOf(rs.getString("status")),
                rs.getInt("attempt_count"),
                nullableLongOrDefault(rs, "next_attempt_at", 0L),
                rs.getLong("created_at"),
                rs.getLong("updated_at"),
                decodeFailureCode(rs.getString("last_failure_code")),
                lease,
                // FEAT-013 V3: correlation_id recovered from the column (null for pre-FEAT-013 rows).
                rs.getString("correlation_id"),
                // FEAT-013 V3: event_type recovered → AgentBusEventType (null for pre-FEAT-013 rows).
                decodeEventType(rs.getString("event_type")));
    }

    static ForwardingInboxRecord mapInbox(ResultSet rs) throws SQLException {
        return new ForwardingInboxRecord(
                rs.getString("tenant_id"),
                new ForwardingMessageId(rs.getString("message_id")),
                rs.getString("consumer_service_id"),
                ForwardingStatus.Inbox.valueOf(rs.getString("status")),
                rs.getLong("received_at"),
                nullableLongOrDefault(rs, "consumed_at", 0L),
                decodeFailureCode(rs.getString("failure_code")));
    }

    /**
     * Snake_case ICD wire code → {@link ForwardingFailureCode} (null-safe). An
     * unknown wire value means the on-disk row drifted from the enum — surface it
     * loudly rather than silently misclassify.
     */
    static ForwardingFailureCode decodeFailureCode(String wire) {
        if (wire == null) {
            return null;
        }
        for (ForwardingFailureCode code : ForwardingFailureCode.values()) {
            if (code.wireCode().equals(wire)) {
                return code;
            }
        }
        throw new IllegalStateException(
                "unknown forwarding failure_code wire value in db row: " + wire);
    }

    /**
     * FEAT-013 V3: on-disk event_type → {@link AgentBusEventType} (null-safe, mirrors
     * {@link #decodeFailureCode}'s "surface unknown loudly" stance). Null for
     * pre-FEAT-013 / control-only rows.
     */
    static AgentBusEventType decodeEventType(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        return AgentBusEventType.valueOf(wire);
    }

    private static long nullableLongOrDefault(ResultSet rs, String column, long defaultValue)
            throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? defaultValue : value;
    }
}
