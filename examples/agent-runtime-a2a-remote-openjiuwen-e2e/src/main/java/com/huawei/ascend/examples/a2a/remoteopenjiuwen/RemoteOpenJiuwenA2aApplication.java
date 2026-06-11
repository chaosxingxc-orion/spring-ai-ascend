package com.huawei.ascend.examples.a2a.remoteopenjiuwen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.examples.a2a.remoteopenjiuwen",
        "com.huawei.ascend.runtime.boot"})
public class RemoteOpenJiuwenA2aApplication {

    public static void main(String[] args) {
        SpringApplication.run(RemoteOpenJiuwenA2aApplication.class, args);
    }
}
