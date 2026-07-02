package com.huawei.ascend.runtime.engine.alpha;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * RealLlmHarness 承重——plan V2 轮9 诚实分层红→绿：harness 工具的 promptOf/responseOf 纯函数 IFF。
 *
 * <p>承重 IFF（诚实分层核心）：
 * <ul>
 *   <li>{@code promptOf} 取<b>首个</b>匹配 prompt（重试间 prompt 字节相同，首==唯一）。</li>
 *   <li>{@code responseOf} 取<b>最后</b>匹配 response（框架只保留最后成功 attempt 内嵌下游）。</li>
 * </ul>
 *
 * <p>mutation-RED：改 promptOf 取最后 / responseOf 取首 → 取首/取最后翻转 → 断言 RED。
 *
 * <p>诚实边界：promptOf/responseOf 是纯函数（mock calls 构造，不调真 LLM）。真 LLM 数据通道软观察
 * （requireEnv gate + 真 capturing）defer——harness 工具就位，真 LLM e2e 跑按用户"LLM e2e 之后再开始"。
 */
class RealLlmHarnessTest {

    // 两对 think 调用记录：同 marker，不同 prompt（attempt 1/2）+ response（重试）
    private static final List<String[]> CALLS = List.of(
            new String[]{"<task>nodeA attempt1", "resp-old"},
            new String[]{"<task>nodeA attempt2", "resp-final"});

    @Test
    void promptOfReturnsFirstMatch() {
        // mutation-RED：改 promptOf 取最后 → 返 "attempt2" → 断言 "attempt1" RED
        String prompt = RealLlmHarness.promptOf(CALLS, "<task>nodeA");
        assertThat(prompt).isEqualTo("<task>nodeA attempt1");
    }

    @Test
    void responseOfReturnsLastMatch() {
        // mutation-RED：改 responseOf 取首 → 返 "resp-old" → 断言 "resp-final" RED
        String response = RealLlmHarness.responseOf(CALLS, "<task>nodeA");
        assertThat(response).isEqualTo("resp-final");
    }

    @Test
    void promptOfNoMatchReturnsNull() {
        assertThat(RealLlmHarness.promptOf(CALLS, "<task>nodeZ")).isNull();
    }

    @Test
    void responseOfNoMatchReturnsNull() {
        assertThat(RealLlmHarness.responseOf(CALLS, "<task>nodeZ")).isNull();
    }

    @Test
    void envPresentMissingEnvReturnsFalse() {
        // requireEnv gate 基础：env 缺 → false → requireEnv 内 assumeTrue 跳过（非 failure）
        assertThat(RealLlmHarness.envPresent("REAL_LLM_HARNESS_NONEXISTENT_ENV")).isFalse();
    }

    @Test
    void envPresentMissingEnvReturnsFalseOnly() {
        // env 名空 / 不存在 → false（不依赖具体 env，避 flaky）
        assertThat(RealLlmHarness.envPresent("")).isFalse();
        assertThat(RealLlmHarness.envPresent("REAL_LLM_HARNESS_DEFINITELY_MISSING")).isFalse();
    }
}
