package com.huawei.ascend.examples.workmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.examples.workmate",
        "com.huawei.ascend.runtime.boot"
})
@ConfigurationPropertiesScan
@EnableScheduling
public class WorkmateApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkmateApiApplication.class, args);
    }
}
