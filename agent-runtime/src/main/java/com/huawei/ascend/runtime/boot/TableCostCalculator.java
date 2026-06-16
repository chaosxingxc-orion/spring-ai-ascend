package com.huawei.ascend.runtime.boot;

import com.huawei.ascend.runtime.engine.spi.CostCalculator;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Usage;
import java.util.Map;

/**
 * Config-table {@link CostCalculator}: fills a model-call usage's {@code provider} and
 * {@code costMicros} from the per-model pricing in {@code app.trajectory.pricing}. A usage whose
 * model is not in the table (or a null usage) is returned unchanged. Cost is integer
 * micro-currency to avoid float rounding in billing aggregation.
 */
final class TableCostCalculator implements CostCalculator {

    private final Map<String, TrajectoryProperties.Pricing.Model> models;

    TableCostCalculator(Map<String, TrajectoryProperties.Pricing.Model> models) {
        this.models = models == null ? Map.of() : Map.copyOf(models);
    }

    @Override
    public Usage enrich(Usage usage) {
        if (usage == null || usage.model() == null) {
            return usage;
        }
        TrajectoryProperties.Pricing.Model price = models.get(usage.model());
        if (price == null) {
            return usage;
        }
        long cost = 0L;
        if (usage.inputTokens() != null) {
            cost += (long) usage.inputTokens() * price.getInputMicrosPerToken();
        }
        if (usage.outputTokens() != null) {
            cost += (long) usage.outputTokens() * price.getOutputMicrosPerToken();
        }
        String provider = usage.provider() != null ? usage.provider() : price.getProvider();
        return new Usage(usage.inputTokens(), usage.outputTokens(), usage.latencyMs(), usage.model(),
                provider, cost);
    }
}
