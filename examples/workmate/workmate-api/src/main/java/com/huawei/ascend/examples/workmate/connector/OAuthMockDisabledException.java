package com.huawei.ascend.examples.workmate.connector;

public class OAuthMockDisabledException extends RuntimeException {

    public OAuthMockDisabledException() {
        super("OAuth mock redirect flow is disabled; use token or device-code auth instead");
    }
}
