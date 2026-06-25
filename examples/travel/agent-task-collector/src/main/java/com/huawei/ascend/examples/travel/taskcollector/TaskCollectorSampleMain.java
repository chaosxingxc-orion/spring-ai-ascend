/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.ascend.examples.travel.taskcollector;

public final class TaskCollectorSampleMain {

    private TaskCollectorSampleMain() {
    }

    public static void main(String[] args) {
        String query = args.length == 0
                ? "我 2026-06-18 到 2026-06-20 去北京出差，帮我收集会议、待办和差标。"
                : String.join(" ", args);
        try (TaskCollectorAgent agent = new TaskCollectorAgent(LlmConfig.fromEnv())) {
            System.out.println(agent.chat(query));
        }
    }
}
