package com.huawei.ascend.examples.workmate.tenant;

public class QuotaExceededException extends RuntimeException {

    private final String metric;

    public QuotaExceededException(String metric, String message) {
        super(message);
        this.metric = metric;
    }

    public String metric() {
        return metric;
    }
}
