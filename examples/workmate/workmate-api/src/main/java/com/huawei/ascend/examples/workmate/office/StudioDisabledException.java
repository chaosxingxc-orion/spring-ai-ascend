package com.huawei.ascend.examples.workmate.office;

public class StudioDisabledException extends RuntimeException {

    public StudioDisabledException() {
        super("Developer Studio is disabled");
    }
}
