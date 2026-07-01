package com.openjiuwen.core.alpha.event;

import com.openjiuwen.core.alpha.graph.TaskGraph;
import com.openjiuwen.core.alpha.model.ApprovalGate;
import com.openjiuwen.core.alpha.model.Constraint;
import com.openjiuwen.core.alpha.verifier.RootCause;
import com.openjiuwen.core.kernel.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Alpha 策略事件——PEV 模型的执行过程事件。
 *
 * <p>sealed interface 覆盖 Plan-Execute-Verify 的所有关键节点。
 * 所有事件共享 taskId 和 timestamp，便于事件溯源和排序。
 */
public sealed interface AlphaEvent
    permits AlphaEvent.PlanGenerated,
            AlphaEvent.PlanRevised,
            AlphaEvent.NodeStarted,
            AlphaEvent.NodeCompleted,
            AlphaEvent.NodeFailed,
            AlphaEvent.LayerCompleted,
            AlphaEvent.VerifyPassed,
            AlphaEvent.VerifyFailed,
            AlphaEvent.RootCauseDiagnosed,
            AlphaEvent.ApprovalRequired,
            AlphaEvent.ApprovalGranted,
            AlphaEvent.ApprovalDenied,
            AlphaEvent.ConstraintViolated,
            AlphaEvent.AlphaCompleted {

    TaskId taskId();
    Instant timestamp();

    record PlanGenerated(TaskId taskId, Instant timestamp, TaskGraph plan) implements AlphaEvent {}
    record PlanRevised(TaskId taskId, Instant timestamp, TaskGraph revisedPlan,
                       Set<String> failedNodes, String feedback) implements AlphaEvent {}
    record NodeStarted(TaskId taskId, Instant timestamp, NodeId nodeId, String description) implements AlphaEvent {}
    record NodeCompleted(TaskId taskId, Instant timestamp, NodeId nodeId, Object result) implements AlphaEvent {}
    record NodeFailed(TaskId taskId, Instant timestamp, NodeId nodeId, String error) implements AlphaEvent {}
    record LayerCompleted(TaskId taskId, Instant timestamp, int layerIndex,
                          List<NodeId> completedNodes) implements AlphaEvent {}
    record VerifyPassed(TaskId taskId, Instant timestamp, String feedback) implements AlphaEvent {}
    record VerifyFailed(TaskId taskId, Instant timestamp, String feedback,
                        Set<String> failedNodes) implements AlphaEvent {}
    record RootCauseDiagnosed(TaskId taskId, Instant timestamp, RootCause cause) implements AlphaEvent {}
    record ApprovalRequired(TaskId taskId, Instant timestamp, ApprovalGate gate) implements AlphaEvent {}
    record ApprovalGranted(TaskId taskId, Instant timestamp, String gateId) implements AlphaEvent {}
    record ApprovalDenied(TaskId taskId, Instant timestamp, String gateId, String reason) implements AlphaEvent {}
    record ConstraintViolated(TaskId taskId, Instant timestamp, Constraint constraint) implements AlphaEvent {}
    record AlphaCompleted(TaskId taskId, Instant timestamp, String output,
                          TaskGraph plan, boolean verified, boolean degraded) implements AlphaEvent {}
}
