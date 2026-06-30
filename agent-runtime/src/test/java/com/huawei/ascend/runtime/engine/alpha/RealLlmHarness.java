package com.huawei.ascend.runtime.engine.alpha;

import java.util.List;
import org.junit.jupiter.api.Assumptions;

/**
 * 真 LLM e2e harness 共享工具（移植自 openjiuwen 2.0 RealLlmE2eGraphSupport，适配 spring-ai-ascend）。
 *
 * <p>诚实分层（plan V2 轮9 承重核心）：
 * <ul>
 *   <li><b>mock 控制流硬断</b>（轮3-8 CapturingModelClient/StubKernel）：承重 token 来自 mock 注入，
 *       与 LLM 响应质量无关，故 mock 全绿可靠证控制流。</li>
 *   <li><b>真 LLM 数据通道软观察</b>（本 harness capturing/promptOf/responseOf）：真 LLM 慢/非确定，
 *       作软观察（requireEnv gate 跳过非 failure），证数据通道（token 经数据通道到达真 LLM prompt）。</li>
 * </ul>
 *
 * <p>三件共享：
 * <ol>
 *   <li><b>harness gate</b> {@link #requireEnv()}：三 env 全到位才真跑（Assumptions 跳过非 failure）；</li>
 *   <li><b>(prompt, response) 对查找</b> {@link #promptOf}/{@link #responseOf}：从 think 调用记录找节点
 *       prompt/response（contiguous marker 锚定，prompt 取首 / response 取最后）；</li>
 *   <li><b>诚实分层断言</b> {@link #mockControlFlowAssert}/{@link #realLlmSoftObserve}：分层标记范式。</li>
 * </ol>
 */
public final class RealLlmHarness {

    /** 真 LLM 慢，e2e 超时安全网。 */
    public static final long E2E_TIMEOUT_SECONDS = 300;

    private RealLlmHarness() {
    }

    /** gate：三 env 全到位才真跑（OPENJIUWEN_API_KEY/BASE_URL/MODEL），否则 Assumptions 跳过（非 failure）。 */
    public static void requireEnv() {
        Assumptions.assumeTrue(envPresent("OPENJIUWEN_API_KEY"), "OPENJIUWEN_API_KEY 未设置，跳过真 LLM e2e");
        Assumptions.assumeTrue(envPresent("OPENJIUWEN_BASE_URL"), "OPENJIUWEN_BASE_URL 未设置，跳过真 LLM e2e");
        Assumptions.assumeTrue(envPresent("OPENJIUWEN_MODEL"), "OPENJIUWEN_MODEL 未设置，跳过真 LLM e2e");
    }

    public static boolean envPresent(String name) {
        String v = System.getenv(name);
        return v != null && !v.isBlank();
    }

    /**
     * 从 think 调用记录（{@code [prompt, response]} 对）找匹配 marker 的 <b>prompt 文本</b>（对第一位），取首个。
     * prompt 在重试间由确定性重建（字节相同，首==唯一）。
     */
    public static String promptOf(List<String[]> calls, String marker) {
        for (String[] c : calls) {
            if (c.length >= 1 && c[0].contains(marker)) {
                return c[0];
            }
        }
        return null;
    }

    /**
     * 从 think 调用记录找匹配 marker 的 <b>response 文本</b>（对第二位），返回<b>最后一个</b>匹配。
     * 取最后：节点级重试时框架只保留最后成功 attempt 内嵌下游，取首会返被丢弃 attempt。
     */
    public static String responseOf(List<String[]> calls, String marker) {
        String last = null;
        for (String[] c : calls) {
            if (c.length >= 2 && c[0].contains(marker)) {
                last = c[1];
            }
        }
        return last;
    }

    /**
     * 诚实分层：mock 控制流硬断标记。承重 token 来自 mock 注入（与 LLM 响应质量无关），
     * mock 全绿可靠证控制流——这是轮3-8 承重测试的分层（硬断言）。
     */
    public static void mockControlFlowAssert(String assertion, Runnable check) {
        check.run();
    }

    /**
     * 诚实分层：真 LLM 数据通道软观察标记。真 LLM 慢/非确定，作软观察（@RealLlmSoftObserve），
     * 证数据通道（token 经数据通道到达真 LLM prompt）——非控制流硬断，失败不阻塞 mock 承重。
     * 当前 defer（真 LLM e2e 跑在 harness 移植后，按用户"LLM e2e 之后再开始"）。
     */
    public static void realLlmSoftObserve(String observation) {
        // 真 LLM 软观察 defer：harness 工具就位（promptOf/responseOf/capturing 可用），
        // 真 LLM e2e 跑 defer（用户授权"LLM e2e 之后再开始"）。
    }
}
