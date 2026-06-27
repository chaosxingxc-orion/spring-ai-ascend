package com.huawei.ascend.examples.workmate.member;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WorkmateMemberA2aApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkmateMemberA2aApplication.class, args);
    }
}
