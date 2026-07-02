package com.openjiuwen.runtime.alpha.verifier;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.alpha.verifier.ReplanStrategy;
import com.openjiuwen.core.alpha.verifier.VerifyResult;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * DefaultVerifier 承重测试——plan V2 轮7 主承重：Verify→Replan IFF（验证结果+retryCount→ReplanStrategy 3 态）。
 *
 * <p>承重核心：decideReplanStrategy 纯函数决策。
 * <ul>
 *   <li>passed → AcceptPartial（不需 replan）</li>
 *   <li>retryCount >= MAX_RETRY_BEFORE_ACCEPT(3) → AcceptPartial（放弃重试）</li>
 *   <li>failedNodes > 3 || retryCount >= 2 → GlobalReplan（全局重规划）</li>
 *   <li>else → LocalReplan（局部重做）</li>
 * </ul>
 *
 * <p>mutation-RED：剥 retry>=3→AcceptPartial 决策 → retry=3 返 GlobalReplan/LocalReplan（非 AcceptPartial）→ RED。
 *
 * <p>诚实边界：decideReplanStrategy 纯函数（不调 kernel/LLM），真 LLM judge（verify 的 llmVerifyNodes）
 * 数据通道 defer 轮9。
 */
class DefaultVerifierTest {

    // decideReplanStrategy 纯函数不调 kernel，传 null kernel 即可（只测决策逻辑）
    private final DefaultVerifier verifier = new DefaultVerifier(null);

    @Test
    void passedReturnsAcceptPartial() {
        ReplanStrategy strategy = verifier.decideReplanStrategy(VerifyResult.passed("ok"), 0);
        assertThat(strategy).isInstanceOf(ReplanStrategy.AcceptPartial.class);
    }

    @Test
    void retryExhaustedReturnsAcceptPartial() {
        // mutation-RED：剥 retry>=3→AcceptPartial → 此处返 GlobalReplan/LocalReplan → RED
        ReplanStrategy strategy = verifier.decideReplanStrategy(
                VerifyResult.failed("still failing", Set.of("n1")), 3);
        assertThat(strategy).isInstanceOf(ReplanStrategy.AcceptPartial.class);
    }

    @Test
    void manyFailedNodesReturnGlobalReplan() {
        // failedNodes=4 > 3 → GlobalReplan
        ReplanStrategy strategy = verifier.decideReplanStrategy(
                VerifyResult.failed("many failed", Set.of("n1", "n2", "n3", "n4")), 0);
        assertThat(strategy).isInstanceOf(ReplanStrategy.GlobalReplan.class);
    }

    @Test
    void retryTwoReturnsGlobalReplan() {
        // retryCount=2 → GlobalReplan
        ReplanStrategy strategy = verifier.decideReplanStrategy(
                VerifyResult.failed("fail", Set.of("n1")), 2);
        assertThat(strategy).isInstanceOf(ReplanStrategy.GlobalReplan.class);
    }

    @Test
    void fewFailedFirstRetryReturnsLocalReplan() {
        // failedNodes=1, retry=0 → LocalReplan
        ReplanStrategy strategy = verifier.decideReplanStrategy(
                VerifyResult.failed("fail", Set.of("n1")), 0);
        assertThat(strategy).isInstanceOf(ReplanStrategy.LocalReplan.class);
    }

    @Test
    void localReplanMaxRoundsDecreasesWithRetry() {
        // retry=0 → LocalReplan(maxRounds=3); retry=1 → maxRounds=2（递减）
        ReplanStrategy.LocalReplan s0 = (ReplanStrategy.LocalReplan) verifier.decideReplanStrategy(
                VerifyResult.failed("fail", Set.of("n1")), 0);
        ReplanStrategy.LocalReplan s1 = (ReplanStrategy.LocalReplan) verifier.decideReplanStrategy(
                VerifyResult.failed("fail", Set.of("n1")), 1);
        assertThat(s0.maxRounds()).isGreaterThan(s1.maxRounds());
    }
}
