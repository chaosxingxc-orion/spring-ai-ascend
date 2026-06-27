package com.huawei.ascend.examples.workmate.artifact;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class PreviewNotAllowedException extends RuntimeException {

    public PreviewNotAllowedException(String message) {
        super(message);
    }
}
