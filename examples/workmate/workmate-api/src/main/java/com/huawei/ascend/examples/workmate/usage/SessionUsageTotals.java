package com.huawei.ascend.examples.workmate.usage;

public record SessionUsageTotals(long promptTokens, long completionTokens) {

    public static SessionUsageTotals empty() {
        return new SessionUsageTotals(0L, 0L);
    }

    public long totalTokens() {
        return promptTokens + completionTokens;
    }
}
