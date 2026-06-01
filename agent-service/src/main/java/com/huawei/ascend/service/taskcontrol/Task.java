package com.huawei.ascend.service.taskcontrol;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JavaBean-style Task-Centric Control (TCC) task snapshot.
 *
 * <p>The object can be stored in a queue, but queue code must remain
 * payload-agnostic. State decisions belong to Task-Centric Control APIs, while
 * runtime adapters report state intent back through TaskControlClient mark*
 * calls.
 */
public class Task {

    private String tenantId;
    private String sessionId;
    private String taskId;
    private String agentId;
    private TaskState state;
    private long revision;
    private WaitingReason waitingReason;
    private TaskFailureCode failureCode;
    /*
     * Small runtime/context detail may be kept inline. Large payloads should be
     * compressed or referenced by higher-level code so the scheduling queue stays
     * thin.
     */
    private Object detail;
    private Instant createdAt;
    private Instant updatedAt;

    public Task() {
    }

    public Task(String tenantId, String sessionId, String taskId, String agentId,
                TaskState state, long revision, Instant createdAt, Instant updatedAt) {
        setTenantId(tenantId);
        setSessionId(sessionId);
        setTaskId(taskId);
        setAgentId(agentId);
        setState(state);
        setRevision(revision);
        setCreatedAt(createdAt);
        setUpdatedAt(updatedAt);
    }

    public static Task created(String tenantId, String sessionId, String agentId, Instant now) {
        return new Task(tenantId, sessionId, UUID.randomUUID().toString(), agentId,
                TaskState.CREATED, 1L, now, now);
    }

    /**
     * Creates a task with a caller-supplied {@code taskId}. The access layer
     * pre-allocates the id so it can bind the reply (egress) channel before the
     * task is dispatched, which the synchronous in-memory engine requires.
     */
    public static Task created(String tenantId, String sessionId, String taskId, String agentId, Instant now) {
        String id = taskId == null || taskId.isBlank() ? UUID.randomUUID().toString() : taskId;
        return new Task(tenantId, sessionId, id, agentId, TaskState.CREATED, 1L, now, now);
    }

    public Task transitionTo(TaskState nextState, WaitingReason nextWaitingReason,
                             TaskFailureCode nextFailureCode, Object nextDetail, Instant now) {
        setState(nextState);
        setWaitingReason(nextWaitingReason);
        setFailureCode(nextFailureCode);
        setDetail(nextDetail);
        setRevision(revision + 1L);
        setUpdatedAt(now);
        return this;
    }

    public boolean terminal() {
        return state == TaskState.COMPLETED || state == TaskState.FAILED || state == TaskState.CANCELLED;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = requireNonBlank(tenantId, "tenantId");
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = requireNonBlank(sessionId, "sessionId");
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = requireNonBlank(taskId, "taskId");
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId == null || agentId.isBlank() ? null : agentId;
    }

    public TaskState getState() {
        return state;
    }

    public void setState(TaskState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        if (revision < 1L) {
            throw new IllegalArgumentException("revision must be positive");
        }
        this.revision = revision;
    }

    public WaitingReason getWaitingReason() {
        return waitingReason;
    }

    public void setWaitingReason(WaitingReason waitingReason) {
        this.waitingReason = waitingReason;
    }

    public TaskFailureCode getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(TaskFailureCode failureCode) {
        this.failureCode = failureCode;
    }

    public Object getDetail() {
        return detail;
    }

    public void setDetail(Object detail) {
        this.detail = detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
