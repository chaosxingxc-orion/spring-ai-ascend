package com.huawei.ascend.examples.workmate.office;

public class PlaybookNotFoundException extends RuntimeException {

    public PlaybookNotFoundException(String playbookId) {
        super("Playbook not found: " + playbookId);
    }
}
