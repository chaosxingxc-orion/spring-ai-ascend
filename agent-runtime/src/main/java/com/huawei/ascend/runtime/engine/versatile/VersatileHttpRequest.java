package com.huawei.ascend.runtime.engine.versatile;

import java.util.Map;

/**
 * Immutable snapshot of a resolved versatile REST call.
 */
record VersatileHttpRequest(
        String method,
        String url,
        Map<String, String> headers,
        Map<String, Object> body) {
}
