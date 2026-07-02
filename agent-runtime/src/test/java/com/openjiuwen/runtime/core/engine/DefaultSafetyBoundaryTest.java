package com.openjiuwen.runtime.core.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.kernel.model.*;
import java.util.*;
import java.util.regex.Pattern;

import org.junit.jupiter.api.*;

/**
 * DefaultSafetyBoundary 承重测试——证 6 个安全检查方法全部分支。
 *
 * <p>每个测试标注 mutation-RED：剥什么断言会红。
 *
 * <p>承重契约：SafetyBoundary 是 PEV 管道的安全闸门，断言必须硬（具体违规类型+消息），非弱断言（仅非空）。
 */
@DisplayName("DefaultSafetyBoundary 安全检查承重（12 场景）")
class DefaultSafetyBoundaryTest {

    // ==================== checkToolCall ====================

    @Test
    @DisplayName("S1: 工具在白名单中→无违规")
    void toolInWhitelistPasses() {
        SafetyBoundary sb = new DefaultSafetyBoundary(
                Set.of(new ToolName("safeTool")), List.of());
        BudgetLimits budget = BudgetLimits.start(new Budget.Fixed(10, 10, 10_000, 60_000));

        List<Violation> violations = sb.checkToolCall(
                new ToolName("safeTool"), Map.of(), budget);

        assertThat(violations).isEmpty();
        // mutation-RED: 剥白名单 empty 短路 → violations 含 UnauthorizedAction → RED
    }

    @Test
    @DisplayName("S2: 工具不在白名单中→UnauthorizedAction")
    void toolNotInWhitelistBlocked() {
        SafetyBoundary sb = new DefaultSafetyBoundary(
                Set.of(new ToolName("safeTool")), List.of());
        BudgetLimits budget = BudgetLimits.start(new Budget.Fixed(10, 10, 10_000, 60_000));

        List<Violation> violations = sb.checkToolCall(
                new ToolName("evilTool"), Map.of(), budget);

        assertThat(violations).isNotEmpty();
        assertThat(violations.get(0)).isInstanceOf(Violation.UnauthorizedAction.class);
        // mutation-RED: 剥 whitelist check → violations empty → RED
    }

    @Test
    @DisplayName("S3: 白名单为空→允许所有工具（permissive default）")
    void emptyWhitelistAllowsAll() {
        SafetyBoundary sb = new DefaultSafetyBoundary(); // no-arg = empty whitelist
        BudgetLimits budget = BudgetLimits.start(new Budget.Fixed(10, 10, 10_000, 60_000));

        List<Violation> violations = sb.checkToolCall(
                new ToolName("anyTool"), Map.of(), budget);

        assertThat(violations).isEmpty();
        // mutation-RED: 剥 empty skip → 空白名单也拦截 → RED（设计意图：permissive default）
    }

    // ==================== checkLLMOutput ====================

    @Test
    @DisplayName("S4: LLM 输出含敏感模式→DataLeakViolation")
    void sensitivePatternDetected() {
        SafetyBoundary sb = new DefaultSafetyBoundary(Set.of(),
                List.of(Pattern.compile("\\b\\d{16}\\b"))); // credit-card-like
        String output = "我的卡号是 1234567890123456";

        List<Violation> violations = sb.checkLLMOutput(output);

        assertThat(violations).isNotEmpty();
        assertThat(violations.get(0)).isInstanceOf(Violation.DataLeakViolation.class);
        // mutation-RED: 剥敏感模式匹配 → violations empty → RED
    }

