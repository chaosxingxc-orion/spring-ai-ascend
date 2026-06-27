package com.huawei.ascend.examples.workmate.diag;

public record NetworkCheckResult(
        String target, boolean reachable, long latencyMs, String detail, boolean policyAllowed) {

    public NetworkCheckResult(String target, boolean reachable, long latencyMs, String detail) {
        this(target, reachable, latencyMs, detail, true);
    }
}
