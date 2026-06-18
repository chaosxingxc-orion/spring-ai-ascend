package com.huawei.ascend.examples.a2a;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.examples.a2a",
        "com.huawei.ascend.runtime.boot"})
public class OpenJiuwenA2aAgentRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenJiuwenA2aAgentRuntimeApplication.class, args);
    }
}
