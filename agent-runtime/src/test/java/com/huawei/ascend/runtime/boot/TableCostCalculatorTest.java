package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.CostCalculator;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Usage;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** TableCostCalculator: provider/costMicros enrichment from a pricing model table. */
class TableCostCalculatorTest {

    private static TrajectoryProperties.Pricing.Model model(String provider, long in, long out) {
        var m = new TrajectoryProperties.Pricing.Model();
        m.setProvider(provider);
        m.setInputMicrosPerToken(in);
        m.setOutputMicrosPerToken(out);
        return m;
    }

    private static Usage usage(Integer in, Integer out, String modelId) {
        return new Usage(in, out, null, modelId, null, null);
    }

    @Test
    void enrichesCostAndProviderForKnownModel() {
        var calc = new TableCostCalculator(Map.of("gpt-4o", model("openai", 5L, 15L)));
        Usage result = calc.enrich(usage(1000, 500, "gpt-4o"));

        assertThat(result.provider()).isEqualTo("openai");
        // 1000*5 + 500*15 = 5000 + 7500 = 12500 micros
        assertThat(result.costMicros()).isEqualTo(12500L);
        // Original token counts preserved
        assertThat(result.inputTokens()).isEqualTo(1000);
        assertThat(result.outputTokens()).isEqualTo(500);
    }

    @Test
    void preservesExistingProviderWhenAlreadySet() {
        var calc = new TableCostCalculator(Map.of("gpt-4o", model("openai", 5L, 15L)));
        Usage withProvider = new Usage(100, 50, null, "gpt-4o", "azure", null);
        Usage result = calc.enrich(withProvider);

        // Provider was already filled — keep caller's value
        assertThat(result.provider()).isEqualTo("azure");
        assertThat(result.costMicros()).isEqualTo(100 * 5L + 50 * 15L);
    }

    @Test
    void unknownModelPassesThroughUnchanged() {
        var calc = new TableCostCalculator(Map.of("gpt-4o", model("openai", 5L, 15L)));
        Usage u = usage(100, 50, "llama-3");
        assertThat(calc.enrich(u)).isSameAs(u);
    }

    @Test
    void nullUsagePassesThroughUnchanged() {
        var calc = new TableCostCalculator(Map.of("gpt-4o", model("openai", 5L, 15L)));
        assertThat(calc.enrich(null)).isNull();
    }

    @Test
    void nullModelInUsagePassesThroughUnchanged() {
        var calc = new TableCostCalculator(Map.of("gpt-4o", model("openai", 5L, 15L)));
        Usage u = usage(100, 50, null);
        assertThat(calc.enrich(u)).isSameAs(u);
    }

    @Test
    void nullInputTokensSkipsInputCost() {
        var calc = new TableCostCalculator(Map.of("claude-3", model("anthropic", 3L, 12L)));
        Usage u = new Usage(null, 200, null, "claude-3", null, null);
        Usage result = calc.enrich(u);
        assertThat(result.costMicros()).isEqualTo(200 * 12L);
    }

    @Test
    void nullOutputTokensSkipsOutputCost() {
        var calc = new TableCostCalculator(Map.of("claude-3", model("anthropic", 3L, 12L)));
        Usage u = new Usage(100, null, null, "claude-3", null, null);
        Usage result = calc.enrich(u);
        assertThat(result.costMicros()).isEqualTo(100 * 3L);
    }

    @Test
    void emptyTablePassesThroughUnchanged() {
        var calc = new TableCostCalculator(Map.of());
        Usage u = usage(100, 50, "any-model");
        assertThat(calc.enrich(u)).isSameAs(u);
    }

    @Test
    void costCalculatorNoneIsNoOp() {
        Usage u = usage(100, 50, "gpt-4o");
        assertThat(CostCalculator.NONE.enrich(u)).isSameAs(u);
    }
}
