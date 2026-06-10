package com.huawei.ascend.examples.a2a.returnmodes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.examples.a2a.returnmodes",
        "com.huawei.ascend.runtime.boot"})
public class ReturnModesA2aRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReturnModesA2aRuntimeApplication.class, args);
    }
}
