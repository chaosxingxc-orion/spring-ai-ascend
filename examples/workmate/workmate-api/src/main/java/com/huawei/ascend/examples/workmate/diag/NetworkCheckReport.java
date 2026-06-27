package com.huawei.ascend.examples.workmate.diag;

import java.util.List;

public record NetworkCheckReport(
        String proxyMode,
        java.util.List<NetworkCheckResult> checks) {}
