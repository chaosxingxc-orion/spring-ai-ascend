/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.hotel.a2a;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.huawei.ascend.examples.hotel.a2a",
        "com.huawei.ascend.runtime.boot"})
public class HotelAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotelAgentApplication.class, args);
    }
}
