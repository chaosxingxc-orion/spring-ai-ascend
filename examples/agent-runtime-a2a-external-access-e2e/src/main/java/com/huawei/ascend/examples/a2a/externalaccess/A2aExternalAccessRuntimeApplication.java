package com.huawei.ascend.examples.a2a.externalaccess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.examples.a2a.externalaccess",
        "com.huawei.ascend.runtime.boot"})
public class A2aExternalAccessRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(A2aExternalAccessRuntimeApplication.class, args);
    }
}
