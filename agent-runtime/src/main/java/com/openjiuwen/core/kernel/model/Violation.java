package com.openjiuwen.core.kernel.model;

/**
 * 违规类型——SafetyBoundary 检测到的安全违规。
 * sealed interface 确保所有违规场景都被枚举。
 *
 * 每种违规对应一个严重级别和处理策略：
 * - CRITICAL: 立即终止 Agent 执行
 * - HIGH:     中断当前操作，上报
 * - MEDIUM:   警告，记录审计日志，继续执行
 */
public sealed interface Violation
    permits Violation.BudgetViolation,
            Violation.SandboxViolation,
            Violation.DataLeakViolation,
            Violation.UnauthorizedAction,
            Violation.ConstraintViolation,
            Violation.CriteriaNotCovered,
            Violation.McpSecurityViolation {

    /** 违规代码 */
    String code();

    /** 人类可读的描述 */
    String message();

    /** 严重级别 */
    Severity severity();

    /** 修复建议 */
    String remediation();

    /**
     * 预算违规——Agent 消耗超出预设限制。
     * 处理：保存检查点，暂停执行。
     */
    record BudgetViolation(String message, String remediation) implements Violation {
        @Override public String code() { return "BUDGET_EXCEEDED"; }
        @Override public Severity severity() { return Severity.HIGH; }
    }

    /**
     * 沙箱违规——Agent 尝试访问未授权的资源。
     * 如：读写未授权的文件、调用未注册的工具。
     * 处理：立即终止。
     */
    record SandboxViolation(String message, String remediation) implements Violation {
        @Override public String code() { return "SANDBOX_VIOLATION"; }
        @Override public Severity severity() { return Severity.CRITICAL; }
    }

    /**
     * 数据泄漏违规——Agent 输出中包含敏感信息。
     * 如：PII、API Key、密码等。
     * 处理：拦截输出，替换为脱敏版本。
     */
    record DataLeakViolation(String message, String remediation) implements Violation {
        @Override public String code() { return "DATA_LEAK"; }
        @Override public Severity severity() { return Severity.CRITICAL; }
    }

    /**
     * 未授权操作——Agent 尝试执行需要更高权限的操作。
     * 处理：中断操作，上报审计。
     */
    record UnauthorizedAction(String message, String remediation) implements Violation {
        @Override public String code() { return "UNAUTHORIZED"; }
        @Override public Severity severity() { return Severity.HIGH; }
    }

    /**
     * 约束违反——Agent 的行为违反了预设的业务约束。
     * 如：涉及金额操作但未经审批、修改了只读数据。
     * 处理：回滚操作，暂停等待人工确认。
     */
    record ConstraintViolation(String message, String remediation) implements Violation {
        @Override public String code() { return "CONSTRAINT_VIOLATED"; }
        @Override public Severity severity() { return Severity.MEDIUM; }
    }

    /**
     * 成功标准未覆盖——Agent 的 FinalAnswer 未满足所有 successCriteria。
     *
     * Beta 策略在 FinalAnswer 前必须检查所有 criteria 是否被覆盖。
     * 每条未覆盖的标准对应一条 Violation。
     *
     * 处理：阻止 FinalAnswer，强制 Agent 继续执行或 replan。
     */
    record CriteriaNotCovered(String criterion, String reason) implements Violation {
        @Override public String code() { return "CRITERIA_NOT_COVERED"; }
        @Override public String message() { return "成功标准未满足: " + criterion + " — " + reason; }
        @Override public Severity severity() { return Severity.HIGH; }
        @Override public String remediation() { return "Agent 应继续执行或 replan，确保覆盖: " + criterion; }
    }

    /**
     * MCP 安全违规——MCP 通信未满足 mTLS 加密要求。
     *
     * Runtime 与 SDK 之间的 MCP 通信必须使用 mTLS。
     * 如果 TLS 握手失败、证书无效或使用了明文传输，触发此违规。
     *
     * 处理：阻止 MCP 工具调用，记录审计日志。
     */
    record McpSecurityViolation(String endpoint, String reason) implements Violation {
        @Override public String code() { return "MCP_SECURITY_VIOLATION"; }
        @Override public String message() { return "MCP 安全检查失败: " + endpoint + " — " + reason; }
        @Override public Severity severity() { return Severity.CRITICAL; }
        @Override public String remediation() { return "检查 mTLS 证书配置，确保 Runtime 和 SDK 之间的通信加密"; }
    }

    /** 严重级别 */
    enum Severity { CRITICAL, HIGH, MEDIUM }
}
