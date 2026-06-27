package com.huawei.ascend.examples.workmate.artifact;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ArtifactNotFoundException extends RuntimeException {

    public ArtifactNotFoundException(String message) {
        super(message);
    }
}