    @Test
    @DisplayName("S5: LLM 输出不含敏感模式→无违规")
    void noSensitivePatternPasses() {
        SafetyBoundary sb = new DefaultSafetyBoundary(Set.of(),
                List.of(Pattern.compile("\\b\\d{16}\\b")));
        String output = "今天天气很好，适合出门散步。";

        List<Violation> violations = sb.checkLLMOutput(output);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("S6: null 输出→无违规（防 NPE）")
    void nullOutputNoViolation() {
        SafetyBoundary sb = new DefaultSafetyBoundary(Set.of(),
                List.of(Pattern.compile("secret")));

        List<Violation> violations = sb.checkLLMOutput(null);

        assertThat(violations).isEmpty();
        // mutation-RED: 剥 null guard → NPE → RED
    }

    // ==================== checkBudget ====================

    @Test
    @DisplayName("S7: 预算未超限→无违规")
    void budgetNotExceededPasses() {
        SafetyBoundary sb = new DefaultSafetyBoundary();
        Budget budget = new Budget.Fixed(10, 10, 10_000, 60_000);
        BudgetLimits limits = BudgetLimits.start(budget);

        List<Violation> violations = sb.checkBudget(limits);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("S8: 预算超限→BudgetViolation")
    void budgetExceededReturnsViolation() {
        SafetyBoundary sb = new DefaultSafetyBoundary();
        // Create a tiny budget and immediately record one call to exceed it
        Budget budget = new Budget.Fixed(1, 1, 1, 60_000);
        BudgetLimits exceeded = BudgetLimits.start(budget).recordLLMCall(1);

        List<Violation> violations = sb.checkBudget(exceeded);

        assertThat(violations).isNotEmpty();
        assertThat(violations.get(0)).isInstanceOf(Violation.BudgetViolation.class);
        assertThat(violations.get(0).severity()).isEqualTo(Violation.Severity.HIGH);
        // mutation-RED: 剥 isExceeded check → violations empty → RED
    }

    // ==================== checkCriteriaCoverage ====================

    @Test
    @DisplayName("S9: 所有 criteria 被覆盖→empty")
    void allCriteriaCovered() {
        SafetyBoundary sb = new DefaultSafetyBoundary();
        List<String> successCriteria = List.of("准确性", "完整性");
        List<String> verified = List.of("验证了准确性", "检查了完整性");

        Optional<Violation> violation = sb.checkCriteriaCoverage(
                new TaskId("t1"), successCriteria, verified);

        assertThat(violation).isEmpty();
    }

    @Test
    @DisplayName("S10: 有 criteria 未被覆盖→CriteriaNotCovered")
    void uncoveredCriteriaDetected() {
        SafetyBoundary sb = new DefaultSafetyBoundary();
        List<String> successCriteria = List.of("准确性", "安全性");
        List<String> verified = List.of("验证了准确性"); // "安全性" not covered

        Optional<Violation> violation = sb.checkCriteriaCoverage(
                new TaskId("t1"), successCriteria, verified);

        assertThat(violation).isPresent();
        assertThat(violation.get()).isInstanceOf(Violation.CriteriaNotCovered.class);
        assertThat(violation.get().message()).contains("安全性");
        // mutation-RED: 剥 uncovered check → violation empty → RED
    }

    // ==================== checkMcpSecurity ====================

    @Test
    @DisplayName("S11: TLS 已建立→无违规")
    void tlsEstablishedPasses() {
        SafetyBoundary sb = new DefaultSafetyBoundary();

        Optional<Violation> violation = sb.checkMcpSecurity("mcp://localhost:8080", true);

        assertThat(violation).isEmpty();
    }

    @Test
    @DisplayName("S12: TLS 未建立→McpSecurityViolation")
    void tlsNotEstablishedReturnsViolation() {
        SafetyBoundary sb = new DefaultSafetyBoundary();

        Optional<Violation> violation = sb.checkMcpSecurity("mcp://localhost:8080", false);

        assertThat(violation).isPresent();
        assertThat(violation.get()).isInstanceOf(Violation.McpSecurityViolation.class);
        assertThat(violation.get().severity()).isEqualTo(Violation.Severity.CRITICAL);
        assertThat(violation.get().message()).contains("localhost");
        // mutation-RED: 剥 !isTlsEstablished check → violation empty → RED
    }
}
