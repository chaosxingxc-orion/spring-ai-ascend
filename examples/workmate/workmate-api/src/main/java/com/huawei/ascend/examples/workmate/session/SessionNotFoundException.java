package com.huawei.ascend.examples.workmate.session;

import java.util.UUID;

public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(UUID id) {
        super("Session not found: " + id);
    }
}
