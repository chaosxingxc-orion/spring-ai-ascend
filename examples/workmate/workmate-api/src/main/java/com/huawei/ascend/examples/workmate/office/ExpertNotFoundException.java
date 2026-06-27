package com.huawei.ascend.examples.workmate.office;

public class ExpertNotFoundException extends RuntimeException {

    private final String expertId;

    public ExpertNotFoundException(String expertId) {
        super("Expert not found: " + expertId);
        this.expertId = expertId;
    }

    public String expertId() {
        return expertId;
    }
}
