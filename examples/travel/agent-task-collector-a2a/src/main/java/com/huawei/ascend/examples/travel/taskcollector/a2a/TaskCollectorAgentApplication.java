/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector.a2a;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.runtime.boot",
        "com.huawei.ascend.examples.travel.taskcollector.a2a"})
public class TaskCollectorAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskCollectorAgentApplication.class, args);
    }
}
