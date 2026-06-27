package com.huawei.ascend.examples.workmate.approval;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class ApprovalGate {

    public enum Verdict {
        APPROVED,
        DENIED,
        TIMED_OUT
    }

    private final ApprovalService approvalService;
    private volatile Consumer<PendingApproval> listener;
    private final long timeoutSeconds;

    public ApprovalGate(ApprovalService approvalService, long timeoutSeconds) {
        this.approvalService = approvalService;
        this.timeoutSeconds = timeoutSeconds;
    }

    public void setListener(Consumer<PendingApproval> listener) {
        this.listener = listener;
    }

    public Verdict await(UUID sessionId, String taskId, String toolName, Map<String, Object> args) {
        ToolRiskPolicy.RiskAssessment risk = ToolRiskPolicy.assess(toolName, args);
        if (risk.policyBlocked()) {
            return Verdict.DENIED;
        }
        if (!risk.requiresApproval()) {
            return Verdict.APPROVED;
        }
        if (approvalService.isAlwaysAllowed(sessionId, toolName, risk.summary())) {
            return Verdict.APPROVED;
        }

        PendingApproval pending = approvalService.register(sessionId, taskId, toolName, risk, args);
        Consumer<PendingApproval> notify = listener;
        if (notify != null) {
            notify.accept(pending);
        }

        try {
            if (!pending.await(timeoutSeconds, TimeUnit.SECONDS)) {
                approvalService.expire(pending.id());
                return Verdict.TIMED_OUT;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            approvalService.expire(pending.id());
            return Verdict.DENIED;
        }
        return pending.verdict().orElse(Verdict.DENIED);
    }

    public static final class PendingApproval {
        private final UUID id;
        private final UUID sessionId;
        private final String taskId;
        private final String toolName;
        private final ToolRiskPolicy.RiskAssessment risk;
        private final Map<String, Object> args;
        private final Instant createdAt;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<Verdict> verdict = new AtomicReference<>();

        PendingApproval(
                UUID id,
                UUID sessionId,
                String taskId,
                String toolName,
                ToolRiskPolicy.RiskAssessment risk,
                Map<String, Object> args) {
            this.id = id;
            this.sessionId = sessionId;
            this.taskId = taskId;
            this.toolName = toolName;
            this.risk = risk;
            this.args = args;
            this.createdAt = Instant.now();
        }

        public UUID id() {
            return id;
        }

        public UUID sessionId() {
            return sessionId;
        }

        public String taskId() {
            return taskId;
        }

        public String toolName() {
            return toolName;
        }

        public ToolRiskPolicy.RiskAssessment risk() {
            return risk;
        }

        public Map<String, Object> args() {
            return args;
        }

        public Instant createdAt() {
            return createdAt;
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        void complete(Verdict decision) {
            verdict.set(decision);
            latch.countDown();
        }

        java.util.Optional<Verdict> verdict() {
            return java.util.Optional.ofNullable(verdict.get());
        }
    }
}
