package com.huawei.ascend.examples.workmate.tools;

import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.Mockito.mock;

import com.huawei.ascend.examples.workmate.audit.AuditLedgerService;
import com.huawei.ascend.examples.workmate.approval.ApprovalGate;
import com.huawei.ascend.examples.workmate.approval.ApprovalService;
import com.huawei.ascend.examples.workmate.approval.dto.ApprovalDecisionRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BashToolHitlTest {

    @TempDir
    Path workspace;

    @Test
    void denyRmPreventsDeletion() throws Exception {
        Path target = workspace.resolve("keep.txt");
        Files.writeString(target, "stay");

        ApprovalService approvalService = new ApprovalService(mock(AuditLedgerService.class));
        ApprovalGate gate = new ApprovalGate(approvalService, 10);
        AtomicReference<ApprovalGate.PendingApproval> pendingRef = new AtomicReference<>();
        gate.setListener(pendingRef::set);

        UUID sessionId = UUID.randomUUID();
        ToolExecutionContext ctx = new ToolExecutionContext(sessionId, "task-1", gate);

        CompletableFuture<Map<String, Object>> bashFuture = CompletableFuture.supplyAsync(() ->
                WorkspaceToolFactory.BashTool.bash(
                        workspace,
                        ctx,
                        new WorkspaceShellExecutor(),
                        WorkmateToolIds.bash(sessionId),
                        Map.of("command", "rm keep.txt")));

        ApprovalGate.PendingApproval pending = waitFor(pendingRef);
        approvalService.decide(pending.id(), new ApprovalDecisionRequest("deny", null, false));

        Map<String, Object> result = bashFuture.get(15, TimeUnit.SECONDS);
        assertThat(result.get("success")).isEqualTo(false);
        assertThat(String.valueOf(result.get("error"))).contains("denied");
        assertThat(Files.exists(target)).isTrue();
    }

    private static ApprovalGate.PendingApproval waitFor(AtomicReference<ApprovalGate.PendingApproval> ref)
            throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            ApprovalGate.PendingApproval pending = ref.get();
            if (pending != null) {
                return pending;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("no pending approval");
    }
}
