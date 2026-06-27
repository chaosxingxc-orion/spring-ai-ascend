package com.huawei.ascend.examples.workmate.cloud;

public class CloudDisabledException extends RuntimeException {

    public CloudDisabledException() {
        super("Cloud sessions are disabled");
    }
}
