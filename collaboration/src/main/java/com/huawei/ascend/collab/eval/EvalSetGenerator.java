package com.huawei.ascend.collab.eval;

import com.huawei.ascend.collab.core.SubTask;
import com.huawei.ascend.collab.sim.ScriptedWorker.Behavior;
import java.util.List;
import java.util.Map;

/**
 * Generates the multi-task collaboration eval set ("评测集生成") — a curated suite
 * of scenarios that exercises every collaboration path: distribution, hand-over,
 * reclaim-and-retry, reclaim-exhausted, task-token verification (forged/absent),
 * result validation, input-required, and capability gaps. Each scenario declares
 * its expected per-task outcome and the coordination events that must occur.
 */
public final class EvalSetGenerator {

    private EvalSetGenerator() {
    }

    public static List<EvalScenario> generate() {
        return List.of(
                happyPath(),
                fanOutDistribution(),
                handOver(),
                reclaimThenSucceed(),
                reclaimExhausted(),
                forgedTokenRejected(),
                absentTokenRejected(),
                validationFailThenReclaim(),
                inputRequired(),
                noCapableWorker(),
                mixedBatch());
    }

    private static EvalScenario happyPath() {
        return new EvalScenario("happy-path", "单任务,单 worker,直接完成",
                List.of(SubTask.of("t1", "summarize", "doc")),
                List.of(WorkerSpec.of("w-sum", "summarize", Behavior.COMPLETE)),
                Map.of("t1", "COMPLETED"),
                List.of("DISPATCH", "VALIDATE_OK", "COMPLETE"));
    }

    private static EvalScenario fanOutDistribution() {
        return new EvalScenario("fan-out-distribution", "多任务分发到不同 capability 的 worker",
                List.of(SubTask.of("t1", "summarize", "d1"),
                        SubTask.of("t2", "translate", "d2"),
                        SubTask.of("t3", "summarize", "d3")),
                List.of(WorkerSpec.of("w-sum-a", "summarize", Behavior.COMPLETE),
                        WorkerSpec.of("w-sum-b", "summarize", Behavior.COMPLETE),
                        WorkerSpec.of("w-tr", "translate", Behavior.COMPLETE)),
                Map.of("t1", "COMPLETED", "t2", "COMPLETED", "t3", "COMPLETED"),
                List.of("DISPATCH", "COMPLETE"));
    }

    private static EvalScenario handOver() {
        return new EvalScenario("hand-over", "worker 把任务交接给另一 capability,再完成",
                List.of(SubTask.of("t1", "triage", "case")),
                List.of(WorkerSpec.handover("w-triage", "triage", "specialist"),
                        WorkerSpec.of("w-spec", "specialist", Behavior.COMPLETE)),
                Map.of("t1", "COMPLETED"),
                List.of("DISPATCH", "HANDOVER", "COMPLETE"));
    }

    private static EvalScenario reclaimThenSucceed() {
        return new EvalScenario("reclaim-then-succeed", "前两次失败被回收重派,第三次成功(maxAttempts=3)",
                List.of(new SubTask("t1", "summarize", "doc", 3, 30_000)),
                List.of(WorkerSpec.flaky("w-flaky", "summarize", 2)),
                Map.of("t1", "COMPLETED"),
                List.of("RECLAIM", "COMPLETE"));
    }

    private static EvalScenario reclaimExhausted() {
        return new EvalScenario("reclaim-exhausted", "始终失败,回收重派耗尽 maxAttempts → FAILED",
                List.of(new SubTask("t1", "summarize", "doc", 3, 30_000)),
                List.of(WorkerSpec.of("w-bad", "summarize", Behavior.FAIL)),
                Map.of("t1", "FAILED"),
                List.of("RECLAIM", "FAIL"));
    }

    private static EvalScenario forgedTokenRejected() {
        return new EvalScenario("forged-token-rejected", "worker 回传伪造令牌 → 令牌校验拒绝",
                List.of(new SubTask("t1", "summarize", "doc", 1, 30_000)),
                List.of(WorkerSpec.of("w-evil", "summarize", Behavior.BAD_TOKEN)),
                Map.of("t1", "REJECTED"),
                List.of("TOKEN_REJECT", "FAIL"));
    }

    private static EvalScenario absentTokenRejected() {
        return new EvalScenario("absent-token-rejected", "worker 不回传令牌 → 拒绝",
                List.of(new SubTask("t1", "summarize", "doc", 1, 30_000)),
                List.of(WorkerSpec.of("w-no-tok", "summarize", Behavior.NO_TOKEN)),
                Map.of("t1", "REJECTED"),
                List.of("TOKEN_REJECT"));
    }

    private static EvalScenario validationFailThenReclaim() {
        return new EvalScenario("validation-fail-reclaim", "空输出未过校验 → 回收;无更多 worker 则 FAILED",
                List.of(new SubTask("t1", "summarize", "doc", 2, 30_000)),
                List.of(WorkerSpec.of("w-empty", "summarize", Behavior.EMPTY_OUTPUT)),
                Map.of("t1", "FAILED"),
                List.of("VALIDATE_FAIL", "RECLAIM"));
    }

    private static EvalScenario inputRequired() {
        return new EvalScenario("input-required", "worker 需要人工输入 → 批处理无法解决,标记 INPUT_REQUIRED",
                List.of(SubTask.of("t1", "approve", "transfer")),
                List.of(WorkerSpec.of("w-appr", "approve", Behavior.INPUT_REQUIRED)),
                Map.of("t1", "INPUT_REQUIRED"),
                List.of("INPUT_REQUIRED"));
    }

    private static EvalScenario noCapableWorker() {
        return new EvalScenario("no-capable-worker", "无 worker 能处理该 capability → FAILED",
                List.of(SubTask.of("t1", "exotic", "x")),
                List.of(WorkerSpec.of("w-sum", "summarize", Behavior.COMPLETE)),
                Map.of("t1", "FAILED"),
                List.of("NO_WORKER"));
    }

    private static EvalScenario mixedBatch() {
        return new EvalScenario("mixed-batch", "混合:完成 + 交接完成 + 伪造令牌拒绝 + 重试成功",
                List.of(SubTask.of("t1", "summarize", "d1"),
                        SubTask.of("t2", "triage", "case"),
                        new SubTask("t3", "translate", "d3", 1, 30_000),
                        new SubTask("t4", "score", "x", 3, 30_000)),
                List.of(WorkerSpec.of("w-sum", "summarize", Behavior.COMPLETE),
                        WorkerSpec.handover("w-triage", "triage", "specialist"),
                        WorkerSpec.of("w-spec", "specialist", Behavior.COMPLETE),
                        WorkerSpec.of("w-tr-evil", "translate", Behavior.BAD_TOKEN),
                        WorkerSpec.flaky("w-score", "score", 1)),
                Map.of("t1", "COMPLETED", "t2", "COMPLETED", "t3", "REJECTED", "t4", "COMPLETED"),
                List.of("HANDOVER", "TOKEN_REJECT", "RECLAIM", "COMPLETE"));
    }
}
