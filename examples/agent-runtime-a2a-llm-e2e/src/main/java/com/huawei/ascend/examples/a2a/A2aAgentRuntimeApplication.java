package com.huawei.ascend.examples.a2a;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.examples.a2a",
        "com.huawei.ascend.runtime.boot"})
public class A2aAgentRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(A2aAgentRuntimeApplication.class, args);
    }
}
