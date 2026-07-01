package com.openjiuwen.core.alpha.verifier;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * NodeResult sealed type 承重测试——sealed 穷举 3 态 + 数据携带 IFF。
 *
 * <p>扁平 @Test（非 @Nested）——匹配项目 convention。
 *
 * <p>承重 IFF：
 * <ul>
 *   <li>sealed permits 3 态——删任一 permitted 子类 → 编译红（类型层 mutation-prove）。</li>
 *   <li>Success 携带值 round-trip——剥 Success.value 赋值 → RED。</li>
 *   <li>DeviceFailure 携带错误+超时标记——剥 isTimeout → RED。</li>
 *   <li>VerifierFailure 携带原因——剥 reason → RED。</li>
 * </ul>
 */
class NodeResultSealedTest {

    // ==================== Sealed exhaustiveness ====================

    @Test
    void sealedPermits3States() {
        // 反射验证：编译器 enforced permits 列表
        Class<?>[] permitted = NodeResult.class.getPermittedSubclasses();
        assertThat(permitted).hasSize(3);
        assertThat(permitted).extracting(Class::getSimpleName)
                .containsExactlyInAnyOrder("Success", "DeviceFailure", "VerifierFailure");
    }

    // ==================== Success ====================

    @Test
    void successWrapsValueRoundTrip() {
        // mutation-RED：剥 value 赋值 → RED
        NodeResult.Success s = new NodeResult.Success("hello");
        assertThat(s.value()).isEqualTo("hello");
    }

    @Test
    void successWithNullValue() {
        NodeResult.Success s = new NodeResult.Success(null);
        assertThat(s.value()).isNull();
    }

    // ==================== DeviceFailure ====================

    @Test
    void deviceFailureCarriesErrorAndTimeoutFlag() {
        // mutation-RED：剥 isTimeout → RED（超时信号丢失，diagnose 分不清超时/异常）
        NodeResult.DeviceFailure df = new NodeResult.DeviceFailure("n1", "连接超时", true);
        assertThat(df.nodeId()).isEqualTo("n1");
        assertThat(df.error()).isEqualTo("连接超时");
        assertThat(df.isTimeout()).isTrue();
    }

    @Test
    void deviceFailureTimeoutFalseByDefault() {
        NodeResult.DeviceFailure df = new NodeResult.DeviceFailure("n2", "NPE", false);
        assertThat(df.isTimeout()).isFalse();
    }

    // ==================== VerifierFailure ====================

    @Test
    void verifierFailureCarriesReason() {
        // mutation-RED：剥 reason → RED（失败原因丢失，无可审计性）
        NodeResult.VerifierFailure vf = new NodeResult.VerifierFailure("n3", "期望值 100 实际 50");
        assertThat(vf.nodeId()).isEqualTo("n3");
        assertThat(vf.reason()).isEqualTo("期望值 100 实际 50");
    }

    // ==================== Pattern-match exhaustiveness ====================

    @Test
    void patternMatchCoversAll3States() {
        // 承重：switch over NodeResult 穷尽 3 态——删任一 case → 编译红
        NodeResult[] cases = {
                new NodeResult.Success("ok"),
                new NodeResult.DeviceFailure("n", "err", false),
                new NodeResult.VerifierFailure("n", "wrong")
        };
        for (NodeResult nr : cases) {
            String label = switch (nr) {
                case NodeResult.Success s -> "success:" + s.value();
                case NodeResult.DeviceFailure d -> "device:" + d.error();
                case NodeResult.VerifierFailure v -> "verify:" + v.reason();
            };
            assertThat(label).isNotEmpty();
        }
    }
}
