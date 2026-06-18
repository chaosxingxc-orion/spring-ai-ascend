package com.huawei.ascend.examples.a2a.versatileparent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.examples.a2a.versatileparent",
        "com.huawei.ascend.runtime.boot"})
public class VersatileParentA2aApplication {

    public static void main(String[] args) {
        SpringApplication.run(VersatileParentA2aApplication.class, args);
    }
}
