package com.huawei.ascend.examples.a2a.remoteagenttool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.examples.a2a.remoteagenttool",
        "com.huawei.ascend.runtime.boot"})
public class RemoteA2aToolInvocationApplication {

    public static void main(String[] args) {
        SpringApplication.run(RemoteA2aToolInvocationApplication.class, args);
    }
}
