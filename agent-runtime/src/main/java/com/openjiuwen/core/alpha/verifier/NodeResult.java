package com.openjiuwen.core.alpha.verifier;

/**
 * 节点执行结果——PregelExecutor 执行单个 DAG 节点后产生的类型化结果。
 *
 * <p>替代原先的 {@code "FAILED:"} 字符串前缀协议（{@code DefaultVerifier.FAILED_PREFIX}）：
 * sealed 3 态由编译器保证 switch 穷举——删任一 case arm → 编译红，而非运行时静默漏分支。
 *
 * <p>三态：
 * <ul>
 *   <li>{@link Success} —— 节点正常完成，携带返回值。</li>
 *   <li>{@link DeviceFailure} —— 工具/基础设施故障（超时、网络错误、异常）。isTimeout 区分超时。</li>
 *   <li>{@link VerifierFailure} —— 验证器判定节点输出不符合预期（verify 失败节点集）。</li>
 * </ul>
 *
 * <p>使用契约：PregelExecutor 记录节点结果时用此类型（替代字符串），DefaultVerifier 消费时用
 * {@code instanceof NodeResult.DeviceFailure}（替代 {@code startsWith("FAILED:")}）。
 * 剥任一 permitted 子类 → 编译红（编译期拦漏分支，比运行时测试 RED 更早）。
 *
 * <p>删 Success → 无法表达正常完成 → 编译器报 switch 非穷举 → 编译红。
 *
 * @see RootCause
 * @see ReplanAction
 */
public sealed interface NodeResult
        permits NodeResult.Success,
                NodeResult.DeviceFailure,
                NodeResult.VerifierFailure {

    /** 节点执行成功，携带返回值。value 为工具返回值或 LLM 响应字符串。 */
    record Success(Object value) implements NodeResult {}

    /**
     * 设备/工具故障：工具抛异常、超时、或基础设施错误。
     *
     * @param nodeId    失败节点 ID
     * @param error     错误消息
     * @param isTimeout 是否为超时（用于 RootCause.diagnose 超时模式匹配）
     */
    record DeviceFailure(String nodeId, String error, boolean isTimeout) implements NodeResult {}

    /** 验证器判定节点输出不符合预期。nodeId=失败节点ID，reason=不通过原因。 */
    record VerifierFailure(String nodeId, String reason) implements NodeResult {}
}
