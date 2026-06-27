package com.huawei.ascend.examples.workmate.share;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ShareNotFoundException extends RuntimeException {

    public ShareNotFoundException(String token) {
        super("Share link not found: " + token);
    }
}
